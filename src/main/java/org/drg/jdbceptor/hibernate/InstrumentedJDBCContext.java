package org.drg.jdbceptor.hibernate;

import org.hibernate.connection.ConnectionProvider;
import org.hibernate.jdbc.ConnectionManager;

import java.sql.Connection;

/**
 * Contract for any subclass of the {@link org.hibernate.jdbc.JDBCContext} that provides information about the state of
 * any connection that may or may not be attached/connected to it.
 *
 * @author dgarson
 */
public interface InstrumentedJDBCContext {

    /**
     * Returns an existing {@link java.sql.Connection} that is being held by this JDBCContext, or <code>null</code> if
     * there is no current connection attached and one would have to be acquired through the ConnectionProvider using
     * {@link ConnectionProvider#getConnection()}.
     */
    Connection getAttachedConnection();

    /**
     * Checks whether a <i>real</i> connection is attached to this context and is being managed by the owning
     * {@link org.hibernate.jdbc.JDBCContext#getConnectionManager()} instance.
     * @return true if a connection exists, false otherwise (even if logically open)
     */
    boolean isConnectionAttached();

    /**
     * Returns the associated connection manager instance for this context, which is never <code>null</code>.
     */
    ConnectionManager getConnectionManager();
}
