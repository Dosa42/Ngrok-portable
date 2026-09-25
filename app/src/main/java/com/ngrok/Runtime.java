package com.ngrok;

import android.util.Log;

/** Android loader for the installed ngrok JNI library. */
final class Runtime {
    private static final Logger LOGGER = new Logger();

    static Logger getLogger() {
        return LOGGER;
    }

    static void load() {
        System.loadLibrary("ngrok_java");
    }

    static native void init(Logger logger);

    static final class Logger {
        public String getLevel() {
            return "INFO";
        }

        public void log(String level, String target, String message) {
            int priority;
            switch (level) {
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
