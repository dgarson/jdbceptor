package org.drg.jdbceptor.config;

import org.drg.jdbceptor.api.ConnectionCustomizer;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Data-source specific configuration elements that must be provided by the JdbceptorConfiguration in order to use a
 * data source with Jdbceptor. See the specialized sub-interface
 * {@link org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration} instead.
 *
 * @author dgarson
 * @see org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration
 */
public interface DataSourceConfiguration {

    /**
     * Returns the unique identifier for this data source.
     */
    @Nonnull String getId();

    /**
     * Returns the data source type.
     */
    @Nonnull DataSourceType getDataSourceType();

    /**
     * Checks whether instrumentation is enabled at all for this data source. This will apply to whether each newly
     * constructed connection and may also apply to pooled connections once they are re-acquired.
     */
    boolean isInstrumented();

    /**
     * Determines if a connection pool is being used around the instrumented JDBC driver, which requires special
     * handling when integrating with Hibernate.
     */
    boolean isPoolingConnections();

    /**
     * Generates a transaction identifier for a given connection and instrumented transaction.
     * @see org.drg.jdbceptor.hibernate.InstrumentedTransactionFactory
     */
    String generateTransactionId(InstrumentedConnection connection, InstrumentedTransaction transaction, int seqNo);

    /**
     * Returns a connection resolver that will be used if this data-source is wrapped in a connection pool and the
     * the underlying instrumented connection must be extracted/resolved from the pooled connection. This method may
     * return <code>null</code> if this is data-source is not using a pool.
     */
    @Nullable ConnectionResolver getConnectionResolver();

    /**
     * Returns an optional customizer that will be applied to every opened (or acquired) connection to this data source,
     * prior to firing the logical connection-opened event.
     */
    @Nullable ConnectionCustomizer getConnectionCustomizer();

    /**
     * Returns the feature checker that is applicable for this data source. This must never return
     * <code>null</code>.
     */
    @Nonnull FeatureChecker getFeatureChecker();

    /**
     * Returns a query parameter formatter that should be used for this data source. This is necessary to see any
     * parameterized arguments used in Prepared and Callable statements.
     */
    @Nullable QueryParameterFormatter getQueryParameterFormatter();
}
