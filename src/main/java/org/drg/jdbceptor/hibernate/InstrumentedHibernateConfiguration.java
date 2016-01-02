package org.drg.jdbceptor.hibernate;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.Settings;
import org.hibernate.connection.ConnectionProvider;
import org.hibernate.transaction.TransactionFactory;
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

    private final HibernateAwareInstrumentationHandler instrumentationHandler;

    public InstrumentedHibernateConfiguration() {
        InstrumentationHandler handler = Jdbceptor.getInstrumentationHandler();
        instrumentationHandler = (handler instanceof HibernateAwareInstrumentationHandler ?
            (HibernateAwareInstrumentationHandler)handler : null);
    }

    @Override
    public Settings buildSettings() throws HibernateException {
        Properties clone = ( Properties ) getProperties().clone();
        PropertiesHelper.resolvePlaceHolders(clone);
        preProcessProperties(clone);
        Settings settings = settingsFactory.buildSettings(clone);
        return postProcessSettings(settings);
    }

    @Override
    public Settings buildSettings(Properties props) throws HibernateException {
        preProcessProperties(props);
        Settings settings = settingsFactory.buildSettings(props);
        return postProcessSettings(settings);
    }

    private void preProcessProperties(Properties props) {
        if (instrumentationHandler == null) {
            return;
        }

        String transactionFactoryName = props.getProperty(Environment.TRANSACTION_STRATEGY);
        if (StringUtils.isNotBlank(transactionFactoryName) && !InstrumentedTransactionFactory.class.getName().equals(
            transactionFactoryName) && instrumentationHandler.isInstrumentTransactionsEnabled()) {
            // substitute in our InstrumentedTransactionFactory and setup the delegate impl. class name property
            props.setProperty(Environment.TRANSACTION_STRATEGY, InstrumentedTransactionFactory.class.getName());
            props.setProperty(InstrumentedTransactionFactory.PROPERTY_DELEGATE_TRANSACTION_FACTORY,
                transactionFactoryName);
        }
    }

    /**
     * Post-processes the Hibernate {@link Settings} that were built already and injects any necessary beans that are
     * used by instrumented Hibernate framework classes that cannot be injected directly since Hibernate manages all of
     * the object instantiation.
     */
    protected Settings postProcessSettings(Settings settings) throws HibernateException {
        if (instrumentationHandler == null) {
            return settings;
        }

        TransactionFactory factory = settings.getTransactionFactory();
        ConnectionProvider connProvider = settings.getConnectionProvider();
        if (factory instanceof InstrumentedTransactionFactory) {
            if (connProvider instanceof MetadataAwareConnectionProvider) {
                MetadataAwareConnectionProvider connectionProvider = (MetadataAwareConnectionProvider)connProvider;
                InstrumentedTransactionFactory transactionFactory = (InstrumentedTransactionFactory)factory;
                transactionFactory.setDependencies(connectionProvider,
                    instrumentationHandler.getTransactionCustomizer(connectionProvider),
                    instrumentationHandler.getConnectionResolver(connectionProvider));
            } else {
                log.warn("Unable to instrument transactions because the connection provider does not " +
                    "implement MetadataAwareConnectionProvider: {}", connProvider.getClass().getName());
            }
        }

        return settings;
    }
}
