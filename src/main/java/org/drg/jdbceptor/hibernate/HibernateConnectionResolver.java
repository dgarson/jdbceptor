package org.drg.jdbceptor.hibernate;

import org.drg.jdbceptor.api.InstrumentedConnection;

import java.sql.Connection;

/**
 * Resolves an {@link org.drg.jdbceptor.api.InstrumentedConnection} from an instance of {@link java.sql.Connection}
 * that is being used on the Hibernate/ORM layer. </br>
 * Generally connection pools are used with Hibernate, in which case an implementation of a resolver should be provided
 * so that a Hibernate-layer Connection can have the underlying, instrumented connection returned. In the case of C3P0,
 * this would be done by running:
 * <code>
 *     ((C3P0ProxyConnection)connection).rawConnectionOperation(InstrumentedConnection.class.getMethod("getThis"),
 *                  C3P0ProxyConnection.RAW_CONNECTION);
 * </code>
 */
public interface HibernateConnectionResolver {

    /**
     * Resolves the correct {@link InstrumentedConnection} from Hibernate in cases where a connection pool may be used
     * and the connection being used by Hibernate is in fact wrapping an InstrumentedConnection, meaning any direct cast
     * would fail.
     */
    InstrumentedConnection resolveInstrumentedConnection(Connection hibernateConnection);
}
