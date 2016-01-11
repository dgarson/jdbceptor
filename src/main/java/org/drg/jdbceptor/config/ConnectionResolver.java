package org.drg.jdbceptor.config;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.impl.IdentityConnectionResolver;

import java.sql.Connection;

/**
 * Resolves an {@link InstrumentedConnection} from an instance of {@link Connection}
 * that is being used on the Hibernate/ORM layer, or even just user code. </br>
 * Generally connection pools are used with Hibernate and other ORMs/framework code, in which case an implementation of
 * a resolver should be provided so that a top-level Connection can have the underlying, instrumented connection
 * returned. In the case of C3P0, this would be done by running:
 * <code>
 *     ((C3P0ProxyConnection)connection).rawConnectionOperation(InstrumentedConnection.class.getMethod("getThis"),
 *                  C3P0ProxyConnection.RAW_CONNECTION);
 * </code>
 *
 * @author dgarson
 */
public interface ConnectionResolver {

    /**
     * Reference to the identity implementation of the resolver which simply returns the parameter it is passed in but
     * cast to an instrumented connection.
     * @see IdentityConnectionResolver
     */
    static final ConnectionResolver IDENTITY = IdentityConnectionResolver.INSTANCE;

    /**
     * Resolves the correct {@link InstrumentedConnection} from Hibernate in cases where a connection pool may be used
     * and the connection being used by the user is in fact wrapping an {@link InstrumentedConnection}, meaning just
     * trying to cast may fail with a class cast exception.
     */
    InstrumentedConnection resolveInstrumentedConnection(Connection connection);

    /**
     * Returns the identifier that should be used for a given connection. This is assigned to that connection and used
     * throughout its lifetime. In the case of pooled connections, this identifier may be re-used, but will only ever
     * be associated with a single proxied connection at any given time.
     */
    String generateConnectionId(Connection connection);
}
