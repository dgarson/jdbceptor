package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;

/**
 * Listener whose callback is invoked whenever a connection it is attached to is closed.
 *
 * @author dgarson
 */
public interface ConnectionClosedListener {

    /**
     * Invoked whenever the parent connection is closed.
     * @param connection convenience reference to the owning connection
     */
    void connectionClosed(InstrumentedConnection connection);
}
