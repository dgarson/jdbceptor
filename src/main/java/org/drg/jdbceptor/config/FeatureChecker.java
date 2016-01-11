package org.drg.jdbceptor.config;

/**
 * Feature checkers determine whether or not certain instrumentation capabilities should be
 * enabled at a given point in time.
 *
 * @author dgarson
 */
public interface FeatureChecker {

    /**
     * Determines if connections and statements should be instrumented at this moment. </br>
     * If this returns <code>false</code> when a connection is being opened, then it will be created in pass-through
     * mode and all further invocations of this method will be skipped for statement instrumentation within that
     * connection. </br>
     * This is done as an optimization in the event that this method has some performance cost, such as multiple
     * thread-local retrieval, String/Date parsing, etc.
     */
    boolean shouldInstrumentConnection();

    /**
     * Checks whether we should capture SQL statements for connections that are being instrumented. </br>
     * This is not tunable on a per-query basis because all of those checks would be costly. Instead, this method is
     * consulted and enabled/disabled is cached when a connection is opened rather than when executing each statement.
     * </br>
     * In the case of pooled connections, so long as the data source connection indicates it is pooled and
     * a connection resolver is provided, then this will instead apply to the <i>pooled</i> connection rather
     * than the underlying &quot;physical&quot; connection.
     */
    boolean shouldCaptureStatements();
}
