package com.example.floatingwindow;

import android.os.IBinder;

import java.lang.reflect.Method;

/**
 * Dijalankan lewat "app_process" sebagai root (bukan di dalam aplikasi):
 *   CLASSPATH=<base.apk> app_process /system/bin <nama kelas ini> remove <taskId>
 * Mencetak "OK" bila berhasil.
 */
public final class RootBridge {

    private RootBridge() { }

    public static void main(String[] args) {
        boolean ok = false;
        try {
            if (args.length >= 2 && "remove".equals(args[0])) {
                ok = removeTask(Integer.parseInt(args[1]));
            }
        } catch (Throwable t) {
            System.out.println("ERR " + t);
        }
        System.out.println(ok ? "OK" : "FAIL");
    }

    private static boolean removeTask(int taskId) throws Exception {
        Method getService = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class);

        // Android 10+ : IActivityTaskManager
        IBinder atmBinder = (IBinder) getService.invoke(null, "activity_task");
        if (atmBinder != null) {
            Object atm = Class.forName("android.app.IActivityTaskManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, atmBinder);
            Method remove = Class.forName("android.app.IActivityTaskManager")
                    .getMethod("removeTask", int.class);
            return (Boolean) remove.invoke(atm, taskId);
        }

        // Android 9 ke bawah : IActivityManager
        IBinder amBinder = (IBinder) getService.invoke(null, "activity");
        Object am = Class.forName("android.app.IActivityManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, amBinder);
        Method remove = Class.forName("android.app.IActivityManager")
                .getMethod("removeTask", int.class);
        return (Boolean) remove.invoke(am, taskId);
    }
}
