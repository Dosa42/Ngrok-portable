package com.ngrok;

import android.util.Log;

/** Android loader and initializer for the installed ngrok JNI library. */
public final class Runtime {
    private static final Logger LOGGER = new Logger();
    private static boolean initialized = false;

    public static Logger getLogger() {
        return LOGGER;
    }

    public static synchronized void load() {
        if (!initialized) {
            try {
                System.loadLibrary("ngrok_java");
                init(LOGGER);
                initialized = true;
                Log.i("ngrok", "ngrok_java JNI runtime successfully loaded and initialized");
            } catch (Throwable t) {
                Log.e("ngrok", "Failed to load and initialize ngrok_java runtime", t);
                throw new RuntimeException("Failed to initialize ngrok runtime: " + t.getMessage(), t);
            }
        }
    }

    public static native void init(Logger logger);

    public static final class Logger {
        public String getLevel() {
            return "INFO";
        }

        public void log(String level, String target, String message) {
            int priority;
            if (level == null) level = "INFO";
            switch (level.toUpperCase()) {
                case "ERROR": priority = Log.ERROR; break;
                case "WARN": priority = Log.WARN; break;
                case "DEBUG": priority = Log.DEBUG; break;
                case "TRACE": priority = Log.VERBOSE; break;
                default: priority = Log.INFO;
            }
            Log.println(priority, "ngrok", "[" + target + "] " + message);
        }
    }
}
