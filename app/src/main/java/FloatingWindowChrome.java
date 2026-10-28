package com.example.floatingwindow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.ExecutorService;

/**
 * "Bingkai" buatan sendiri di atas jendela aplikasi (sesuai preview):
 *  - bar hitam di atas   : tahan lalu geser = pindahkan jendela
 *  - indikator abu bawah : swipe ke atas    = tutup jendela
 * Keduanya adalah overlay kecil; jendela aplikasinya dipindah/ditutup lewat root.
 */
final class FloatingWindowChrome {

    interface Listener {
        void onCloseRequested(FloatingWindowChrome chrome);
    }

    // area sentuh & ukuran pil (dp), diukur dari preview
    private static final int HIT_W_DP = 160;
    private static final int HIT_H_DP = 28;
    private static final int PILL_W_DP = 113;
    private static final int PILL_H_DP = 5;
    private static final int CLOSE_SWIPE_DP = 60;

    private static final int COLOR_TOP = 0xFF000000;
    private static final int COLOR_BOTTOM = 0xFFE6E6E6;

    final int taskId;
    final String packageName;

    private final Context context;
    private final WindowManager wm;
    private final ExecutorService io;
    private final Listener listener;
    private final Rect bounds;

    private final PillView topView;
    private final PillView bottomView;
    private final WindowManager.LayoutParams topLp;
    private final WindowManager.LayoutParams bottomLp;

    private final Object lock = new Object();
    private Rect pending;
    private boolean resizing;
    private boolean shown;

    FloatingWindowChrome(Context context, WindowManager wm, ExecutorService io,
                         int taskId, String packageName, Rect bounds, Listener listener) {
        this.context = context;
        this.wm = wm;
        this.io = io;
        this.taskId = taskId;
        this.packageName = packageName;
        this.bounds = new Rect(bounds);
        this.listener = listener;

        topView = new PillView(context, COLOR_TOP, dp(PILL_W_DP), dp(PILL_H_DP));
        bottomView = new PillView(context, COLOR_BOTTOM, dp(PILL_W_DP), dp(PILL_H_DP));
        topLp = newParams();
        bottomLp = newParams();

        topView.setOnTouchListener(new DragTouch());
        bottomView.setOnTouchListener(new SwipeCloseTouch());
        layoutViews();
    }

    void show() {
        if (shown) return;
        wm.addView(topView, topLp);
        wm.addView(bottomView, bottomLp);
        shown = true;
    }

    void dismiss() {
        if (!shown) return;
        shown = false;
        try { wm.removeView(topView); } catch (IllegalArgumentException ignored) { }
        try { wm.removeView(bottomView); } catch (IllegalArgumentException ignored) { }
    }

    /** Dipanggil bila penutupan gagal: kembalikan indikator ke posisi semula. */
    void resetSwipe() {
        bottomView.animate().translationY(0).setDuration(120).start();
    }

    // ------------------------------------------------------------------

    private WindowManager.LayoutParams newParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(HIT_W_DP), dp(HIT_H_DP),
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    /** Posisikan kedua overlay mengikuti bounds jendela. */
    private void layoutViews() {
        topLp.x = bounds.centerX() - dp(HIT_W_DP) / 2;
        topLp.y = bounds.top - dp(2);
        bottomLp.x = topLp.x;
        bottomLp.y = bounds.bottom - dp(HIT_H_DP) + dp(5);
        if (shown) {
            try {
                wm.updateViewLayout(topView, topLp);
                wm.updateViewLayout(bottomView, bottomLp);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    /** Kirim ukuran/posisi terbaru ke root; hanya nilai terakhir yang dipakai (anti-antrean). */
    private void requestResize() {
        synchronized (lock) {
            pending = new Rect(bounds);
            if (resizing) return;
            resizing = true;
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Rect r;
                    synchronized (lock) {
                        r = pending;
                        pending = null;
                        if (r == null) {
                            resizing = false;
                            return;
                        }
                    }
                    RootTasks.resize(taskId, r);
                }
            }
        });
    }

    private final class DragTouch implements View.OnTouchListener {
        private float downX, downY;
        private final Rect start = new Rect();

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    start.set(bounds);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    DisplayMetrics dm = context.getResources().getDisplayMetrics();
                    int w = start.width(), h = start.height();
                    int left = clamp(start.left + Math.round(e.getRawX() - downX), 0, dm.widthPixels - w);
                    int top = clamp(start.top + Math.round(e.getRawY() - downY), 0, dm.heightPixels - h);
                    bounds.set(left, top, left + w, top + h);
                    layoutViews();
                    requestResize();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    requestResize();   // pastikan posisi akhir terkirim
                    return true;
            }
            return false;
        }
    }

    private final class SwipeCloseTouch implements View.OnTouchListener {
        private float downY;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float up = Math.max(0, downY - e.getRawY());
                    bottomView.setTranslationY(-Math.min(up, dp(40)));   // umpan balik visual
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float up = downY - e.getRawY();
                    if (up > dp(CLOSE_SWIPE_DP)) {
                        listener.onCloseRequested(FloatingWindowChrome.this);
                    } else {
                        resetSwipe();
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    resetSwipe();
                    return true;
            }
            return false;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, Math.max(min, max)));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** View transparan berisi satu "pil" di tengah. */
    private static final class PillView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float pillW, pillH;

        PillView(Context c, int color, float pillW, float pillH) {
            super(c);
            this.pillW = pillW;
            this.pillH = pillH;
            paint.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float l = (getWidth() - pillW) / 2f;
            float t = (getHeight() - pillH) / 2f;
            canvas.drawRoundRect(l, t, l + pillW, t + pillH, pillH / 2f, pillH / 2f, paint);
        }
    }
}
