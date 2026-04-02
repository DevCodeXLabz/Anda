package android.util;

/**
 * Minimal stub of android.util.Log for JVM unit tests.
 * Methods return 0 and do not perform any logging. This file is included only
 * in the test classpath and prevents "Method X in android.util.Log not mocked" errors.
 */
public final class Log {
    private Log() {}

    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable t) { return 0; }

    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable t) { return 0; }

    public static int i(String tag, String msg) { return 0; }
    public static int w(String tag, String msg) { return 0; }
}

