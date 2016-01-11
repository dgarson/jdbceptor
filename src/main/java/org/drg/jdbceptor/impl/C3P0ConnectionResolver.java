package org.drg.jdbceptor.impl;

import com.mchange.v2.c3p0.C3P0ProxyConnection;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.ConnectionResolver;
import org.drg.jdbceptor.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Provided implementation for a {@link org.drg.jdbceptor.config.ConnectionResolver} that
 *
 * @author dgarson
 */
public class C3P0ConnectionResolver implements ConnectionResolver {

    public static final C3P0ConnectionResolver INSTANCE = new C3P0ConnectionResolver();

    private static final Object[] NO_ARGS = new Object[0];

    /**
     * Static reference to the {@link InstrumentedConnectionImpl#getThis()} helper method used with c3p0.
     */
    private static final Method GET_THIS_METHOD = ReflectionUtils.findMethod(InstrumentedConnectionImpl.class,
        "getThis");

    @Override
    public InstrumentedConnection resolveInstrumentedConnection(Connection connection) {
        Connection unwrapped = unwrapPooledConnection(connection);
        if (unwrapped == null || !(unwrapped instanceof InstrumentedConnection)) {
            throw new IllegalArgumentException("Unable to resolve an InstrumentedConnection from a connection of " +
                "type " + connection.getClass());
        }
        return (InstrumentedConnection)unwrapped;
    }

    protected Connection unwrapPooledConnection(Connection connection) {
        if (connection instanceof C3P0ProxyConnection) {
            C3P0ProxyConnection c3p0Conn = (C3P0ProxyConnection) connection;
            try {
                return (Connection) c3p0Conn.rawConnectionOperation(GET_THIS_METHOD, connection, NO_ARGS);
            } catch (InvocationTargetException ite) {
                if (ite.getTargetException() instanceof SQLException) {
                    throw new IllegalArgumentException("Unable to invoke 'getThis' on connection wrapped by c3p0 due " +
                        "to database exception", ite.getTargetException());
                } else {
                    throw new IllegalStateException("Unable to invoke 'getThis' on connection wrapped by c3p0 due " +
                        "to unexpected exception", ite.getTargetException());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to access 'getThis' method of connection wrapped by c3p0: " +
                    e.getMessage());
            } catch (SQLException se) {
                throw new IllegalArgumentException("Unable to invoke getThis() on connection of type " +
                    connection.getClass().getName() + ". Is it wrapping a Jdbceptor connection?", se);
            }
        }
        // nothing to do if it's not wrapped by c3p0
        return connection;
    }

    @Override
    public String generateConnectionId(Connection connection) {
        // TODO FIXME(dgarson) implement this properly...
        return UUID.randomUUID().toString();
    }

    public static C3P0ConnectionResolver getInstance() {
        return INSTANCE;
    }
}
