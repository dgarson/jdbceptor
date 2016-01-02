package org.drg.jdbceptor.hibernate.event;

import java.sql.Connection;

/**
 * Listener type that is invoked immediately prior to closing an open connection and returning it to the underlying
 * connection provider implementation (e.g. c3p0).
 *
 * @author dgarson
 * @created 6/5/15
 */
public interface PreConnectionCloseListener extends ConnectionProviderListener {

    /**
     * Callback invoked immediately prior to attempting to close an open connection, returning it to any underlying
     * connection pool.
     *
     * @param connection the connection being closed
     */
    void beforeClosingConnection(Connection connection);
}

