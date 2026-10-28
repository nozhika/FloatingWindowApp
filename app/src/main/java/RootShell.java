package com.example.floatingwindow;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Shell root yang dibuka sekali dan dipakai berulang (lebih cepat dari memanggil "su -c" tiap perintah).
 * Semua method blocking: JANGAN dipanggil dari main thread.
 */
final class RootShell {

    private static final String END = "__FWA_END__";

    private static Process process;
    private static BufferedWriter out;
    private static BufferedReader in;
    private static Boolean available;

    private RootShell() { }

    /** true bila "su" ada dan diizinkan (pertama kali akan memunculkan dialog izin root). */
    static synchronized boolean isAvailable() {
        if (available != null) return available;
        try {
            available = run("id").contains("uid=0");
        } catch (IOException e) {
            available = false;
        }
        return available;
    }

    /** Jalankan perintah sebagai root, kembalikan stdout + stderr. */
    static synchronized String run(String command) throws IOException {
        ensureOpen();
        try {
            out.write("(" + command + ") 2>&1\n");
            out.write("echo " + END + "\n");
            out.flush();

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(END)) return sb.toString();
                sb.append(line).append('\n');
            }
            throw new IOException("root shell tertutup");
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private static void ensureOpen() throws IOException {
        if (process != null) return;
        process = Runtime.getRuntime().exec("su");
        out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        in = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    private static void close() {
        try { if (process != null) process.destroy(); } catch (Exception ignored) { }
        process = null;
        out = null;
        in = null;
    }
}
