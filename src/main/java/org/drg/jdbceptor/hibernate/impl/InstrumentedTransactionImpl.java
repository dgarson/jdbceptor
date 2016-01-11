package org.drg.jdbceptor.hibernate.impl;

import static org.drg.jdbceptor.Jdbceptor.timestampNanos;

import com.google.common.annotations.VisibleForTesting;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.InstrumentedConnectionProvider;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.drg.jdbceptor.hibernate.event.TransactionListener;
import org.drg.jdbceptor.impl.DataSourceManager;
import org.drg.jdbceptor.impl.InstrumentedConnectionImpl;
import org.drg.jdbceptor.impl.UserDataStorageImpl;
import org.drg.jdbceptor.util.JdbcUtils;
import org.hibernate.HibernateException;
import org.hibernate.Transaction;
import org.hibernate.jdbc.JDBCContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.Synchronization;

/**
 * @author dgarson
 */
public class InstrumentedTransactionImpl extends UserDataStorageImpl implements Transaction, InstrumentedTransaction {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedTransactionImpl.class);

    private final InstrumentedConnectionProvider connectionProvider;
    private final Transaction realTransaction;
    private final String transactionId;

    // non-final in case the connection is lazily opened
    private InstrumentedConnection connection;
    // used to acquire the connection instance on lazily opened connections
    private final JDBCContext jdbcContext;

    private List<TransactionListener> transactionListeners;

    long openedTimestampNanos; // timestamp when the begin() method was called
    long closedTimestampNanos;

    // optional user-data attached to this transaction
    private Object userData;

    InstrumentedTransactionImpl(InstrumentedConnectionProvider connectionProvider, JDBCContext jdbcContext,
                                Transaction realTransaction, String transactionId) {
        // this should always be the case otherwise an instance of this class should have never been allowed to be
        //          constructed
        this.connectionProvider = connectionProvider;
        this.jdbcContext = jdbcContext;
        this.realTransaction = realTransaction;
        this.transactionId = transactionId;
    }

    @Override
    public InstrumentedConnectionProvider getConnectionProvider() {
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
    public long getOpenedTimestampNanos() {
        return openedTimestampNanos;
    }

    @VisibleForTesting
    public long getClosedTimestampNanos() {
        return closedTimestampNanos;
    }

    @Override
    public long getDurationNanos() {
        if (openedTimestampNanos <= 0) {
            // return -1 to indicate invalid duration
            return -1L;
        } else if (closedTimestampNanos > 0) {
            // use the close and open timestamp since both are available
            return closedTimestampNanos - openedTimestampNanos;
        } else {
            // use ticker value and compute instantaneous
            return timestampNanos() - openedTimestampNanos;
        }
    }

    @Override
    public String getDataSourceId() {
        return connectionProvider.getDataSourceId();
    }

    @Override
    public DataSourceManager getDataSourceManager() {
        return connectionProvider.getDataSourceManager();
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
            ((InstrumentedConnectionImpl)connection).beganTransaction(this);
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
        Connection conn = JdbcUtils.getConnectionFromJdbcContext(jdbcContext);
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
