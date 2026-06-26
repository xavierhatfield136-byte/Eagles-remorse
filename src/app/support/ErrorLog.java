package app.support;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight persistent error logging for runtime and IO failures.
 */
public final class ErrorLog {
    private ErrorLog() {}

    private static final Object LOCK = new Object();
    private static final Path LOG_DIR = UserDataPaths.logDir();
    private static final Path LOG_FILE = LOG_DIR.resolve("error.log");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static volatile boolean globalHandlerInstalled = false;

    public static void installGlobalHandler() {
        if (globalHandlerInstalled) return;
        synchronized (LOCK) {
            if (globalHandlerInstalled) return;
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                    logException("uncaught thread=" + thread.getName(), throwable));
            globalHandlerInstalled = true;
        }
    }

    public static void logException(String scope, Throwable ex) {
        String normalizedScope = (scope == null || scope.isBlank()) ? "general" : scope.trim();
        StringBuilder entry = new StringBuilder(256);
        entry.append("[").append(TS_FORMAT.format(ZonedDateTime.now())).append("] ");
        entry.append("scope=").append(normalizedScope).append('\n');

        if (ex != null) {
            entry.append(ex.getClass().getSimpleName());
            String msg = ex.getMessage();
            if (msg != null && !msg.isBlank()) {
                entry.append(": ").append(msg);
            }
            entry.append('\n');

            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            entry.append(sw);
        } else {
            entry.append("null exception\n");
        }
        entry.append('\n');

        System.err.println("[error] " + normalizedScope + " reason=" + ((ex == null) ? "null" : ex.toString()));
        if (ex != null) ex.printStackTrace(System.err);

        synchronized (LOCK) {
            try {
                Files.createDirectories(LOG_DIR);
                Files.writeString(
                        LOG_FILE,
                        entry.toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } catch (IOException logEx) {
                System.err.println("[error-log] write_failed path=" + LOG_FILE + " reason=" + logEx.getMessage());
            }
        }
    }
}
