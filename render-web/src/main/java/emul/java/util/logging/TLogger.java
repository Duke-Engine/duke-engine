package emul.java.util.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * TeaVM's own Logger prints every level and breaks on a message that ends in a parameter ("{0}"), which jME's
 * messages do. This one keeps the JDK's rules: a level inherited from the parent, INFO at the root, and
 * MessageFormat-style "{n}" parameters.
 */
public class TLogger {
    public static final String GLOBAL_LOGGER_NAME = "global";
    private static final Map<String, TLogger> LOGGERS = new HashMap<>();

    private final String name;
    private final TLogger parent;
    private Level level;

    private TLogger(String name, TLogger parent) {
        this.name = name;
        this.parent = parent;
    }

    public static synchronized TLogger getLogger(String name) {
        TLogger logger = LOGGERS.get(name);
        if (logger == null) {
            TLogger parent = null;
            if (!name.isEmpty()) {
                int dot = name.lastIndexOf('.');
                parent = getLogger(dot < 0 ? "" : name.substring(0, dot));
            }
            logger = new TLogger(name, parent);
            if (name.isEmpty()) {
                logger.level = Level.INFO;
            }
            LOGGERS.put(name, logger);
        }
        return logger;
    }

    public static TLogger getLogger(String name, String resourceBundle) {
        return getLogger(name);
    }

    public static TLogger getGlobal() {
        return getLogger(GLOBAL_LOGGER_NAME);
    }

    public static TLogger getAnonymousLogger() {
        return new TLogger("", getLogger(""));
    }

    public String getName() {
        return name;
    }

    public TLogger getParent() {
        return parent;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public boolean isLoggable(Level level) {
        for (TLogger l = this; l != null; l = l.parent) {
            if (l.level != null) {
                return level.intValue() >= l.level.intValue() && l.level != Level.OFF;
            }
        }
        return level.intValue() >= Level.INFO.intValue();
    }

    public void log(Level level, String msg) {
        log(level, msg, (Object[]) null, null);
    }

    public void log(Level level, String msg, Object param) {
        log(level, msg, new Object[] {param}, null);
    }

    public void log(Level level, String msg, Object[] params) {
        log(level, msg, params, null);
    }

    public void log(Level level, String msg, Throwable thrown) {
        log(level, msg, null, thrown);
    }

    public void log(Level level, Supplier<String> msg) {
        if (isLoggable(level)) {
            log(level, msg.get(), null, null);
        }
    }

    public void log(Level level, Throwable thrown, Supplier<String> msg) {
        if (isLoggable(level)) {
            log(level, msg.get(), null, thrown);
        }
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        log(level, msg);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param) {
        log(level, msg, param);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
        log(level, msg, params);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        log(level, msg, thrown);
    }

    public void severe(String msg) { log(Level.SEVERE, msg); }
    public void warning(String msg) { log(Level.WARNING, msg); }
    public void info(String msg) { log(Level.INFO, msg); }
    public void config(String msg) { log(Level.CONFIG, msg); }
    public void fine(String msg) { log(Level.FINE, msg); }
    public void finer(String msg) { log(Level.FINER, msg); }
    public void finest(String msg) { log(Level.FINEST, msg); }
    public void severe(Supplier<String> msg) { log(Level.SEVERE, msg); }
    public void warning(Supplier<String> msg) { log(Level.WARNING, msg); }
    public void info(Supplier<String> msg) { log(Level.INFO, msg); }
    public void fine(Supplier<String> msg) { log(Level.FINE, msg); }

    private void log(Level level, String msg, Object[] params, Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        String line = "[" + level.getName() + "] " + name + ": " + format(msg, params);
        if (level.intValue() >= Level.WARNING.intValue()) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
        if (thrown != null) {
            thrown.printStackTrace();
        }
    }

    static String format(String msg, Object[] params) {
        if (msg == null || params == null || params.length == 0) {
            return msg;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < msg.length()) {
            char c = msg.charAt(i);
            int close = c == '{' ? msg.indexOf('}', i) : -1;
            if (close > i + 1) {
                String digits = msg.substring(i + 1, close);
                int comma = digits.indexOf(',');
                if (comma >= 0) {
                    digits = digits.substring(0, comma);
                }
                int n = -1;
                try {
                    n = Integer.parseInt(digits.trim());
                } catch (NumberFormatException e) {
                    // not a parameter: copied as it stands
                }
                if (n >= 0 && n < params.length) {
                    sb.append(params[n]);
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
