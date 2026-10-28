package com.example.floatingwindow;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mengatur jendela aplikasi mengambang:
 *  - Root tersedia : buka dalam mode freeform, atur posisi/ukuran, pasang bingkai (geser & swipe-up tutup).
 *  - Tanpa root    : cukup ActivityOptions.setLaunchBounds (jendela bawaan sistem, tanpa bingkai sendiri).
 */
public final class FloatingAppManager implements FloatingWindowChrome.Listener {

    private static final long WATCH_INTERVAL_MS = 2000;

    private final Context context;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<FloatingWindowChrome> chromes = new ArrayList<>();
    private boolean watching = false;

    public FloatingAppManager(Context context, WindowManager windowManager) {
        this.context = context;
        this.wm = windowManager;
    }

    /** Buka aplikasi sebagai jendela mengambang. Aman dipanggil dari main thread. */
    public void open(final Intent launch) {
        final ComponentName cn = launch.getComponent();
        if (cn == null) {
            FloatingAppLauncher.launchFullscreen(context, launch);
            return;
        }
        final String pkg = cn.getPackageName();
        final String component = cn.flattenToShortString();
        final Rect bounds = cascade(FloatingAppLauncher.computeBounds(context), chromes.size());

        io.execute(new Runnable() {
            @Override
            public void run() {
                if (!RootShell.isAvailable()) {
                    main.post(new Runnable() {
                        @Override public void run() { FloatingAppLauncher.launch(context, launch, bounds); }
                    });
                    return;
                }

                if (!RootTasks.isFreeformActive(context)) {
                    RootTasks.enableFreeform(context);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context,
                                    "Freeform diaktifkan. Restart perangkat sekali, lalu coba lagi.",
                                    Toast.LENGTH_LONG).show();
                            FloatingAppLauncher.launchFullscreen(context, launch);
                        }
                    });
                    return;
                }

                RootTasks.launchFreeform(component);
                final int taskId = RootTasks.waitForTask(pkg, 4000);
                if (taskId < 0) return;

                sleep(350);                       // beri waktu sistem memindahkan task ke freeform
                RootTasks.resize(taskId, bounds);
                sleep(150);
                RootTasks.resize(taskId, bounds); // jaga-jaga bila resize pertama terlalu cepat

                main.post(new Runnable() {
                    @Override public void run() { addChrome(taskId, pkg, bounds); }
                });
            }
        });
    }

    /** Lepas semua bingkai (jendela aplikasi tidak ikut ditutup). Panggil di onDestroy(). */
    public void release() {
        for (FloatingWindowChrome c : chromes) c.dismiss();
        chromes.clear();
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
    }

    // ------------------------------------------------------------------

    private void addChrome(int taskId, String pkg, Rect bounds) {
        removeChromeByTask(taskId);   // hindari bingkai ganda untuk task yang sama
        FloatingWindowChrome chrome =
                new FloatingWindowChrome(context, wm, io, taskId, pkg, bounds, this);
        chromes.add(chrome);
        chrome.show();
        startWatching();
    }

    private void removeChromeByTask(int taskId) {
        for (int i = chromes.size() - 1; i >= 0; i--) {
            if (chromes.get(i).taskId == taskId) {
                chromes.get(i).dismiss();
                chromes.remove(i);
            }
        }
    }

    /** Swipe-up pada indikator bawah. */
    @Override
    public void onCloseRequested(final FloatingWindowChrome chrome) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = RootTasks.removeTask(context, chrome.taskId);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            removeChromeByTask(chrome.taskId);
                        } else {
                            chrome.resetSwipe();
                            Toast.makeText(context, "Gagal menutup jendela", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    /** Bingkai ikut hilang bila jendelanya ditutup dengan cara lain (tombol sistem, dll). */
    private void startWatching() {
        if (watching) return;
        watching = true;
        main.postDelayed(watchdog, WATCH_INTERVAL_MS);
    }

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (chromes.isEmpty()) {
                watching = false;
                return;
            }
            final List<Integer> ids = new ArrayList<>();
            for (FloatingWindowChrome c : chromes) ids.add(c.taskId);

            io.execute(new Runnable() {
                @Override
                public void run() {
                    final Map<Integer, String> tasks;
                    try {
                        tasks = RootTasks.listTasks();
                    } catch (IOException e) {
                        return;
                    }
                    if (tasks.isEmpty()) return;   // gagal membaca daftar: jangan hapus apa pun
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            for (int id : ids) {
                                if (!tasks.containsKey(id)) removeChromeByTask(id);
                            }
                        }
                    });
                }
            });
            main.postDelayed(this, WATCH_INTERVAL_MS);
        }
    };

    /** Geser sedikit tiap jendela baru agar tidak menumpuk persis. */
    private Rect cascade(Rect base, int index) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        Rect r = new Rect(base);
        r.offset(index * Math.round(28 * dm.density), 0);
        if (r.right > dm.widthPixels) r.offset(dm.widthPixels - r.right, 0);
        if (r.left < 0) r.offset(-r.left, 0);
        return r;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
