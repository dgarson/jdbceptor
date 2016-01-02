package org.drg.jdbceptor.hibernate;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.event.TransactionListener;
import org.hibernate.ConnectionReleaseMode;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;
import org.hibernate.cfg.Environment;
import org.hibernate.jdbc.JDBCContext;
import org.hibernate.transaction.TransactionFactory;
import org.hibernate.transaction.TransactionFactoryFactory;

import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by dgarson on 11/13/15.
 */
public class InstrumentedTransactionFactory implements TransactionFactory {

    private static final String HOSTNAME_PREFIX = Host.getShortHostname();

    /**
     * Property in the configuration properties that declares the transaction factory implementation class to use under
     * the hood in this instrumented wrapper. If this property is undefined, then an exception will be thrown.
     */
    public static final String PROPERTY_DELEGATE_TRANSACTION_FACTORY = "jdbceptor.hibernate.transaction_factory_class";

    /**
     * The actual TransactionFactory implementation being used.
     */
    private TransactionFactory targetFactory;

    private TransactionCustomizer customizer;
    private HibernateConnectionResolver connectionResolver;
    private MetadataAwareConnectionProvider connectionProvider;
    private TransactionListener transactionInterceptor;

    private final HibernateAwareInstrumentationHandler instrumentationHandler;

    /**
     * AtomicLong that keeps track of the next identifier unique to this particular TransactionFactory instance, which
     * is always one-to-one with a particular {@link org.hibernate.SessionFactory}.
     */
    private final AtomicLong nextLocalTransactionId = new AtomicLong();

    public InstrumentedTransactionFactory() {
        InstrumentationHandler handler = Jdbceptor.getInstrumentationHandler();
        Preconditions.checkNotNull(handler, "instrumentationHandler not present!");
        if (!HibernateAwareInstrumentationHandler.class.isAssignableFrom(handler.getClass())) {
            throw new IllegalStateException("Unable to use InstrumentedTransactionFactory without " +
                handler.getClass() + " implementing the HibernateAwareInstrumentationHandler interface!");
        }
        instrumentationHandler = (HibernateAwareInstrumentationHandler) handler;
    }

    void setDependencies(MetadataAwareConnectionProvider connectionProvider,
                         TransactionCustomizer customizer, HibernateConnectionResolver connectionResolver) {
        this.connectionProvider = connectionProvider;
        this.transactionInterceptor = instrumentationHandler.getTransactionInterceptor(connectionProvider);
        this.customizer = customizer;
        this.connectionResolver = connectionResolver;
    }

    @Override
    public Transaction createTransaction(JDBCContext jdbcContext,
                                         Context context) throws HibernateException {
        Transaction realTransaction = targetFactory.createTransaction(jdbcContext, context);
        // return immediately and without decoration if not instrumented
        if (instrumentationHandler == null || !instrumentationHandler.isInstrumentTransactionsEnabled()) {
            return realTransaction;
        }
        Connection connection = ConnectionProviderUtils.getConnectionFromJdbcContext(jdbcContext);
        InstrumentedConnection conn = (connection != null ?
            (connectionResolver != null ? connectionResolver.resolveInstrumentedConnection(connection) :
                (InstrumentedConnection)connection) : null);
        // return transaction without wrapping if we are in pass-through mode
        if (conn != null && conn.isPassthrough()) {
            return realTransaction;
        }
        // generate a new unique identifier for the transaction and continue
        String transactionId = generateNextTransactionId(connectionProvider.getDataSourceId(),
            (conn != null ? conn.getConnectionId() : null));
        InstrumentedTransactionImpl transaction = new InstrumentedTransactionImpl(connectionProvider,
            jdbcContext, realTransaction, transactionInterceptor, transactionId);
        if (customizer != null) {
            customizer.customizeTransaction(transaction);
        }
        return transaction;
    }

    @Override
    public void configure(Properties props) throws HibernateException {
        String delegateFactoryClassName = props.getProperty(PROPERTY_DELEGATE_TRANSACTION_FACTORY);
        if (StringUtils.isBlank(delegateFactoryClassName)) {
            throw new HibernateException("Missing '" + PROPERTY_DELEGATE_TRANSACTION_FACTORY + "' property required to "
                + " use the InstrumentedTransactionFactory");
        }

        // create a new Properties object to pass off to build the delegate TransactionFactory, but override the
        // property that Hibernate uses to instantiate the TransactionFactory (since we don't want to instantiate
        // ourselves again
        Properties targetFactoryProps = new Properties(props);
        targetFactoryProps.setProperty(Environment.TRANSACTION_STRATEGY, delegateFactoryClassName);
        targetFactory = TransactionFactoryFactory.buildTransactionFactory(targetFactoryProps);
    }

    @Override
    public ConnectionReleaseMode getDefaultReleaseMode() {
        return targetFactory.getDefaultReleaseMode();
    }

    @Override
    public boolean isTransactionManagerRequired() {
        return targetFactory.isTransactionManagerRequired();
    }

    @Override
    public boolean areCallbacksLocalToHibernateTransactions() {
        return targetFactory.areCallbacksLocalToHibernateTransactions();
    }

    @Override
    public boolean isTransactionInProgress(JDBCContext jdbcContext, Context transactionContext,
                                           Transaction transaction) {
        return targetFactory.isTransactionInProgress(jdbcContext, transactionContext, transaction);
    }


    private String generateNextTransactionId(String databaseName, String connectionId) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(HOSTNAME_PREFIX);
        if (databaseName != null) {
            sb.append("_db-").append(databaseName);
        }
        if (connectionId != null) {
            sb.append("_c-").append(connectionId);
        }
        sb.append("_");
        sb.append(nextLocalTransactionId.incrementAndGet());
        return sb.toString();
    }
}
