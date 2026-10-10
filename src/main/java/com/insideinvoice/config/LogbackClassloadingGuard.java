package com.insideinvoice.config;

/**
 * Eagerly resolves the logback classes that a JUL-to-SLF4J log call needs, before Tomcat
 * starts accepting connections.
 *
 * <p>Tomcat logs socket failures (dead Neon pooler connections, abrupt client disconnects)
 * through {@code DirectJDKLog} -> {@code SLF4JBridgeHandler} -> logback. {@code ThrowableProxy}
 * is only ever touched on that error path, so it was first loaded concurrently from several
 * Tomcat worker threads. Under that first-load race the fat-jar {@code LaunchedClassLoader}
 * threw a transient {@link ClassNotFoundException}, the JVM permanently marked the class as
 * failed-to-initialize, and every later socket error died with
 * {@link NoClassDefFoundError} <em>from inside the log call</em>. The worker thread was killed
 * before it could write or close the HTTP response, which is why clients hung until their own
 * timeout and only a JVM restart recovered the process.</p>
 *
 * <p>Resolving them once on the main thread removes the race entirely. If resolution fails we
 * log through {@code System.err} rather than logback, so a logging problem can never mask
 * itself.</p>
 */
public final class LogbackClassloadingGuard {

    private static final String[] EAGER_CLASSES = {
            "ch.qos.logback.classic.spi.ThrowableProxy",
            "ch.qos.logback.classic.spi.ThrowableProxyUtil",
            "ch.qos.logback.classic.spi.LoggingEvent",
            "ch.qos.logback.classic.Logger",
            "org.slf4j.bridge.SLF4JBridgeHandler",
    };

    private LogbackClassloadingGuard() {
    }

    /** Called once from {@code main} before the Spring context (and Tomcat) is created. */
    public static void preload() {
        for (String name : EAGER_CLASSES) {
            try {
                Class.forName(name, true, LogbackClassloadingGuard.class.getClassLoader());
            } catch (Throwable t) {
                System.err.println("[logback-guard] could not preload " + name + ": " + t);
            }
        }
    }
}
