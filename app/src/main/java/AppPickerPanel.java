package com.example.floatingwindow;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kotak abu-abu yang muncul di samping panel saat tombol tambah (+) diklik.
 * Berisi grid aplikasi terpasang (aplikasi sistem tidak ditampilkan).
 */
public class AppPickerPanel {

    public interface Callbacks {
        void onAppSelected(String packageName);
    }

    public static final int COLOR_PANEL = 0xFF6E6E6E;

    /** true = aplikasi bawaan yang sudah di-update (Chrome, YouTube, dll.) tetap ditampilkan. */
    private static final boolean INCLUDE_UPDATED_SYSTEM_APPS = true;

    // ukuran dari preview (dp)
    private static final int WIDTH_DP = 245;
    private static final int HEIGHT_DP = 325;
    private static final int GAP_DP = 26;          // jarak dari panel samping
    private static final int RADIUS_DP = 20;
    private static final int PAD_H_DP = 12;
    private static final int PAD_V_DP = 14;
    private static final int COLUMNS = 3;
    private static final int ICON_DP = 52;
    private static final int ICON_RADIUS_DP = 14;
    private static final int ROW_GAP_DP = 10;

    private final Context context;
    private final Callbacks callbacks;
    private final Set<String> excluded = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final ScrollView scroll;
    private final GridLayout grid;
    private final int cellWidthPx;
    private volatile boolean released = false;
    private List<Item> allItems = null;   // diisi setelah load() selesai

    private static class Item {
        final String packageName;
        final String label;
        final Drawable icon;
        Item(String p, String l, Drawable i) { packageName = p; label = l; icon = i; }
    }

    /**
     * @param widthPx  lebar window picker (px)
     * @param excluded package yang sudah ada di panel samping (tidak ditampilkan lagi)
     */
    public AppPickerPanel(Context context, int widthPx, Collection<String> excluded,
                          Callbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        this.excluded.addAll(excluded);
        this.cellWidthPx = (widthPx - 2 * dp(PAD_H_DP)) / COLUMNS;

        root = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_PANEL);
        bg.setCornerRadius(dp(RADIUS_DP));
        root.setBackground(bg);
        final int radius = dp(RADIUS_DP);
        root.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        root.setClipToOutline(true);

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid = new GridLayout(context);
        grid.setColumnCount(COLUMNS);
        grid.setPadding(dp(PAD_H_DP), dp(PAD_V_DP), dp(PAD_H_DP), dp(PAD_V_DP));
        scroll.addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public View getView() {
        return root;
    }

    public static int widthPx(Context c) { return dpOf(c, WIDTH_DP); }
    public static int heightPx(Context c) { return dpOf(c, HEIGHT_DP); }
    public static int gapPx(Context c) { return dpOf(c, GAP_DP); }

    /** Muat daftar aplikasi di background thread, lalu isi grid. */
    public void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Item> items = queryApps();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (released) return;
                        allItems = items;
                        populate();
                    }
                });
            }
        }, "app-picker-loader").start();
    }

    /** Perbarui daftar aplikasi yang disembunyikan (yang sudah ada di panel samping). */
    public void setExcluded(Collection<String> packages) {
        excluded.clear();
        excluded.addAll(packages);
        if (allItems != null) populate();
    }

    public void release() {
        released = true;
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ------------------------------------------------------------------

    private List<Item> queryApps() {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        List<Item> items = new ArrayList<>();

        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            String pkg = ai.packageName;
            if (pkg.equals(context.getPackageName())) continue;   // aplikasi ini sendiri
            if (isSystemApp(ai)) continue;                        // aplikasi sistem
            if (!seen.add(pkg)) continue;                         // hindari duplikat
            items.add(new Item(pkg, String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
        }

        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT));
            }
        });
        return items;
    }

    private static boolean isSystemApp(ApplicationInfo ai) {
        boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (INCLUDE_UPDATED_SYSTEM_APPS) return system && !updated;
        return system;
    }

    private void populate() {
        final int scrollY = scroll.getScrollY();
        grid.removeAllViews();

        List<Item> items = new ArrayList<>();
        for (Item it : allItems) {
            if (!excluded.contains(it.packageName)) items.add(it);   // sudah ada di panel -> sembunyikan
        }

        if (items.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Tidak ada aplikasi lain");
            empty.setTextColor(0xFFFFFFFF);
            empty.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(0, COLUMNS), GridLayout.spec(0));
            lp.width = cellWidthPx * COLUMNS;
            grid.addView(empty, lp);
            return;
        }

        for (final Item item : items) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(item.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            final int radius = dp(ICON_RADIUS_DP);
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            icon.setClipToOutline(true);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)));

            TextView label = new TextView(context);
            label.setText(item.label);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(11);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(4);
            cell.addView(label, llp);

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callbacks.onAppSelected(item.packageName);
                }
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellWidthPx;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.bottomMargin = dp(ROW_GAP_DP);
            grid.addView(cell, lp);
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, scrollY);
            }
        });
    }

    private int dp(int value) { return dpOf(context, value); }

    private static int dpOf(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
