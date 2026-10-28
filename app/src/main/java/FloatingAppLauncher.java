package com.example.floatingwindow;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.widget.Toast;

/**
 * Membuka aplikasi lain sebagai jendela mengambang (freeform window sistem Android).
 *
 * PENTING: ActivityOptions.setLaunchBounds() DIABAIKAN oleh Android bila perangkat tidak
 * mengaktifkan freeform (FEATURE_FREEFORM_WINDOW_MANAGEMENT). Itu sebabnya aplikasi
 * sebelumnya selalu terbuka layar penuh. Lihat CHANGES.md untuk cara mengaktifkannya.
 */
public final class FloatingAppLauncher {

    private FloatingAppLauncher() { }

    // Posisi & ukuran jendela dari preview lanskap (layar 2460 x 1080):
    // x 237..800, y 22..1057  ->  disimpan sebagai pecahan layar agar cocok di layar lain.
    private static final float LAND_LEFT = 237f / 2460f;
    private static final float LAND_TOP = 22f / 1080f;
    private static final float LAND_WIDTH = 563f / 2460f;
    private static final float LAND_HEIGHT = 1035f / 1080f;

    // Mode potret: belum ada preview -> sementara di tengah layar (silakan sesuaikan).
    private static final float PORT_WIDTH = 0.90f;
    private static final float PORT_HEIGHT = 0.60f;

    /** true bila sistem mengaktifkan freeform window. */
    public static boolean isFreeformSupported(Context context) {
        return context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT);
    }

    /** Hitung posisi & ukuran jendela sesuai orientasi layar saat ini. */
    public static Rect computeBounds(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int left, top, width, height;
        if (screenW > screenH) {                       // lanskap (sesuai preview)
            left = Math.round(LAND_LEFT * screenW);
            top = Math.round(LAND_TOP * screenH);
            width = Math.round(LAND_WIDTH * screenW);
            height = Math.round(LAND_HEIGHT * screenH);
        } else {                                       // potret
            width = Math.round(PORT_WIDTH * screenW);
            height = Math.round(PORT_HEIGHT * screenH);
            left = (screenW - width) / 2;
            top = (screenH - height) / 2;
        }
        return new Rect(left, top, left + width, top + height);
    }

    public static void launch(Context context, Intent intent) {
        launch(context, intent, computeBounds(context));
    }

    public static void launch(Context context, Intent intent, Rect bounds) {
        Intent i = new Intent(intent);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (!isFreeformSupported(context)) {
            // Tanpa freeform, Android pasti membuka layar penuh.
            Toast.makeText(context,
                    "Freeform belum aktif, aplikasi dibuka layar penuh",
                    Toast.LENGTH_LONG).show();
            context.startActivity(i);
            return;
        }

        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(bounds);
            context.startActivity(i, options.toBundle());
        } catch (RuntimeException e) {
            context.startActivity(i);   // cadangan: layar penuh
        }
    }
}
