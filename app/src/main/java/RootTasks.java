package com.example.floatingwindow;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operasi jendela (task) aplikasi lain lewat root: aktifkan freeform, buka, pindah/ubah ukuran, tutup.
 * Semua method blocking: panggil dari background thread.
 */
final class RootTasks {

    private RootTasks() { }

    private static final String TAG = "FWA";   // logcat -s FWA
    private static final String PREFS = "floating_panel";
    private static final String KEY_FREEFORM_SET_AT_BOOT = "freeform_set_at_boot";

    // contoh baris:  Task{a1b2c3d #12 type=standard A=10213:com.whatsapp U=0 ...}
    //                TaskRecord{a1b2c3d #12 A=com.whatsapp U=0 StackId=2 sz=1}
    private static final Pattern TASK =
            Pattern.compile("(?:Task|TaskRecord)\\{[^}]*?#(\\d+)[^}]*?\\bA=(?:\\d+:)?([\\w.]+)");

    /** Sintaks "task resize" berbeda antar versi Android (spasi vs koma). Dicoba otomatis. */
    private static boolean spaceSyntax = true;

    // ---------------------------------------------------------------- freeform

    /**
     * true bila freeform benar-benar aktif. Setelah kita menyalakan settingnya sendiri,
     * Android baru memakainya setelah restart (dideteksi lewat boot_count).
     */
    static boolean isFreeformActive(Context c) {
        boolean on = Settings.Global.getInt(c.getContentResolver(), "enable_freeform_support", 0) == 1;
        if (!on) return false;

        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int setAt = sp.getInt(KEY_FREEFORM_SET_AT_BOOT, -1);
        if (setAt < 0) return true;   // dinyalakan pengguna sendiri lewat Opsi pengembang
        int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        return boot > setAt;
    }

    static void enableFreeform(Context c) {
        try {
            RootShell.run("settings put global enable_freeform_support 1");
            RootShell.run("settings put global force_resizable_activities 1");
            int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_FREEFORM_SET_AT_BOOT, boot).apply();
        } catch (IOException ignored) { }
    }

    // ---------------------------------------------------------------- buka

    /** Buka komponen langsung dalam mode freeform (windowing mode 5). */
    static String launchFreeform(String flattenedComponent) {
        try {
            String out = RootShell.run("am start --windowingMode 5 -n " + flattenedComponent);
            Log.d(TAG, "launchFreeform " + flattenedComponent + ": " + out.trim());
            return out;
        } catch (IOException e) {
            return "ERR " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------- daftar task

    /** task id -> package, dibaca dari "dumpsys activity activities". */
    static Map<Integer, String> listTasks() throws IOException {
        String dump = RootShell.run("dumpsys activity activities");
        Map<Integer, String> map = new LinkedHashMap<>();
        Matcher m = TASK.matcher(dump);
        while (m.find()) {
            map.put(Integer.parseInt(m.group(1)), m.group(2));
        }
        return map;
    }

    /** Task id terbaru (id terbesar) milik package, atau -1. */
    static int findTaskId(String pkg) {
        try {
            int best = -1;
            for (Map.Entry<Integer, String> e : listTasks().entrySet()) {
                if (pkg.equals(e.getValue()) && e.getKey() > best) best = e.getKey();
            }
            return best;
        } catch (IOException e) {
            return -1;
        }
    }

    /** Tunggu sampai task package muncul. Mengembalikan task id atau -1 bila timeout. */
    static int waitForTask(String pkg, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int id = findTaskId(pkg);
            if (id >= 0) return id;
            try { Thread.sleep(250); } catch (InterruptedException e) { return -1; }
        }
        Log.w(TAG, "task untuk " + pkg + " tidak ditemukan di dumpsys (format baris mungkin berbeda)");
        return -1;
    }

    // ---------------------------------------------------------------- pindah / ubah ukuran

    static void resize(int taskId, Rect r) {
        try {
            String out = RootShell.run(resizeCommand(taskId, r, spaceSyntax));
            if (looksLikeError(out)) {
                Log.w(TAG, "resize#" + taskId + " gagal (" + (spaceSyntax ? "spasi" : "koma") + "): " + out.trim());
                String retry = RootShell.run(resizeCommand(taskId, r, !spaceSyntax));
                if (!looksLikeError(retry)) {
                    spaceSyntax = !spaceSyntax;   // sintaks yang cocok dipakai seterusnya
                } else {
                    Log.w(TAG, "resize#" + taskId + " gagal juga dengan sintaks lain: " + retry.trim());
                }
            }
        } catch (IOException ignored) { }
    }

    private static String resizeCommand(int id, Rect r, boolean space) {
        String base = "cmd activity task resize " + id + " ";
        return space
                ? base + r.left + " " + r.top + " " + r.right + " " + r.bottom
                : base + r.left + "," + r.top + "," + r.right + "," + r.bottom;
    }

    private static boolean looksLikeError(String out) {
        String s = out.toLowerCase();
        return s.contains("error") || s.contains("exception") || s.contains("usage")
                || s.contains("unknown") || s.contains("invalid");
    }

    // ---------------------------------------------------------------- tutup

    /**
     * Menutup task. Tidak ada perintah shell yang pasti untuk ini di Android modern, jadi memakai
     * proses kecil (RootBridge) yang memanggil IActivityTaskManager.removeTask() sebagai root.
     */
    static boolean removeTask(Context c, int taskId) {
        try {
            String cmd = "CLASSPATH=" + c.getPackageCodePath()
                    + " app_process /system/bin " + RootBridge.class.getName()
                    + " remove " + taskId;
            String out = RootShell.run(cmd);
            Log.d(TAG, "removeTask#" + taskId + ": " + out.trim());
            return out.contains("OK");
        } catch (IOException e) {
            return false;
        }
    }
}
