package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;

/**
 * Event that is fired when a connection is opened, or in the case of connection pooling, acquired from the pool.
 *
 * @author dgarson
 */
public class ConnectionOpenedEvent extends ConnectionEvent {

    /**
     * Creates a new opened event for a given <strong>connection</strong> and a boolean flag indicating whether the
     * connection is being wrapped by a connection pool and that is being integrated with the Jdbceptor driver.
     */
    public ConnectionOpenedEvent(InstrumentedConnection connection, long timestampNanos, boolean pooled) {
        super(connection, ConnectionEventType.CONNECTION_OPENED, timestampNanos, pooled, connection);
    }
}
