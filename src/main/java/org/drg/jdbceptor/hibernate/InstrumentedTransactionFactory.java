package org.drg.jdbceptor.hibernate;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.ConnectionResolver;
import org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration;
import org.drg.jdbceptor.hibernate.impl.InstrumentedJDBCContextImpl;
import org.drg.jdbceptor.hibernate.impl.InstrumentedTransactionImpl;
import org.drg.jdbceptor.impl.DataSourceManager;
import org.drg.jdbceptor.util.JdbcUtils;
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

import javax.annotation.Nonnull;

/**
 * Created by dgarson on 11/13/15.
 */
public class InstrumentedTransactionFactory implements TransactionFactory {

    private static final String HOSTNAME_PREFIX = JdbcUtils.getHostname();

    /**
     * Property in the configuration properties that declares the transaction factory implementation class to use under
     * the hood in this instrumented wrapper. If this property is undefined, then an exception will be thrown.
     */
    public static final String PROPERTY_DELEGATE_TRANSACTION_FACTORY = "jdbceptor.hibernate.transaction_factory_class";

    /**
     * Reference to the data source configuration associated with the connection provider for the SessionFactory that
     * owns this TransactionFactory.
     */
    private final HibernateDataSourceConfiguration dataSourceConfig;

    /**
     * The instance of {@link org.drg.jdbceptor.impl.DataSourceManager} that is keeping track of transactions and
     * connections to this data source. </br>
     * This cannot be injected until after the configurations are all processed.
     */
    private DataSourceManager dataSourceManager;

    /**
     * The actual TransactionFactory implementation being used.
     */
    private TransactionFactory targetFactory;

    /**
     * Reference to any ConnectionResolver provided for this data source in {@link #dataSourceConfig}.
     */
    private ConnectionResolver connectionResolver;

    /**
     * Reference to the connection provider that sits along side this transaction factory for the same SessionFactory.
     */
    private InstrumentedConnectionProvider connectionProvider;

    /**
     * AtomicLong that keeps track of the next identifier unique to this particular TransactionFactory instance, which
     * is always one-to-one with a particular {@link org.hibernate.SessionFactory}.
     */
    private final AtomicLong nextLocalTransactionId = new AtomicLong();

    public InstrumentedTransactionFactory() {
        dataSourceConfig = InstrumentedHibernateConfiguration.getCurrentDataSourceConfig();
        if (dataSourceConfig == null) {
            throw new IllegalStateException("You must use an InstrumentedHibernateConfiguration to build a " +
                "SessionFactory with Jdbceptor integration");
        }
        this.connectionResolver = dataSourceConfig.getConnectionResolver();
    }

    /**
     * Invoked after all dependencies have been injected into this component. This must always come after a call to the
     * {@link #setConnectionProvider(InstrumentedConnectionProvider)} method.
     */
    void initialize() {
        // no-op for now
    }

    /**
     * Provides a reference to the connection provider, which must be done after construction since its construction
     * also occurs during the SessionFactory building.
     * @throws IllegalArgumentException if <strong>connectionProvider</strong> is <code>null</code>
     */
    void setConnectionProvider(@Nonnull InstrumentedConnectionProvider connectionProvider) {
        Preconditions.checkNotNull(connectionProvider, "connectionProvider cannot be null");
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Transaction createTransaction(final JDBCContext jdbcContext, final Context context) throws HibernateException {
        // check whether we are instrumenting transactions for this data source at all, even if not right now
        if (!dataSourceConfig.isInstrumentTransactionsEnabled()) {
            return targetFactory.createTransaction(jdbcContext, context);
        }

        // wrap the contexts in delegates so we can intercept callbacks
        JDBCContext.Context proxyOwner = InstrumentedJDBCContextImpl.createProxyContext(this, jdbcContext);
        JDBCContext wrappedJdbcContext = (JDBCContext) InstrumentedJDBCContextImpl
            .createInstrumentedContext(proxyOwner, jdbcContext);

        // create the real transaction before doing anything, since there is a chance this could fail
        Transaction realTransaction = targetFactory.createTransaction(wrappedJdbcContext, proxyOwner);
        Connection rawConn = JdbcUtils.getConnectionFromJdbcContext(jdbcContext);
        InstrumentedConnection conn = (rawConn != null ? dataSourceManager.resolveInstrumentedConnection(rawConn)
            : null);
        if (rawConn != null) {
            if (connectionResolver != null) {
                conn = connectionResolver.resolveInstrumentedConnection(rawConn);
            } else if (rawConn instanceof InstrumentedConnection) {
                conn = (InstrumentedConnection)rawConn;
            } else {
                throw new IllegalStateException("Unable to coerce connection of type '" + rawConn.getClass() +
                    "' into an instance of InstrumentedConnection for data source '" + dataSourceConfig.getId() + "'");
            }
        }

        // return transaction without wrapping if we are not instrumenting this particular connection
        if (conn != null && !conn.isInstrumented()) {
            return realTransaction;
        }

        // generate a new unique identifier for the transaction and continue
        String transactionId = generateNextTransactionId(dataSourceConfig.getId(), conn);
        InstrumentedTransactionImpl transaction = new InstrumentedTransactionImpl(connectionProvider,
            jdbcContext, realTransaction, transactionId);

        // customize the transaction if there is a customizer defined
        dataSourceManager.customizeTransaction(transaction);

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

    private String generateNextTransactionId(String dataSourceId, InstrumentedConnection conn) {
        return generateNextTransactionId(dataSourceId, conn != null ? conn.getConnectionId() : null);
    }

    private String generateNextTransactionId(String dataSourceId, String connectionId) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(HOSTNAME_PREFIX);
        if (dataSourceId != null) {
            sb.append("_db-").append(dataSourceId);
        }
        if (connectionId != null) {
            sb.append("_c-").append(connectionId);
        }
        sb.append("_");
        sb.append(nextLocalTransactionId.incrementAndGet());
        return sb.toString();
    }
}
