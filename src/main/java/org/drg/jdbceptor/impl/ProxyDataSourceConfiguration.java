package org.drg.jdbceptor.impl;

import com.google.common.base.Preconditions;
import org.drg.jdbceptor.api.ConnectionCustomizer;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.ConnectionResolver;
import org.drg.jdbceptor.config.DataSourceConfiguration;
import org.drg.jdbceptor.config.FeatureChecker;
import org.drg.jdbceptor.config.QueryParameterFormatter;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.drg.jdbceptor.hibernate.TransactionCustomizer;
import org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Proxy implementation of a data source configuration that will be inaccessible until the {@link #realConfig} is
 * injected. This allows static construction of undeclared data source configurations so that they may be referenced
 * prior to configuring all data sources.
 *
 * @author dgarson
 */
public class ProxyDataSourceConfiguration implements HibernateDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ProxyDataSourceConfiguration.class);

    private final String dataSourceId;
    private DataSourceConfiguration realConfig;

    public ProxyDataSourceConfiguration(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    /**
     * Checks if this data source configuration has been defined yet, since it is a placeholder so long as the
     * {@link #realConfig} is <code>null</code>.
     */
    public boolean isDefined() {
        return realConfig != null;
    }

    /**
     * Sets the real configuration that this proxy should not delegate all calls to.
     */
    public void setRealConfig(DataSourceConfiguration realConfig) {
        Preconditions.checkState(realConfig == null, "cannot inject a real configuration more than once into a proxy");
        log.trace("Injected real data source configuration into proxy for '" + dataSourceId + "'");
        this.realConfig = realConfig;
    }

    protected DataSourceConfiguration getRealConfig() {
        return realConfig;
    }

    @Nonnull
    @Override
    public String getId() {
        return dataSourceId;
    }

    private DataSourceConfiguration getRealConfigOrException() {
        if (realConfig == null) {
            throw new IllegalStateException("The real configuration for '" + dataSourceId + "' has not yet been " +
                "injected into the proxy so all methods other than getId() cannot be called.");
        }
        return realConfig;
    }

    private HibernateDataSourceConfiguration getHibernateConfigOrException() {
        DataSourceConfiguration config = getRealConfigOrException();
        if (!HibernateDataSourceConfiguration.class.isInstance(config)) {
            throw new IllegalStateException("Unable to use Hibernate data source configuration methods for '" +
                dataSourceId + "' when the proxy is not an instance of HibernateDataSourceConfiguration, " +
                "instead found: " + config.getClass().getName());
        }
        return (HibernateDataSourceConfiguration)config;
    }

    @Override
    public boolean isInstrumented() {
        return getRealConfigOrException().isInstrumented();
    }

    @Override
    public boolean isPoolingConnections() {
        return getRealConfigOrException().isPoolingConnections();
    }

    @Override
    public String generateTransactionId(InstrumentedConnection connection, InstrumentedTransaction transaction,
                                        int seqNo) {
        return getRealConfigOrException().generateTransactionId(connection, transaction, seqNo);
    }

    @Nullable
    @Override
    public ConnectionResolver getConnectionResolver() {
        return getRealConfigOrException().getConnectionResolver();
    }

    @Nullable
    @Override
    public ConnectionCustomizer getConnectionCustomizer() {
        return getRealConfigOrException().getConnectionCustomizer();
    }

    @Nonnull
    @Override
    public FeatureChecker getFeatureChecker() {
        return getRealConfigOrException().getFeatureChecker();
    }

    @Nullable
    @Override
    public QueryParameterFormatter getQueryParameterFormatter() {
        return getRealConfigOrException().getQueryParameterFormatter();
    }

    @Override
    public boolean isInstrumentTransactionsEnabled() {
        return getHibernateConfigOrException().isInstrumentTransactionsEnabled();
    }

    @Override
    public boolean isTransactionStatementCaptureEnabled() {
        return getHibernateConfigOrException().isTransactionStatementCaptureEnabled();
    }

    @Override
    public TransactionCustomizer getTransactionCustomizer() {
        return getHibernateConfigOrException().getTransactionCustomizer();
    }

    @Override
    public Class<? extends ConnectionProviderListener>[] getConnectionProviderListenerClasses() {
        return getHibernateConfigOrException().getConnectionProviderListenerClasses();
    }
}
