package org.drg.jdbceptor.event;

/**
 * Listener whose callback is invoked whenever a connection it is attached to is closed.
 *
 * @author dgarson
 */
public interface ConnectionClosedListener {

    /**
     * Invoked whenever the parent connection is closed.
     * @param event event object containing information about connection that was closed
     */
    void connectionClosed(ConnectionClosedEvent event);
}
