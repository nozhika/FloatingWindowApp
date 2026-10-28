package com.example.floatingwindow;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

/**
 * Tab melayang yang menempel di tepi layar.
 *
 * Default   : tab sempit (10dp) menempel di tepi.
 * Ditahan   : setelah HOLD_DELAY_MS (0,5 dtk) tab melebar (26dp) dan baru bisa digeser.
 * Geser     : vertikal  -> memindahkan posisi tab sepanjang tepi.
 *             horizontal ke arah dalam layar -> membuka panel aplikasi (callback).
 * Tap cepat : klik biasa (view.performClick()).
 */
public class EdgeTabTouchListener implements View.OnTouchListener {

    public interface OnSwipeOpenListener {
        void onSwipeOpen(int tabY, int tabHeight);
    }

    public static final long HOLD_DELAY_MS = 500L;

    private static final int WIDTH_DEFAULT_DP = 10;
    private static final int WIDTH_EXPANDED_DP = 26;
    private static final int HEIGHT_DP = 110;
    private static final int OPEN_THRESHOLD_DP = 28;

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private final View view;
    private final boolean dockedRight;
    private final OnSwipeOpenListener openListener;

    private final int widthDefault;
    private final int widthExpanded;
    private final int openThreshold;
    private final int touchSlop;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean armed = false;   // sudah lewat jeda 0,5 dtk
    private boolean opened = false;  // panel sudah dibuka oleh gesture ini
    private boolean moved = false;   // jari bergeser sebelum armed

    private int startParamY;
    private float downRawX, downRawY, lastRawX, lastRawY;

    private final Runnable armRunnable = new Runnable() {
        @Override
        public void run() {
            armed = true;
            expand();
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startParamY = params.y;      // hitung ulang agar tidak "loncat"
            downRawX = lastRawX;
            downRawY = lastRawY;
        }
    };

    public EdgeTabTouchListener(Context context, WindowManager windowManager,
                                WindowManager.LayoutParams params, View view,
                                boolean dockedRight, OnSwipeOpenListener openListener) {
        this.context = context;
        this.windowManager = windowManager;
        this.params = params;
        this.view = view;
        this.dockedRight = dockedRight;
        this.openListener = openListener;
        this.widthDefault = dp(context, WIDTH_DEFAULT_DP);
        this.widthExpanded = dp(context, WIDTH_EXPANDED_DP);
        this.openThreshold = dp(context, OPEN_THRESHOLD_DP);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** Panggil sekali saat membuat LayoutParams (tab sempit di tepi, tengah layar). */
    public static void applyDefaultParams(Context context, WindowManager.LayoutParams p,
                                          boolean dockedRight) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        p.width = dp(context, WIDTH_DEFAULT_DP);
        p.height = dp(context, HEIGHT_DP);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = dockedRight ? dm.widthPixels - p.width : 0;
        p.y = (dm.heightPixels - p.height) / 2;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        lastRawX = event.getRawX();
        lastRawY = event.getRawY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                armed = false;
                opened = false;
                moved = false;
                startParamY = params.y;
                downRawX = lastRawX;
                downRawY = lastRawY;
                handler.postDelayed(armRunnable, HOLD_DELAY_MS);
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (opened) return true;
                float dx = lastRawX - downRawX;
                float dy = lastRawY - downRawY;

                if (!armed) {
                    // sebelum 0,5 dtk: geseran diabaikan
                    if (Math.hypot(dx, dy) > touchSlop) moved = true;
                    return true;
                }

                boolean inward = dockedRight ? dx < -openThreshold : dx > openThreshold;
                if (inward && Math.abs(dx) > Math.abs(dy)) {
                    opened = true;
                    collapse();
                    if (openListener != null) openListener.onSwipeOpen(params.y, params.height);
                    return true;
                }

                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                int maxY = Math.max(0, dm.heightPixels - params.height);
                params.y = Math.max(0, Math.min(startParamY + (int) dy, maxY));
                update();
                return true;
            }

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(armRunnable);
                if (armed && !opened) {
                    collapse();
                } else if (!armed && !moved) {
                    view.performClick();
                }
                armed = false;
                opened = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(armRunnable);
                if (armed && !opened) collapse();
                armed = false;
                opened = false;
                return true;
        }
        return false;
    }

    private void expand() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        params.width = widthExpanded;
        params.x = dockedRight ? dm.widthPixels - widthExpanded : 0;
        update();
    }

    private void collapse() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        params.width = widthDefault;
        params.x = dockedRight ? dm.widthPixels - widthDefault : 0;
        update();
    }

    private void update() {
        try {
            windowManager.updateViewLayout(view, params);
        } catch (IllegalArgumentException ignored) {
            // view sudah dilepas dari WindowManager
        }
    }

    /** Panggil di FloatingWindowService.onDestroy(). */
    public void release() {
        handler.removeCallbacksAndMessages(null);
    }

    private static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
