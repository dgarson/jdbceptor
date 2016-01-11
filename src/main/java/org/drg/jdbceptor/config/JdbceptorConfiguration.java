package org.drg.jdbceptor.config;

/**
 * Configuration that provides necessary user-defined functionality to bring together all of the
 * different pieces of the Jdbceptor monitoring/instrumentation.
 *
 * @author dgarson
 */
public interface JdbceptorConfiguration {

    /**
     * Checks whether query parameters should be captured for prepared and callable statements. </br>
     * <strong>OVERHEAD NOTICE</strong>: Capturing parameterized query's raw SQL is more costly than straight-up SQL
     * statement execution because we must build a String representation of the fully formatted SQL query. This String
     * building occupies additional memory in the heap, which may or may not be consequential depending on the size of
     * <i>printable</i> queries values. This also has a small CPU cost, but in most cases, this is far less significant
     * than the memory overhead. </br>
     * Note that this feature will only ever be enabled for data sources that have statement tracking enabled and only
     * if that connection is being instrumented.
     * @see FeatureChecker#isCaptureStatementsEnabled()
     */
    boolean isCaptureQueryParametersEnabled();


}
