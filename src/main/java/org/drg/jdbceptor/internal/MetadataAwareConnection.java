package org.drg.jdbceptor.internal;

import org.drg.jdbceptor.api.InstrumentedConnection;

import javax.annotation.Nonnull;

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
    @Nonnull String getConnectionId();

    /**
     * Returns the identifier for the data source that this connection is connected to.
     * @see InstrumentedConnection#getDataSourceManager()
     */
    @Nonnull String getDataSourceId();

    /**
     * Checks if this connection has instrumentation enabled. This must remain constant for the entire period that this
     * connection is being held after being acquired.
     */
    boolean isInstrumented();

    /**
     * Injects metadata into this connection so that it can provide access to this data later through its getter
     * methods.
     * @param dataSourceId the identifier for the data source that this connection is connected to
     * @param connectionId the connection id
     * @param instrumented true if this connection is being fully instrumented and <code>false</code> if this method is
     *                      just being called for consistency
     */
    void setConnectionInfo(String dataSourceId, String connectionId, boolean instrumented);
}
