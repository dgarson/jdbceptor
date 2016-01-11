package org.drg.jdbceptor.impl;

import static org.drg.jdbceptor.Jdbceptor.timestampNanos;

import com.google.common.base.Preconditions;
import org.drg.jdbceptor.Jdbceptor;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;
import org.drg.jdbceptor.event.ConnectionClosedEvent;
import org.drg.jdbceptor.event.ConnectionClosedListener;
import org.drg.jdbceptor.event.ConnectionOpenedEvent;
import org.drg.jdbceptor.event.ConnectionOpenedListener;
import org.drg.jdbceptor.event.StatementExecutedEvent;
import org.drg.jdbceptor.event.StatementExecutingEvent;
import org.drg.jdbceptor.event.StatementExecutionListener;
import org.drg.jdbceptor.hibernate.HibernateAwareInstrumentedConnection;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.drg.jdbceptor.hibernate.impl.InstrumentedTransactionImpl;
import org.drg.jdbceptor.internal.MetadataAwareConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;

/**
 * Package-private base superclass that provides implementations for common functionality between the fully instrumented
 * connection implementation in {@link InstrumentedConnectionImpl} as well as the lightweight version.
 *
 * @author dgarson
 */
public class InstrumentedConnectionImpl extends UserDataStorageImpl implements HibernateAwareInstrumentedConnection,
    InstrumentedConnection {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedConnectionImpl.class);

    /**
     * The target connection that we are proxying all actual operations to, whether or not they are being instrumented
     * by this wrapper.
     */
    private final Connection targetConnection;

    /**
     * The Jdbceptor manager for the target data source for this connection.
     */
    protected final DataSourceManager dataSourceManager;

    /**
     * Cache whether or not connections are pooled.
     */
    protected final boolean poolingConnections;

    /**
     * The unique connection identifier. This <i>may</i> be <code>null</code> if the data source is using a connection
     * pool and the connection, although physically established, has not yet been acquired, and logical connection
     * acquisition triggers the generation and injection of a new connection ID.
     */
    private String connectionId;

    /**
     * Optional instance of a connection that is part of a connection pool sitting on top of the Jdbceptor-instrumented
     * connections. In such cases, if a connection resolver is provided, the pooled connections will be injected and
     * cleared from the instrumented connections on logical acquire/release events.
     */
    private Connection pooledConnection;

    /**
     * Keeps track of whether this connection is in fact being instrumented. This can be disabled when using pooled
     * connections when a connection has instrumentation enabled for one acquisition but a future one may have it
     * disabled. Since the instrumented connections are wrapping the <i>physical</i> ones, they must have a way to
     * switch into a mode where as much instrumentation logic in this class as possible is bypassed entirely.
     */
    private boolean instrumented;

    /**
     * Indicates whether full SQL statement capturing should be enabled for statements created thru this connection.
     */
    private boolean captureStatements;

    /**
     * Determines whether more verbose query parameter capturing should be done, which is only ever necessary for
     * Prepared and Callable statements, since variables are substituted into the query prior to executing. If this
     * is <code>false</code> then prepared/callable statements will have question marks instead of parameter values.
     */
    private boolean captureQueryParams;

    /**
     * Next unique statement ID for this connection, or logical connection in the case of this being a pooled physical
     * connection
     */
    private int nextStatementId = 1;

    /**
     * Optional currently active transaction, if using Hibernate with the
     * {@link org.drg.jdbceptor.hibernate.InstrumentedTransactionFactory}.
     */
    private InstrumentedTransactionImpl currentTransaction;

    /**
     * Optional current statement that has begun executing but has not yet completed.
     */
    private InstrumentedStatement<?> currentlyExecutingStatement;

    /**
     * The timestamp (in nanoseconds) when this connection was opened, or acquired in the case of a logical/pooled
     * connection. This value is in nanoseconds since {@link com.google.common.base.Ticker} uses nanoseconds and allows
     * us to mock timings in test cases.
     */
    private long logicalOpenedTimestampNanos;

    /**
     * The timestamp (in nanoseconds) when this connection was last logically closed. In the case of a connection pool,
     * this will be when the connection was released. Otherwise it will be the time when the connection was physically
     * closed.
     */
    private long logicalClosedTimestampNanos;

    /**
     * The timestamp when this connection was physically opened, which will be the same as the logical open timestamp if
     * this connection is not being pooled.
     */
    private long physicalOpenedTimestampNanos;

    /**
     * Optional list of listeners that will be notified whenever this connection is physically or logically closed
     * <strong>NOTE: </strong> listeners registered here rather than with the data source manager will be cleared on any
     *          logical close event
     */
    private List<ConnectionClosedListener> closeListeners;
    private List<ConnectionClosedListener> physicalCloseListeners;

    private List<ConnectionOpenedListener> poolAcquiredListeners;

    /**
     * Optional list of statement listeners that will be notified when this connection begins and finishes executing SQL
     * queries against the database. </br>
     * <strong>NOTE: </strong> listeners registered here rather than with the data source manager will be cleared on any
     *          logical close event
     */
    private List<StatementExecutionListener> statementListeners;

    /**
     * Simply tracks whether we have a connection open, or in the case of a pooled connection, whether there should be a
     * pooled connection checked out for this physical connection.
     */
    private boolean active;

    InstrumentedConnectionImpl(DataSourceManager dataSourceManager, Connection targetConnection) {
        Preconditions.checkNotNull(targetConnection, "targetConnection was not provided");
        this.dataSourceManager = dataSourceManager;
        this.poolingConnections = dataSourceManager.isPoolingConnections();
        this.targetConnection = targetConnection;
    }

    /**
     * The configuration for the data source that this connection is for. This can, among other things, be used to fetch
     * the identifier for that data source.
     */
    @Override
    public DataSourceManager getDataSourceManager() {
        return dataSourceManager;
    }

    @Nonnull
    @Override
    public String getDataSourceId() {
        return dataSourceManager.getId();
    }

    /**
     * Special method designed to be used when the C3P0ProxyConnection is being used such that a method invoked on the
     * raw connection (this object) can return the raw connection since C3P0 does not provide an easy way to do this
     * without hacking around a private field.
     * @return this object
     * @see com.mchange.v2.c3p0.C3P0ProxyConnection#rawConnectionOperation(Method, Object, Object[])
     * @see com.mchange.v2.c3p0.C3P0ProxyConnection#RAW_CONNECTION
     * @see C3P0ConnectionResolver
     */
    @SuppressWarnings("unused")
    public InstrumentedConnection getThis() {
        return this;
    }

    @Override
    public boolean isInstrumented() {
        return instrumented;
    }

    @Override
    public boolean isPooled() {
        return false;
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    void setConnectionId(String connectionId) {
        Preconditions.checkNotNull(connectionId, "null connectionIds are unsupported!");
        this.connectionId = connectionId;
    }

    public boolean isPoolingConnections() {
        return poolingConnections;
    }

    @Override
    public InstrumentedStatement<?> getExecutingStatement() {
        return currentlyExecutingStatement;
    }

    @Override
    public Connection getRealConnection() {
        return targetConnection;
    }

    @Override
    public long getOpenedTimestampNanos() {
        return (poolingConnections ? logicalOpenedTimestampNanos : physicalOpenedTimestampNanos);
    }

    @Override
    public long getCheckoutDurationNanos() {
        if (poolingConnections) {
            if (logicalClosedTimestampNanos > 0) {
                return logicalClosedTimestampNanos - logicalOpenedTimestampNanos;
            } else if (logicalOpenedTimestampNanos > 0) {
                return timestampNanos() - logicalOpenedTimestampNanos;
            } else {
                // undefined case
                return -1L;
            }
        } else if (active) {
            // return elapsed nanoseconds since connection was physically opened
            return timestampNanos() - physicalOpenedTimestampNanos;
        } else {
            // not sure how we'd get here, but return -1 since there's no way to be sure we're checked out...
            return -1L;
        }
    }

    @Override
    public void addCloseListener(ConnectionClosedListener listener) {
        if (closeListeners == null) {
            closeListeners = new CopyOnWriteArrayList<>();
        }
        if (!closeListeners.contains(listener)) {
            closeListeners.add(listener);
        }
    }

    @Override
    public void addStatementListener(StatementExecutionListener listener) {
        if (statementListeners == null) {
            statementListeners = new CopyOnWriteArrayList<>();
        }
        if (!statementListeners.contains(listener)) {
            statementListeners.add(listener);
        }
    }

    @Override
    public void setConnectionInfo(String dataSourceId, String connectionId, boolean instrumented) {
        // this should never be called
        throw new UnsupportedOperationException();
    }

    /**
     * Resets <i>most</i> of the internal state of the instrumented connection and invokes a callback in the subclass to
     * its own clean-up. This method should prepare the connection for &quot;re-use&quot; in terms of being released
     * back to the connection pool.
     */
    private void reset() {
        nextStatementId = 1;
        closeListeners = null;
        statementListeners = null;
        active = false;

        if (pooledConnection != null) {
            setPooledConnection(null);
        }
    }

    /**
     * Resets all internal state for this connection wrapper, resetting any potential mark, clearing the connection ID
     * and clearing any close listeners that are subscribed to this connection.
     * @param pooledConnection optional &quot;top-level&quot; connection, used to support connection pooling wrapping
     *              the JDBCeptor driver
     */
    private void onOpen(Connection pooledConnection) {
        reset();

        boolean isPhysicalConn = false;
        boolean isLogicalEvent = false;
        long timestamp = timestampNanos();
        if (pooledConnection != null) {
            // acquired a connection from the connection pool, wrapping this physical connection
            Preconditions.checkState(physicalOpenedTimestampNanos > 0L, "connection cannot be acquired from pool " +
                "prior to being marked as 'physically open'!");
            logicalOpenedTimestampNanos = timestamp;
            logicalClosedTimestampNanos = 0;
            isLogicalEvent = true;
            active = true;
        } else {
            // established physical connection to the database
            physicalOpenedTimestampNanos = timestamp;

            // if we are not pooling connections, then this is also the logical open event
            if (getDataSourceManager().isPoolingConnections()) {
                active = false;
                logicalOpenedTimestampNanos = 0L;
                logicalClosedTimestampNanos = 0L;
            } else {
                active = true;
                isPhysicalConn = true;
                isLogicalEvent = true;
                logicalOpenedTimestampNanos = physicalOpenedTimestampNanos;
            }
        }

        // avoid this code block if instrumentation will "never" be enabled for this data source for all of runtime
        if (!getDataSourceManager().isInstrumented()) {
            instrumented = false;
            captureQueryParams = false;
            captureStatements = false;
        } else {
            // check again whether we want to in fact instrument this connection, if it is being pooled
            instrumented = getDataSourceManager().getFeatures().shouldInstrumentConnection();

            // if it is a physical connection and instrumentation is at least enabled globally for the data source, then
            // always invoke the physical connection callback to maintain consistency and ensure there are no bookkeeping
            // issues in user code
            ConnectionOpenedEvent event = new ConnectionOpenedEvent(this, timestamp, poolingConnections);
            if (isPhysicalConn) {
                getDataSourceManager().physicalConnectionOpened(event);
            }

            // if we have a pooled connection, or we aren't using pooled connections at all, fire logical open event
            if (instrumented) {
                // inject our pooled connection, even if it is null
                setPooledConnection(pooledConnection);

                // refresh whether we want to capture statements for this connection lease
                captureStatements = getDataSourceManager().getFeatures().shouldCaptureStatements();
                captureQueryParams = captureStatements && Jdbceptor.getSharedConfig().isCaptureQueryParametersEnabled();

                // if we have a pooled connection, or we aren't using pooled connections at all, fire logical open event
                if (isLogicalEvent) {
                    // customize the connection prior to firing the logical open event
                    getDataSourceManager().customizeConnection(this);

                    // now we can fire the event since the connection has been fully configured
                    getDataSourceManager().logicalConnectionOpened(event);
                }
            }
        }
    }

    private void onClose() {
        // keep track of close timestamp regardless of whether instrumentation is enabled
        logicalClosedTimestampNanos = timestampNanos();

        // always invoke if instrumented at all, since the listeners may be expecting to be invoked since it was enabled
        //      when they were registered w/this connection
        boolean isLogicalClose = true;
        ConnectionClosedEvent event = null;
        if (instrumented) {
            if (poolingConnections) {
                // we are wrapped by a connection pool
                if (pooledConnection != null) {
                    // presence of pooled connection means we are closing a pooled connection, so logical event only
                    getDataSourceManager().logicalConnectionClosed(event);
                    logicalClosedTimestampNanos = timestampNanos();
                } else {
                    // if we have no pooledConnection present but we are pooling connections, then it's a physical close
                    getDataSourceManager().physicalConnectionClosed(event);
                    isLogicalClose = false;
                }
            } else {
                // we are not pooling any connections, so it is always a physical event, but we must fire the logical
                // one as well -- this logic is handled in the DataSourceManager class
                getDataSourceManager().logicalConnectionClosed(event);
                logicalClosedTimestampNanos = timestampNanos();

            }
        }

        // invoke close listeners subscribed specifically to this connection, if present
        if (isLogicalClose) {
            if (closeListeners != null) {
                for (ConnectionClosedListener listener : closeListeners) {
                    listener.connectionClosed(event);
                }
            }
        }

        // always make sure to clean-up any monitoring data at end of connection usage
        //  this will reset 'active' to false
        reset();
    }

    void setPooledConnection(Connection pooledConnection) {
        // update with new pooled connection
        this.pooledConnection = pooledConnection;

        if (pooledConnection != null && pooledConnection instanceof MetadataAwareConnection) {
            ((MetadataAwareConnection)pooledConnection).setConnectionInfo(getDataSourceManager().getId(),
                connectionId, true);
        }
    }

    @Override
    public InstrumentedTransaction getCurrentTransaction() {
        return currentTransaction;
    }

    public void setCurrentTransaction(InstrumentedTransaction transaction) {
        // XXX TODO(dgarson) do we fire events here? ideally this would be done in the InstrumentedTransactionImpl...
        currentTransaction = (InstrumentedTransactionImpl) transaction;
    }

    public void beganTransaction(InstrumentedTransaction transaction) {
        Preconditions.checkState(currentTransaction == null, "cannot begin a new transaction when the previous one " +
            "is still active");
        setCurrentTransaction(transaction);
    }

    public void finishedTransaction(InstrumentedTransaction transaction, boolean committed) {
        Preconditions.checkState(transaction == currentTransaction, "cannot finish a transaction that is not the " +
            "same as a present, active transaction for this connection!");
        setCurrentTransaction(null);
    }

    @Override
    public Connection getPooledConnection() {
        return pooledConnection;
    }

    @Override
    public void releasedToConnectionProvider() {
        onClose();
    }

    @Override
    public void acquiredFromConnectionProvider(Connection connection) {
        // reset() and onOpen() whenever we "logically" open the connection, since this may happen multiple times for
        // a physical connection being managed by a connection pool
        onOpen(connection);
    }

    @Override
    public void addPhysicalCloseListener(ConnectionClosedListener closeListener) {
        // if we are not pooling connections, add this to the logical close listener list if not already present
        if (!poolingConnections) {
            log.warn("Attempted to attach a physical connection-closed listener but this connection is not pooled, " +
                "so it will be attached logically instead");
            addCloseListener(closeListener);
            return;
        }
        if (physicalCloseListeners == null) {
            physicalCloseListeners = new CopyOnWriteArrayList<>();
        }
        physicalCloseListeners.add(closeListener);
    }

    @Override
    public void addAcquisitionListener(ConnectionOpenedListener openListener) {
        if (!poolingConnections) {
            throw new IllegalStateException("Unable to attach connection pool acquisition listeners to a connection " +
                "belonging to a data source (" + dataSourceManager.getId() + ") that is not using a connection pool");
        }
        if (poolAcquiredListeners == null) {
            poolAcquiredListeners = new CopyOnWriteArrayList<>();
        }
        poolAcquiredListeners.add(openListener);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        targetConnection.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return targetConnection.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        targetConnection.commit();
    }

    @Override
    public void rollback() throws SQLException {
        targetConnection.rollback();
    }

    @Override
    public void close() throws SQLException {
        try {
            targetConnection.close();
        } finally {
            // if we already have a null 'pooledConnection' then we are currently closing the physical connection
            onClose();
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return targetConnection.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return targetConnection.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        targetConnection.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return targetConnection.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        targetConnection.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return targetConnection.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        targetConnection.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return targetConnection.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return targetConnection.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        targetConnection.clearWarnings();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return targetConnection.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        targetConnection.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        targetConnection.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return targetConnection.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return targetConnection.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return targetConnection.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        targetConnection.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        targetConnection.releaseSavepoint(savepoint);
    }

    /**
     * Invoked whenever a statement is about to be executed thru the driver. The raw SQL may not be available at this point
     * because the driver has not yet intercepted the SQL. </br>
     * Technically it could provide information for prepared calls but this would create inconsistencies with the
     * parameters available in intercepted SQL query execution.
     */
    public void beforeExecutingStatement(StatementExecutingEvent event) {
        currentlyExecutingStatement = event.getStatement();

        if (statementListeners != null) {
            for (StatementExecutionListener listener : statementListeners) {
                listener.beforeExecutingStatement(event);
            }
        }
    }

    /**
     * Invoked whenever a statement finishes being executed.
     */
    public void statementExecuted(StatementExecutedEvent event) {
        currentlyExecutingStatement = null;

        if (statementListeners != null) {
            for (StatementExecutionListener listener : statementListeners) {
                listener.statementExecuted(event);
            }
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        Statement realStatement = targetConnection.createStatement();
        return (captureStatements ? new InstrumentedStatementImpl(this, realStatement, nextStatementId++) :
            realStatement);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        Statement realStatement = targetConnection.createStatement(resultSetType, resultSetConcurrency);
        return (captureStatements ? new InstrumentedStatementImpl(this, realStatement, nextStatementId++) :
            realStatement);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency,
                                     int resultSetHoldability) throws SQLException {
        Statement realStatement = targetConnection.createStatement(resultSetType, resultSetConcurrency,
            resultSetHoldability);
        return (captureStatements ? new InstrumentedStatementImpl(this, realStatement, nextStatementId++) :
            realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql);
        return (captureStatements ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        return (captureStatements ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, resultSetType, resultSetConcurrency,
            resultSetHoldability);
        return (captureStatements ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, autoGeneratedKeys);
        return (captureStatements ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, columnIndexes);
        return (captureStatements ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, columnNames);
        return (captureStatements ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        CallableStatement realStatement = targetConnection.prepareCall(sql);
        return (captureStatements ? new InstrumentedCallableStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        CallableStatement realStatement = targetConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
        return (captureStatements ? new InstrumentedCallableStatement(this, realStatement, nextStatementId++, sql,
            captureQueryParams) : realStatement);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        CallableStatement realStatement = targetConnection.prepareCall(sql, resultSetType, resultSetConcurrency,
            resultSetHoldability);
        return (captureStatements ? new InstrumentedCallableStatement(this, realStatement, nextStatementId++, sql,
            captureStatements) :
            realStatement);
    }

    /**
     * XXX TODO(dgarson) need to add support for 'fake' statements since this does not have a real,
     *      backing java.sql.Statement instance
     */
    @Override
    public String nativeSQL(String sql) throws SQLException {
        /*
        if (!captureStatements) {
            return targetConnection.nativeSQL(sql);
        }
        long startTimeMillis = System.currentTimeMillis();

        executingStatement(null, "nativeSQL", sql);
        try {
            String result = targetConnection.nativeSQL(sql);
            statementExecuted(null, "nativeSQL", sql, System.currentTimeMillis() - startTimeMillis, null);
            return result;
        } catch (SQLException | RuntimeException e) {
            statementExecuted(null, "nativeSQL", sql, System.currentTimeMillis() - startTimeMillis, e);
            throw e;
        }
        */
        return targetConnection.nativeSQL(sql);
    }

    @Override
    public Clob createClob() throws SQLException {
        return targetConnection.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return targetConnection.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return targetConnection.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return targetConnection.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return targetConnection.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        targetConnection.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        targetConnection.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return targetConnection.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return targetConnection.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return targetConnection.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return targetConnection.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        targetConnection.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return targetConnection.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        targetConnection.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        targetConnection.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return targetConnection.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return ((iface != null && iface == Connection.class) ? (T)this : targetConnection.unwrap(iface));
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return (iface != null && iface == Connection.class) || targetConnection.isWrapperFor(iface);
    }
}
