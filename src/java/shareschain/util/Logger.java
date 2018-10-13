
package shareschain.util;

import shareschain.Shareschain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.LogManager;

/**
 * Handle logging for the Shareschain node server
 */
public final class Logger {

    /** Log event types */
    public enum Event {
        MESSAGE, EXCEPTION
    }

    /** Log levels */
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /** Message listeners */
    private static final Listeners<String, Event> messageListeners = new Listeners<>();

    /** Exception listeners */
    private static final Listeners<Throwable, Event> exceptionListeners = new Listeners<>();

    /** Our logger instance */
    private static final org.slf4j.Logger log;

    /** Enable stack traces */
    private static final boolean enableStackTraces;

    /** Enable log traceback */
    private static final boolean enableLogTraceback;

    /**
     * No constructor
     */
    private Logger() {}

    /*
     * Logger initialization
     *
     * The existing Java logging configuration will be used if the Java logger has already
     * been initialized.  Otherwise, we will configure our own log manager and log handlers.
     * The shareschain/conf/logging-default.properties and shareschain/conf/logging.properties configuration
     * files will be used.  Entries in logging.properties will override entries in
     * logging-default.properties.
     */
    static {
        String oldManager = System.getProperty("java.util.logging.manager");
        System.setProperty("java.util.logging.manager", "shareschain.util.ShareschainLogManager");
        if (!(LogManager.getLogManager() instanceof ShareschainLogManager)) {
            System.setProperty("java.util.logging.manager",
                    (oldManager != null ? oldManager : "java.util.logging.LogManager"));
        }
        if (! Boolean.getBoolean("shareschain.doNotConfigureLogging")) {
            try {
                Properties loggingProperties = new Properties();
                Shareschain.loadProperties(loggingProperties, "logging-default.properties", true);
                Shareschain.loadProperties(loggingProperties, "logging.properties", false);
                Shareschain.updateLogFileHandler(loggingProperties);
                if (loggingProperties.size() > 0) {
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    loggingProperties.store(outStream, "logging properties");
                    ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                    java.util.logging.LogManager.getLogManager().readConfiguration(inStream);
                    inStream.close();
                    outStream.close();
                }
                BriefLogFormatter.init();
            } catch (IOException e) {
                throw new RuntimeException("Error loading logging properties", e);
            }
        }
        log = org.slf4j.LoggerFactory.getLogger(Shareschain.class);
        enableStackTraces = Shareschain.getBooleanProperty("shareschain.enableStackTraces");
        enableLogTraceback = Shareschain.getBooleanProperty("shareschain.enableLogTraceback");
        logInfoMessage("logging enabled");
    }

    /**
     * Logger initialization
     */
    public static void init() {}

    /**
     * Logger shutdown
     */
    public static void shutdown() {
        if (LogManager.getLogManager() instanceof ShareschainLogManager) {
            ((ShareschainLogManager) LogManager.getLogManager()).sharesChainShutdown();
        }
    }

    /**
     * Set the log level
     *
     * @param       level               Desired log level
     */
    public static void setLevel(Level level) {
        java.util.logging.Logger jdkLogger = java.util.logging.Logger.getLogger(log.getName());
        switch (level) {
            case DEBUG:
                jdkLogger.setLevel(java.util.logging.Level.FINE);
                break;
            case INFO:
                jdkLogger.setLevel(java.util.logging.Level.INFO);
                break;
            case WARN:
                jdkLogger.setLevel(java.util.logging.Level.WARNING);
                break;
            case ERROR:
                jdkLogger.setLevel(java.util.logging.Level.SEVERE);
                break;
        }
    }

    /**
     * Add a message listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener added
     */
    public static boolean addMessageListener(Listener<String> listener, Event eventType) {
        return messageListeners.addListener(listener, eventType);
    }

    /**
     * Add an exception listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener added
     */
    public static boolean addExceptionListener(Listener<Throwable> listener, Event eventType) {
        return exceptionListeners.addListener(listener, eventType);
    }

    /**
     * Remove a message listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener removed
     */
    public static boolean removeMessageListener(Listener<String> listener, Event eventType) {
        return messageListeners.removeListener(listener, eventType);
    }

