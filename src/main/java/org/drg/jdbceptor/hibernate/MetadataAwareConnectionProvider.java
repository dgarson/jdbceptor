package org.drg.jdbceptor.hibernate;

import org.hibernate.connection.ConnectionProvider;

/**
 * Specialized extension of {@link org.hibernate.connection.ConnectionProvider} that provides additional metadata about
 * the data source that the provider connects to.
 *
 * @author dgarson
 */
public interface MetadataAwareConnectionProvider extends ConnectionProvider {

    /**
     * Returns the label/display name for the database that this connection provider is configured for.
     */
    String getDataSourceId();

    /**
     * Returns the JDBC URL that was used to configure this connection provider.
     */
    String getJdbcUrl();

    /**
     * Checks whether this connection provider has had instrumentation enabled.
     */
    boolean isInstrumented();
}

