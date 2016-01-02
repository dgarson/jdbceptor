package org.drg.jdbceptor.hibernate;

import com.google.common.annotations.VisibleForTesting;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.event.TransactionListener;
import org.drg.jdbceptor.impl.InstrumentedConnectionImpl;
import org.drg.jdbceptor.util.JdbcUrlUtils;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;
import org.hibernate.jdbc.JDBCContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.transaction.Synchronization;

/**
 * @author dgarson
 */
public class InstrumentedTransactionImpl implements Transaction, InstrumentedTransaction {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedTransactionImpl.class);

    private final HibernateAwareInstrumentationHandler instrumentationHandler;
    private final MetadataAwareConnectionProvider connectionProvider;
    private final TransactionInterceptor interceptor;
    private final Transaction realTransaction;
    private final String transactionId;

    // non-final in case the connection is lazily opened
    private InstrumentedConnection connection;
    // used to acquire the connection instance on lazily opened connections
    private final JDBCContext jdbcContext;

    private List<TransactionListener> transactionListeners;

    long openedTimestamp; // timestamp when the begin() method was called
    long closedTimestamp;

    // optional user-data attached to this transaction
    private Object userData;

    private final AtomicBoolean marked = new AtomicBoolean(false);

    InstrumentedTransactionImpl(MetadataAwareConnectionProvider connectionProvider, JDBCContext jdbcContext,
                                Transaction realTransaction, TransactionInterceptor interceptor, String transactionId) {
        // this should always be the case otherwise an instance of this class should have never been allowed to be
        //          constructed
        this.instrumentationHandler = (HibernateAwareInstrumentationHandler) Jdbceptor.getInstrumentationHandler();
        this.connectionProvider = connectionProvider;
        this.interceptor = interceptor;
        this.jdbcContext = jdbcContext;
        this.realTransaction = realTransaction;
        this.transactionId = transactionId;
    }

    @Override
    public MetadataAwareConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    @Override
    public synchronized void addTransactionListener(TransactionListener listener) {
        if (transactionListeners == null) {
            transactionListeners = new ArrayList<>();
        }
        transactionListeners.add(listener);
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public long getOpenedTimestamp() {
        return openedTimestamp;
    }

    @VisibleForTesting
    public long getClosedTimestamp() {
        return closedTimestamp;
    }

    @Override
    public long getDurationMillis() {
        if (openedTimestamp == 0) {
            return 0;
        }
        return (closedTimestamp > 0 ? (closedTimestamp - openedTimestamp) :
            (System.currentTimeMillis() - openedTimestamp));
    }

    @Override
    public String getDatabaseName() {
        return connectionProvider.getDataSourceId();
    }

    @Override
    public InstrumentedConnection getConnection() {
        return connection;
    }

    @Override
    public Object getUserData() {
        return userData;
    }

    @Override
    public void setUserData(Object userData) {
        this.userData = userData;
    }

    @Override
    public void begin() throws HibernateException {
        realTransaction.begin();
        onBegin();
    }

    private void onBegin() {
        refreshConnection();

        // notify parent connection if present
        if (connection != null) {
            ((InstrumentedConnectionImpl)connection).beginningTransaction(this);
        }

        // notify connection that we began
        if (transactionListeners != null) {
            for (TransactionListener listener : transactionListeners) {
                listener.transactionBegan(this);
            }
        }

    }

    @Override
    public void commit() throws HibernateException {
        try {
            realTransaction.commit();
            onCommit();
        } catch (HibernateException e) {
            onCommitFailed(e);
            throw e;
        }
    }

    private void onCommit() {
        // refresh connection field in case the connection has been opened between calling commit() and now
        InstrumentedConnectionImpl conn = (InstrumentedConnectionImpl)connection;
        // this may cause the 'connection' field to become null if using RELEASE_ON_CLOSE or what not ;-)
        refreshConnection();

        if (transactionListeners != null) {
            for (TransactionListener listener : transactionListeners) {
                listener.transactionCommitted(this);
            }
        }

        // notify parent connection if present
        if (conn != null) {
            conn.finishedTransaction(this, /*committed=*/true);
        }
    }

    private void onCommitFailed(HibernateException e) {
        // refresh connection field in case the connection has been opened between calling commit() and now
        refreshConnection();

        if (transactionListeners != null) {
            for (TransactionListener listener : transactionListeners) {
                listener.transactionCommitFailed(this, e);
            }
        }
    }

    @Override
    public void rollback() throws HibernateException {
        try {
            realTransaction.rollback();
            onRollback();
        } catch (HibernateException e) {
            rollbackFailed(e);
            throw e;
        }
    }

    private void onRollback() {
        InstrumentedConnectionImpl conn = (InstrumentedConnectionImpl)connection;
        if (conn != null) {
            conn.finishedTransaction(this, /*committed=*/false);
        }

        if (transactionListeners != null) {
            for (TransactionListener listener : transactionListeners) {
                listener.transactionRolledBack(this);
            }
        }
    }

    private void rollbackFailed(HibernateException e) {
        if (transactionListeners != null) {
            for (TransactionListener listener : transactionListeners) {
                listener.transactionRollbackFailed(this, e);
            }
        }
    }

    private void refreshConnection() {
        Connection conn = JdbcUrlUtils.getConnectionFromJdbcContext(jdbcContext);
        connection = (conn != null && conn instanceof InstrumentedConnection ? (InstrumentedConnection)conn : null);
    }

    @Override
    public boolean wasRolledBack() throws HibernateException {
        return realTransaction.wasRolledBack();
    }

    @Override
    public boolean wasCommitted() throws HibernateException {
        return realTransaction.wasCommitted();
    }

    @Override
    public boolean isActive() throws HibernateException {
        return realTransaction.isActive();
    }

    @Override
    public void registerSynchronization(Synchronization synchronization) throws HibernateException {
        realTransaction.registerSynchronization(synchronization);
    }

    @Override
    public void setTimeout(int seconds) {
        realTransaction.setTimeout(seconds);
    }
}
