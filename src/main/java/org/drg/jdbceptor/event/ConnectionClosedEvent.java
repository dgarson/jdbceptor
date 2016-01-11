package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;

/**
 * Event that is fired when a connection is closed, or in the case of connection pooling, released back into the pool.
 *
 * @author dgarson
 */
public class ConnectionClosedEvent extends ConnectionEvent {

    /**
     * Creates a new closed event for a given <strong>connection</strong> and a boolean flag indicating whether the
     * connection is being wrapped by a connection pool and that is being integrated with the Jdbceptor driver.
     */
    public ConnectionClosedEvent(InstrumentedConnection connection, long timestampNanos, boolean pooled) {
        super(connection, ConnectionEventType.CONNECTION_CLOSED, timestampNanos, pooled, connection);
    }
}
