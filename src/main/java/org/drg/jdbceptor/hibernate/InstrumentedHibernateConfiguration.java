package org.drg.jdbceptor.hibernate;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.hibernate.config.HibernateConfigurationAware;
import org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration;
import org.hibernate.HibernateException;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.Settings;
import org.hibernate.cfg.SettingsHelper;
import org.hibernate.connection.ConnectionProvider;
import org.hibernate.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Instrumented subclass of Hibernate's provided {@link AnnotationConfiguration} that post-processes the Settings prior
 * to building the actual SessionFactory so that any necessary dependencies can be wired in prior to actually using any
 * part of that SessionFactory (including during construction).
 *
 * @author dgarson
 */
public class InstrumentedHibernateConfiguration extends AnnotationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedHibernateConfiguration.class);

    private static final ThreadLocal<HibernateDataSourceConfiguration> currentDataSourceConfig =
        new ThreadLocal<>();

    private final HibernateDataSourceConfiguration dataSourceConfig;

    public InstrumentedHibernateConfiguration(HibernateDataSourceConfiguration dataSourceConfig) {
        Preconditions.checkNotNull(dataSourceConfig, "dataSourceConfig was null");
        this.dataSourceConfig = dataSourceConfig;
    }

    @Override
    public Settings buildSettings() throws HibernateException {
        // first clone and resolve placeholders, then create all the components
        Properties clone = ( Properties ) getProperties().clone();
        PropertiesHelper.resolvePlaceHolders(clone);
        return buildSettings(clone);
    }

    @Override
    public Settings buildSettings(Properties props) throws HibernateException {
        try {
            preProcessProperties(props);
            Settings settings = settingsFactory.buildSettings(props);
            return postProcessSettings(settings);
        } finally {
            currentDataSourceConfig.remove();
        }
    }

    private void preProcessProperties(Properties props) {
        // make sure to set the thread-local prior to building the various beans for this SessionFactory
        currentDataSourceConfig.set(dataSourceConfig);

        // skip all remaining pre-processing if the data source is not being instrumented
        if (!dataSourceConfig.isInstrumented()) {
            return;
        }

        // substitute in our own instrumented transaction factory and specify the existing impl class name as the
        //      delegate we will use in the InstrumentedTransactionFactory
        String transactionFactoryName = props.getProperty(Environment.TRANSACTION_STRATEGY);
        if (StringUtils.isNotBlank(transactionFactoryName) && !InstrumentedTransactionFactory.class.getName().equals(
            transactionFactoryName) && dataSourceConfig.isInstrumentTransactionsEnabled()) {
            // substitute in our InstrumentedTransactionFactory and setup the delegate impl. class name property
            props.setProperty(Environment.TRANSACTION_STRATEGY, InstrumentedTransactionFactory.class.getName());
            props.setProperty(InstrumentedTransactionFactory.PROPERTY_DELEGATE_TRANSACTION_FACTORY,
                transactionFactoryName);
            log.trace("Substituted InstrumentedTransactionFactory for existing transaction manager property: '{}'",
                transactionFactoryName);
        }

        // substitute in our own ConnectionProvider class name if not already specified
        String connectionProviderClass = props.getProperty(Environment.CONNECTION_PROVIDER);
        if (StringUtils.isNotBlank(connectionProviderClass) && !connectionProviderClass.equals(
            InstrumentedConnectionProvider.class.getName())) {
            props.setProperty(Environment.CONNECTION_PROVIDER, InstrumentedConnectionProvider.class.getName());
            props.setProperty(InstrumentedConnectionProvider.DELEGATING_CONNECTION_PROVIDER_CLASS,
                connectionProviderClass);
            log.trace("Substituted connection provider class name with InstrumentedConnectionProvider which will " +
                "delegate to {} for data source '{}'", connectionProviderClass, dataSourceConfig.getId());
        }
    }

    /**
     * Post-processes the Hibernate {@link Settings} that were built already and injects any necessary beans that are
     * used by instrumented Hibernate framework classes that cannot be injected directly since Hibernate manages all of
     * the object instantiation.
     */
    protected Settings postProcessSettings(Settings settings) throws HibernateException {
        if (!dataSourceConfig.isInstrumented()) {
            return settings;
        }

        // drop in our proxied batcher factory so we can toggle on/off whether we are really creating batchers
        //      (see the InstrumentedJDBCContextImpl for use cases)
        SettingsHelper.setupProxyBatcherFactory(settings);

        // retrieve bean instances and do all of our internal wiring
        ConnectionProvider connProvider = settings.getConnectionProvider();
        if (connProvider instanceof InstrumentedConnectionProvider) {
            // ensure all dependencies are injected in the connection provider
            InstrumentedConnectionProvider connectionProvider = (InstrumentedConnectionProvider)connProvider;
            if (connectionProvider instanceof HibernateConfigurationAware) {
                ((HibernateConfigurationAware) connectionProvider).setHibernateConfiguration(this);
            }

            // ensure all dependencies are injected in the transaction factory
            InstrumentedTransactionFactory transactionFactory = (InstrumentedTransactionFactory)settings
                .getTransactionFactory();
            transactionFactory.setConnectionProvider(connectionProvider);
            if (transactionFactory instanceof HibernateConfigurationAware) {
                ((HibernateConfigurationAware)transactionFactory).setHibernateConfiguration(this);
            }
            // finalize initialization of the transaction factory
            transactionFactory.initialize();

        }

        // all done - return settings for further processing
        return settings;
    }

    static HibernateDataSourceConfiguration getCurrentDataSourceConfig() {
        return currentDataSourceConfig.get();
    }
}
