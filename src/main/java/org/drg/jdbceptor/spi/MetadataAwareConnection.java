package org.drg.jdbceptor.spi;

/**
 * Internal interface that is used to mark a connection that can store and/or retrieve information about the associated
 * connection metadata. This is useful when using a connection pool (such as c3p0) and desire the pooled connection to
 * be updated with the underlying physical connection's metadata.
 *
 * @author dgarson
 */
public interface MetadataAwareConnection {

    /**
     * Returns a unique identifier for this connection.
     */
    String getConnectionId();

    /**
     * Returns the name of the database that this connection is connected to.
     *
     * @return the database name
     */
    String getDataSourceId();

    /**
     * Checks if this connection has instrumentation enabled.
     */
    boolean isInstrumented();

    /**
     * Injects metadata into this connection so that it can provide access to this data later thru its getter methods.
     */
    void setConnectionInfo(String databaseName, String connectionId, boolean instrumented);
}
