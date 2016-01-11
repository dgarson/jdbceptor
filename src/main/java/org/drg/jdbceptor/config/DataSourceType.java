package org.drg.jdbceptor.config;

import org.drg.jdbceptor.api.InstrumentedConnection;

import java.sql.Connection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generalization about properties that may be specific to a given relational database backend, such as MySQL vs.
 * Postgres vs. MS-SQL
 *
 * @author dgarson
 */
public interface DataSourceType {

    /**
     * Returns the readable name for this data source type, which should correspond to the database backend product
     * being used.
     */
    @Nonnull String getName();

    /**
     * Formats a parameter value for use in monitoring parameters in {@link java.sql.PreparedStatement} and
     * {@link java.sql.CallableStatement} since both will have placeholder values instead of the parameter values, until
     * the moment the query is sent to the database for execution. This method will have no effect if the
     * query parameter capture feature is disabled globally thru the JdbceptorConfiguration.
     * @return the String value that should correspond exactly with how <strong>paramValue</strong> would be used or
     *          queried directly against the backend database (e.g. correctly formatting date-time values so that
     *          captured queries can be executed directly without having to sanitize parameter values
     * @see JdbceptorConfiguration#isCaptureQueryParametersEnabled()
     */
    @Nullable String formatParameter(Object paramValue);

    /**
     * Extracts or generates a unique identifier for a given database connection instance.
     * @param nativeConn the <i>native</i> connection that needs an identifier assigned
     * @return the identifier, which may never be <code>null</code>
     * @see InstrumentedConnection#getConnectionId()
     */
    @Nonnull String generateIdentifier(Connection nativeConn);
}