    /**
     * Remove an exception listener
     *
     * @param       listener            Listener
     * @param       eventType           Notification event type
     * @return                          TRUE if listener removed
     */
    public static boolean removeExceptionListener(Listener<Throwable> listener, Event eventType) {
        return exceptionListeners.removeListener(listener, eventType);
    }

    /**
     * Log a message (map to INFO)
     *
     * @param       message             Message
     */
    public static void logMessage(String message) {
        doLog(Level.INFO, message, null);
    }

    /**
     * Log an exception (map to ERROR)
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logMessage(String message, Exception exc) {
        doLog(Level.ERROR, message, exc);
    }

    public static void logShutdownMessage(String message) {
        if (LogManager.getLogManager() instanceof ShareschainLogManager) {
            logMessage(message);
        } else {
            System.out.println(message);
        }
    }

    public static void logShutdownMessage(String message, Exception e) {
        if (LogManager.getLogManager() instanceof ShareschainLogManager) {
            logMessage(message, e);
        } else {
            System.out.println(message);
            System.out.println(e.toString());
        }
    }

    public static boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    /**
     * Log an ERROR message
     *
     * @param       message             Message
     */
    public static void logErrorMessage(String message) {
        doLog(Level.ERROR, message, null);
    }

    /**
     * Log an ERROR exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logErrorMessage(String message, Throwable exc) {
        doLog(Level.ERROR, message, exc);
    }

    public static boolean isWarningEnabled() {
        return log.isWarnEnabled();
    }

    /**
     * Log a WARNING message
     *
     * @param       message             Message
     */
    public static void logWarningMessage(String message) {
        doLog(Level.WARN, message, null);
    }

    /**
     * Log a WARNING exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logWarningMessage(String message, Throwable exc) {
        doLog(Level.WARN, message, exc);
    }

    public static boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    /**
     * Log an INFO message
     *
     * @param       message             Message
     */
    public static void logInfoMessage(String message) {
        doLog(Level.INFO, message, null);
    }

    /**
     * Log an INFO message
     *
     * @param       format             Message format
     * @param       args               Message args
     */
    public static void logInfoMessage(String format, Object ... args) {
        doLog(Level.INFO, String.format(format, args), null);
    }

    /**
     * Log an INFO exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logInfoMessage(String message, Throwable exc) {
        doLog(Level.INFO, message, exc);
    }

    public static boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    /**
     * Log a debug message
     *
     * @param       message             Message
     */
    public static void logDebugMessage(String message) {
        doLog(Level.DEBUG, message, null);
    }

    /**
     * Log a debug message
     *
     * @param       format             Message format
     * @param       args               Message args
     */
    public static void logDebugMessage(String format, Object ... args) {
        doLog(Level.DEBUG, String.format(format, args), null);
    }

    /**
     * Log a debug exception
     *
     * @param       message             Message
     * @param       exc                 Exception
     */
    public static void logDebugMessage(String message, Throwable exc) {
        doLog(Level.DEBUG, message, exc);
    }

    /**
     * Log the event
     *
     * @param       level               Level
     * @param       message             Message
     * @param       exc                 Exception
     */
    private static void doLog(Level level, String message, Throwable exc) {
        String logMessage = message;
        Throwable e = exc;
        //
        // Add caller class and method if enabled
        //
        if (enableLogTraceback) {
            StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
            String className = caller.getClassName();
            int index = className.lastIndexOf('.');
            if (index != -1)
                className = className.substring(index+1);
            logMessage = className + "." + caller.getMethodName() + "("+caller.getLineNumber()+"): " + logMessage;
        }
        //
        // Format the stack trace if enabled
        //
        if (e != null) {
            if (!enableStackTraces) {
                logMessage = logMessage + "\n" + exc.toString();
                e = null;
            }
        }
        //
        // Log the event
        //
        switch (level) {
            case DEBUG:
                log.debug(logMessage, e);
                break;
            case INFO:
                log.info(logMessage, e);
                break;
            case WARN:
                log.warn(logMessage, e);
                break;
            case ERROR:
                log.error(logMessage, e);
                break;
        }
        //
        // Notify listeners
        //
        if (exc != null)
            exceptionListeners.notify(exc, Event.EXCEPTION);
        else
            messageListeners.notify(message, Event.MESSAGE);
    }
}
