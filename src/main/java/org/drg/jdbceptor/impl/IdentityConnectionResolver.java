package org.drg.jdbceptor.impl;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.ConnectionResolver;

import java.sql.Connection;

/**
 * Constant identity implementation of the resolver which simply returns the parameter it is passed in but cast to
 * an instrumented connection.
 *
 * @author dgarson
 */
public class IdentityConnectionResolver implements ConnectionResolver {

    public static final IdentityConnectionResolver INSTANCE = new IdentityConnectionResolver();

    @Override
    public InstrumentedConnection resolveInstrumentedConnection(Connection connection) {
        try {
            return (InstrumentedConnection)connection;
        } catch (ClassCastException cce) {
            // wrap with an exception that provides a more useful message
            throw new UnsupportedOperationException(String.format("Cannot use IDENTITY connection resolver since " +
                    "%s is not an instance of %s", connection.getClass().getName(),
                InstrumentedConnection.class.getName()), cce);
        }
    }

    @Override
    public String generateConnectionId(Connection connection) {
        return Integer.toHexString(connection.hashCode());
    }
}
