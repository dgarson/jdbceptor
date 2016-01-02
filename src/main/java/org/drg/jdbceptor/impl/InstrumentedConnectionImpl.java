package org.drg.jdbceptor.impl;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;
import org.drg.jdbceptor.event.ConnectionClosedListener;
import org.drg.jdbceptor.hibernate.HibernateAwareInstrumentedConnection;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.drg.jdbceptor.hibernate.InstrumentedTransactionImpl;
import org.drg.jdbceptor.spi.MetadataAwareConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper around {@link Connection} that provides JDBC instrumentation support and allows subscribing to close
 * events. It also supports logical open/close vs. physical open/close in the cases where a Connection Pool is used.
 *
 * @author dgarson
 */
public class InstrumentedConnectionImpl implements HibernateAwareInstrumentedConnection, Connection {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedConnectionImpl.class);

    /**
     * The target connection that we are proxying all actual operations to, whether or not they are being instrumented
     * by this wrapper.
     */
    private final Connection targetConnection;

    /**
     * The unique connection identifier. This may be <code>null</code> if connection identifiers are not supported by
     * the {@link InstrumentationHandler}
     */
    private String connectionId;

    /**
     * Optional explicitly injected database &quot;dataSourceId&quot; associated with this connection. Usually this is tied to
     * the database that is being connected to.
     */
    private String dataSourceId;

    /**
     * Next unique statement ID for this connection, or logical connection in the case of this being a pooled physical
     * connection
     */
    private int nextStatementId = 1;

    /**
     * Optional list of listeners that will be notified whenever this connection is physically or logically closed
     */
    private List<ConnectionClosedListener> closeListeners;

    /**
     * Timestamp when this connection was opened, either logically or physically
     */
    private long openedTimestamp;

    private long acquiredTimestamp;

    // the instrumentation handler instance
    private final InstrumentationHandler handler;
    private final HibernateAwareInstrumentationHandler hibernateAwareHandler;
    private boolean passthrough;

    private boolean captureStatements;

    private final AtomicBoolean marked = new AtomicBoolean();

    /**
     * Optional currently active transaction, if using Hibernate with the
     * {@link org.drg.jdbceptor.hibernate.InstrumentedTransactionFactory}.
     */
    private InstrumentedTransactionImpl currentTransaction;

    /**
     * The currently executing Statement that was created by this connection object and has not yet completed
     * executing against the database.
     */
    private InstrumentedStatement<?> currentlyExecutingStatement;

    /**
     * The optional connection in the pool that is wrapping this underlying, instrumented connection.
     */
    private Connection pooledConnection;

    public InstrumentedConnectionImpl(InstrumentationHandler handler, String dataSourceId, Connection targetConnection) {
        this.passthrough = (handler == null || !handler.shouldInstrumentConnection());
        this.targetConnection = targetConnection;
        this.handler = handler;
        this.hibernateAwareHandler = (handler instanceof HibernateAwareInstrumentationHandler ?
            (HibernateAwareInstrumentationHandler)handler : null);
        // may be null
        this.dataSourceId = dataSourceId;
        onOpen(/*pooledConnection=*/null);
    }

    @Override
    public Connection getPooledConnection() {
        return pooledConnection;
    }

    /**
     * Returns the singleton instrumentation handler instance.
     */
    InstrumentationHandler getInstrumentationHandler() {
        return handler;
    }

    /**
     * Resets all internal state for this connection wrapper, resetting any potential mark, clearing the connection ID
     * and clearing any close listeners that are subscribed to this connection.
     * @param pooledConnection optional &quot;top-level&quot; connection, used to support connection pooling wrapping
     *              the JDBCeptor driver
     */
    private void onOpen(Connection pooledConnection) {
        reset();
        // capture acquisition timestamp
        if (pooledConnection != null) {
            acquiredTimestamp = System.currentTimeMillis();
        } else {
            openedTimestamp = System.currentTimeMillis();
            acquiredTimestamp = 0;
        }

        // update passthrough mode and cache whether we will be capturing statements thru this connection
        passthrough = !handler.shouldInstrumentConnection();

        boolean isPhysicalConn = handler.isPooledConnections() && (pooledConnection == null);
        // if it is a physical connection and instrumentation is at least enabled, then always invoke the physical
        //      connection callback
        if (isPhysicalConn && handler.isEnabled() && markIfUnmarked()) {
            handler.physicalConnectionOpened(this);
        }

        // if we have a pooled connection, or we aren't using pooled connections at all, fire logical open event
        boolean isLogicalEvent = passthrough || pooledConnection != null || !handler.isPooledConnections();
        if (passthrough) {
            captureStatements = false;
        } else {
            // enabled
            setPooledConnection(pooledConnection);
            captureStatements = handler.isCaptureStatementsEnabled();

            // if we have a pooled connection, or we aren't using pooled connections at all, fire logical open event
            if (isLogicalEvent) {
                handler.logicalConnectionOpened(this);

                // this must be invoked after calling the logical/physicalConnectionOpened(..) callbacks otherwise we will not
                // yet have had the chance to inject a dataSourceConnections
                // dataSourceConnection = handler.createDataSourceConnection(this);
                Preconditions.checkNotNull(dataSourceConnection);
            }
        }
    }

    void setPooledConnection(Connection pooledConnection) {
        // update with new pooled connection
        this.pooledConnection = pooledConnection;

        if (pooledConnection != null && pooledConnection instanceof MetadataAwareConnection) {
            ((MetadataAwareConnection)pooledConnection).setConnectionInfo(getDataSourceId(), connectionId, true);
        }
    }

    @Override
    public boolean isInstrumented() {
        return !passthrough;
    }

    @Override
    public void setConnectionInfo(String databaseName, String connectionId, boolean instrumented) {
        throw new UnsupportedOperationException();
    }

    private void reset() {
        nextStatementId = 1;
        marked.set(false);
        closeListeners = null;

        if (pooledConnection != null) {
            setPooledConnection(null);
        }
    }

    @Override
    public boolean isPassthrough() {
        return passthrough;
    }

    @Override
    public boolean markIfUnmarked() {
        return marked.compareAndSet(false, true);
    }

    @Override
    public boolean isMarked() {
        return marked.get();
    }

    @Override
    public String getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public InstrumentedStatement<?> getExecutingStatement() {
        return currentlyExecutingStatement;
    }

    /**
     * Special method designed to be used when the C3P0ProxyConnection is being used such that a method invoked on the
     * raw connection (this object) can return the raw connection since C3P0 does not provide an easy way to do this
     * without hacking around a private field.
     * @return this object
     */
    @SuppressWarnings("unused")
    @Override
    public InstrumentedConnection getThis() {
        return this;
    }

    @Override
    public InstrumentedTransaction getCurrentTransaction() {
        return currentTransaction;
    }

    public String getTransactionId() {
        return (currentTransaction != null ? currentTransaction.getTransactionId() : null);
    }

    @Override
    public void setCurrentTransaction(InstrumentedTransaction transaction) {
        currentTransaction = (InstrumentedTransactionImpl)transaction;
    }

    public void beginningTransaction(InstrumentedTransactionImpl transaction) {
        if (currentTransaction != null) {
            throw new IllegalStateException("Unable to begin a new transaction when the old one has not yet " +
                "completed: " + currentTransaction);
        }
        currentTransaction = transaction;
    }

    public void finishedTransaction(InstrumentedTransactionImpl transaction, boolean committed) {
        if (currentTransaction != transaction) {
            throw new IllegalStateException("Attempted to finish a transaction that is not the same as the one " +
                "currently associated with this connection: " + transaction);
        }
        currentTransaction = null;
    }

    boolean shouldCaptureStatements() {
        return !passthrough && captureStatements;
    }

    // this method will never be invoked if statement instrumentation is disabled
    public void executingStatement(AbstractInstrumentedStatement<? extends Statement> statement, String methodName, String sql) {
        currentlyExecutingStatement = statement;
        handler.beforeExecutingStatement(this, statement, methodName, sql);
    }

    // this method will never be invoked if statement instrumentation is disabled
    public void executingBatchStatements(AbstractInstrumentedStatement<? extends Statement> statement, String methodName,
                                         String[] sqlStatements) {
        currentlyExecutingStatement = statement;
        handler.beforeExecutingBatchStatements(this, statement, methodName, sqlStatements);
    }

    // this method will never be invoked if statement instrumentation is disabled
    public void statementExecuted(AbstractInstrumentedStatement<? extends Statement> statement, String methodName, String sql,
                                  long executionTimeMillis, Exception exception) {
        handler.statementExecuted(this, statement, methodName, sql, executionTimeMillis, exception);
        currentlyExecutingStatement = null;
    }

    // this method will never be invoked if statement instrumentation is disabled
    public void batchStatementsExecuted(AbstractInstrumentedStatement<? extends Statement> statement, String methodName,
                                        String[] sqlStatements, long executionTimeMillis, Exception exception) {
        handler.batchStatementsExecuted(this, statement, methodName, sqlStatements, executionTimeMillis, exception);
        currentlyExecutingStatement = null;
    }

    @Override
    public Connection getRealConnection() {
        return targetConnection;
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    @Override
    public long getOpenedTimestamp() {
        return openedTimestamp;
    }

    @Override
    public long getAcquisitionTimestamp() {
        return acquiredTimestamp;
    }

    @Override
    public void addCloseListener(ConnectionClosedListener listener) {
        if (closeListeners == null) {
            closeListeners = new ArrayList<>();
        }
        closeListeners.add(listener);
    }

    private void onClose() {
        // always invoke if not in passthrough, since the listeners may be expecting to be invoked since it was enabled
        //      when they were registered w/this connection
        if (isMarked() && handler.isEnabled() && (pooledConnection == null || !handler.isPooledConnections())) {
            // invoke physicalConnectionClosed(..) callback always
            handler.physicalConnectionClosed(this);
        }
        if (!passthrough) {
            // invoke close callback on the handler
            if (pooledConnection != null || !handler.isPooledConnections()) {
                handler.logicalConnectionClosed(this);
            }

            // invoke close listeners if subscribed
            if (closeListeners != null) {
                for (ConnectionClosedListener listener : closeListeners) {
                    listener.connectionClosed(this);
                }
            }
        }

        // mark closed for book-keeping if enabled
        if (dataSourceConnection != null) {
            dataSourceConnection.getDataSource().connectionClosed();
            dataSourceConnection = null;
        }

        // always make sure to clean-up any monitoring data at end of connection usage
        reset();
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
    public Statement createStatement() throws SQLException {
        Statement realStatement = targetConnection.createStatement();
        return (shouldCaptureStatements() ? new InstrumentedStatementImpl(this, realStatement, nextStatementId++) :
            realStatement);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        Statement realStatement = targetConnection.createStatement(resultSetType, resultSetConcurrency);
        return (shouldCaptureStatements() ? new InstrumentedStatementImpl(this, realStatement, nextStatementId++) :
            realStatement);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency,
                                     int resultSetHoldability) throws SQLException {
        Statement realStatement = targetConnection.createStatement(resultSetType, resultSetConcurrency,
            resultSetHoldability);
        return (shouldCaptureStatements() ? new InstrumentedStatementImpl(this, realStatement, nextStatementId++) :
            realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql);
        return (shouldCaptureStatements() ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        return (shouldCaptureStatements() ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, resultSetType, resultSetConcurrency,
            resultSetHoldability);
        return (shouldCaptureStatements() ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, autoGeneratedKeys);
        return (shouldCaptureStatements() ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, columnIndexes);
        return (shouldCaptureStatements() ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        PreparedStatement realStatement = targetConnection.prepareStatement(sql, columnNames);
        return (shouldCaptureStatements() ? new InstrumentedPreparedStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        CallableStatement realStatement = targetConnection.prepareCall(sql);
        return (shouldCaptureStatements() ? new InstrumentedCallableStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        CallableStatement realStatement = targetConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
        return (shouldCaptureStatements() ? new InstrumentedCallableStatement(this, realStatement, nextStatementId++, sql,
            handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        CallableStatement realStatement = targetConnection.prepareCall(sql, resultSetType, resultSetConcurrency,
            resultSetHoldability);
        return (shouldCaptureStatements() ? new InstrumentedCallableStatement(this, realStatement, nextStatementId++,
            sql, handler.isCaptureQueryParametersEnabled()) : realStatement);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        if (!shouldCaptureStatements()) {
            return targetConnection.nativeSQL(sql);
        }
        long startTimeMillis = System.currentTimeMillis();
        executingStatement(/*statement=*/null, "nativeSQL", sql);
        try {
            String result = targetConnection.nativeSQL(sql);
            statementExecuted(/*statement=*/null, "nativeSQL", sql, System.currentTimeMillis() - startTimeMillis, null);
            return result;
        } catch (SQLException | RuntimeException e) {
            statementExecuted(/*statement=*/null, "nativeSQL", sql, System.currentTimeMillis() - startTimeMillis, e);
            throw e;
        }
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
        return targetConnection.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return targetConnection.isWrapperFor(iface);
    }
}