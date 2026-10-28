package com.example.floatingwindow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * Service yang menampilkan sebuah "swiper tab" di tepi layar (mirip edge panel).
 * Tap tab -> buka/tutup panel daftar aplikasi yang terpasang.
 * Tahan lama (long press) tab -> menutup floating window sepenuhnya.
 * Drag vertikal pada tab -> menggeser posisi tab di sepanjang tepi layar.
 * Memilih aplikasi di panel -> membuka aplikasi tsb dalam mode freeform/mengambang
 * (jika perangkat mendukung), mirip fitur Freeform bawaan MIUI.
 */
public class FloatingWindowService extends Service {

    private static final String CHANNEL_ID = "floating_window_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int PANEL_WIDTH_DP = 240;
    private static final int CLICK_MOVE_THRESHOLD_PX = 14;
    private static final int LONG_PRESS_MS = 600;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private View appPanel;
    private LinearLayout appListContainer;
    private boolean panelOpen = false;
    private boolean appsLoaded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        showFloatingWindow();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Nempel di tepi kanan layar; panel akan melebar ke arah kiri saat dibuka.
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 0;
        params.y = 300;

        windowManager.addView(floatingView, params);

        appPanel = floatingView.findViewById(R.id.appPanel);
        appListContainer = floatingView.findViewById(R.id.appListContainer);
        View handle = floatingView.findViewById(R.id.ivHandle);
        ImageView closePanelButton = floatingView.findViewById(R.id.ivClosePanel);

        closePanelButton.setOnClickListener(v -> closePanel());
        setupHandleGestures(handle);
    }

    private void setupHandleGestures(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging = false;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (!isDragging && (Math.abs(dx) > CLICK_MOVE_THRESHOLD_PX
                                || Math.abs(dy) > CLICK_MOVE_THRESHOLD_PX)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            params.y = initialY + (int) dy;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        if (!isDragging) {
                            if (duration >= LONG_PRESS_MS) {
                                stopSelf();
                            } else {
                                togglePanel();
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void togglePanel() {
        if (panelOpen) {
            closePanel();
        } else {
            openPanel();
        }
    }

    private void openPanel() {
        if (!appsLoaded) {
            loadInstalledApps();
            appsLoaded = true;
        }
        appPanel.setVisibility(View.VISIBLE);

        float density = getResources().getDisplayMetrics().density;
        int targetWidth = (int) (PANEL_WIDTH_DP * density);

        animatePanelWidth(0, targetWidth);
        panelOpen = true;
    }

    private void closePanel() {
        int currentWidth = appPanel.getWidth();
        animatePanelWidth(currentWidth, 0);
        panelOpen = false;
    }

    private void animatePanelWidth(int from, int to) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(220);
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams lp = appPanel.getLayoutParams();
            lp.width = (int) animation.getAnimatedValue();
            appPanel.setLayoutParams(lp);
            if (windowManager != null && floatingView != null) {
                windowManager.updateViewLayout(floatingView, params);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (to == 0) {
                    appPanel.setVisibility(View.GONE);
                }
            }
        });
        animator.start();
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        appListContainer.removeAllViews();

        for (ResolveInfo info : apps) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(getPackageName())) {
                continue; // jangan tampilkan aplikasi ini sendiri
            }

            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_app_entry, appListContainer, false);
            ImageView icon = itemView.findViewById(R.id.ivAppIcon);
            TextView name = itemView.findViewById(R.id.tvAppName);

            try {
                Drawable appIcon = info.loadIcon(pm);
                icon.setImageDrawable(appIcon);
            } catch (Exception ignored) {
            }

            name.setText(info.loadLabel(pm));
            itemView.setOnClickListener(v -> launchAppFloating(packageName));

            appListContainer.addView(itemView);
        }
    }

    /**
     * Membuka aplikasi lain dalam mode jendela mengambang (freeform), mirip fitur
     * Freeform bawaan MIUI. Hanya benar-benar mengambang di perangkat yang
     * mendukung freeform window management; kalau tidak didukung, sistem akan
     * fallback membuka aplikasi secara normal (fullscreen) tanpa error.
     */
    private void launchAppFloating(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            Toast.makeText(this, "Tidak bisa membuka aplikasi ini", Toast.LENGTH_SHORT).show();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int screenWidth = metrics.widthPixels;
                int screenHeight = metrics.heightPixels;

                int winWidth = (int) (screenWidth * 0.85f);
                int winHeight = (int) (screenHeight * 0.65f);
                int left = (screenWidth - winWidth) / 2;
                int top = (int) (screenHeight * 0.12f);

                Rect bounds = new Rect(left, top, left + winWidth, top + winHeight);

                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchBounds(bounds);

                startActivity(launchIntent, options.toBundle());
            } else {
                startActivity(launchIntent);
            }
        } catch (Exception e) {
            startActivity(launchIntent);
        }

        closePanel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Floating Window Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Floating Window aktif")
                .setContentText("Tap tab untuk buka daftar app, tahan untuk tutup")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }
}
