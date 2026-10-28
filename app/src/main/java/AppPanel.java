package com.example.floatingwindow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Tab kecil": panel yang muncul saat tab di-swipe.
 * Berisi aplikasi pilihan + tombol tambah (+).
 *
 * Mode normal  : klik ikon = buka aplikasi.
 * Mode add-app : klik ikon = keluarkan dari panel (kembali ke tab besar / picker).
 */
public class AppPanel {

    public interface Callbacks {
        void onAppLaunched();
        void onAddClicked();
        /** Ikon diklik saat mode add-app -> aplikasi dikeluarkan dari panel. */
        void onAppTappedInAddMode(String packageName);
    }

    public static final int COLOR_PANEL = 0xFF6E6E6E;   // background panel / tab
    public static final int COLOR_ADD_BG = 0xFF424242;  // background tombol tambah-aplikasi

    /** Batas jumlah aplikasi di panel. */
    public static final int MAX_APPS = 6;

    /** Aplikasi default (dipakai selama pengguna belum mengubah daftar). */
    private static final List<String> DEFAULT_PACKAGES = Arrays.asList(
            "com.whatsapp",
            "ru.zdevs.zarchiver",
            "tw.nekomimi.nekogram");

    private static final String PREFS = "floating_panel";
    private static final String KEY_APPS = "panel_apps";   // package dipisah koma

    // ukuran dari preview (dp)
    private static final int ICON_SIZE = 62;
    private static final int ICON_RADIUS = 16;
    private static final int ICON_GAP = 11;
    private static final int PANEL_RADIUS = 20;
    private static final int PANEL_PAD_H = 7;
    private static final int PANEL_PAD_TOP = 22;
    private static final int PANEL_PAD_BOTTOM = 20;
    public static final int EDGE_MARGIN_DP = 8;

    private final Context context;
    private final Callbacks callbacks;
    private final LinearLayout root;
    private final List<String> shownPackages = new ArrayList<>();
    private boolean addMode = false;

    public AppPanel(Context context, Callbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(PANEL_PAD_H), dp(PANEL_PAD_TOP), dp(PANEL_PAD_H), dp(PANEL_PAD_BOTTOM));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_PANEL);
        bg.setCornerRadius(dp(PANEL_RADIUS));
        root.setBackground(bg);

        build();
    }

    public View getView() {
        return root;
    }

    /** Mode add-app: klik ikon = keluarkan dari panel. */
    public void setAddMode(boolean addMode) {
        this.addMode = addMode;
    }

    /** Gambar ulang isi panel dari daftar tersimpan (setelah tambah/hapus). */
    public void rebuild() {
        build();
    }

    /** Package yang sedang ada di panel (dipakai picker agar tidak menampilkan duplikat). */
    public List<String> getShownPackages() {
        return shownPackages;
    }

    // ---------------- penyimpanan daftar aplikasi ----------------

    public static List<String> getPackages(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_APPS)) return new ArrayList<>(DEFAULT_PACKAGES);

        List<String> list = new ArrayList<>();
        String raw = sp.getString(KEY_APPS, "");
        if (raw != null && !raw.isEmpty()) {
            for (String p : raw.split(",")) {
                if (!p.isEmpty()) list.add(p);
            }
        }
        return list;
    }

    public static void addPackage(Context c, String pkg) {
        List<String> list = getPackages(c);
        if (!list.contains(pkg)) {
            list.add(pkg);
            save(c, list);
        }
    }

    public static void removePackage(Context c, String pkg) {
        List<String> list = getPackages(c);
        if (list.remove(pkg)) save(c, list);
    }

    public static int getAppCount(Context c) {
        return getPackages(c).size();
    }

    private static void save(Context c, List<String> list) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_APPS, TextUtils.join(",", list)).apply();
    }

    // ------------------------------------------------------------------

    private void build() {
        root.removeAllViews();
        shownPackages.clear();

        for (String pkg : getPackages(context)) {
            root.addView(createAppSlot(pkg), slotParams(ICON_GAP));
            shownPackages.add(pkg);
        }
        root.addView(createAddButton(), slotParams(0));
    }

    private View createAppSlot(final String pkg) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(COLOR_ADD_BG);              // placeholder bila app tidak terpasang
        roundCorners(iv, dp(ICON_RADIUS));

        PackageManager pm = context.getPackageManager();
        final Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) {
            try {
                iv.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }

        iv.setOnClickListener(v -> {
            if (addMode) {
                callbacks.onAppTappedInAddMode(pkg);
                return;
            }
            if (launch == null) {
                Toast.makeText(context, "Aplikasi belum terpasang", Toast.LENGTH_SHORT).show();
                return;
            }
            FloatingAppLauncher.launch(context, launch);   // jendela mengambang
            callbacks.onAppLaunched();
        });
        return iv;
    }

    private View createAddButton() {
        ImageView add = new ImageView(context);
        add.setImageResource(R.drawable.ic_add_white);
        int pad = dp(12);
        add.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COLOR_ADD_BG);
        add.setBackground(bg);

        add.setOnClickListener(v -> callbacks.onAddClicked());
        return add;
    }

    private LinearLayout.LayoutParams slotParams(int bottomMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ICON_SIZE), dp(ICON_SIZE));
        lp.bottomMargin = dp(bottomMarginDp);
        return lp;
    }

    private static void roundCorners(View v, final int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int edgeMarginPx(Context c) {
        return Math.round(EDGE_MARGIN_DP * c.getResources().getDisplayMetrics().density);
    }
}
