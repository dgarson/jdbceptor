package org.drg.jdbceptor.impl;

import static org.drg.jdbceptor.Jdbceptor.timestampNanos;

import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.Jdbceptor;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;
import org.drg.jdbceptor.event.StatementExecutedEvent;
import org.drg.jdbceptor.event.StatementExecutingEvent;
import org.drg.jdbceptor.event.StatementExecutionListener;
import org.drg.jdbceptor.hibernate.InstrumentedHibernateStatement;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private abstract base class for implementations of {@link InstrumentedStatement}.
 *
 * @author dgarson
 */
abstract class AbstractInstrumentedStatement<T extends Statement> extends UserDataStorageImpl
    implements InstrumentedHibernateStatement<T>, Statement {

    protected final T statement;
    protected final InstrumentedConnection connection;
    protected final String transactionId;
    protected final int statementId;

    private boolean running;

    // timestamp when statement began executing SQL against the database
    private long startTimeNanos;
    private long completionTimeNanos;

    // optional batch statement list to track SQL queries in the batch if we are tracking statements and using batching
    protected List<String> batchStatementList;

    // optional list of execution listeners
    private List<StatementExecutionListener> executionListeners;

    // optional SQL statement that is cached whenever an execute(String, ..) method is called so that it can be
    // captured when committing/rolling back a transaction
    private String sql;

    protected AbstractInstrumentedStatement(InstrumentedConnection connection, T statement, int statementId) {
        this.statement = statement;
        this.transactionId = StatementHelper.getTransactionIdOrNull(connection);
        this.connection = connection;
        this.statementId = statementId;
    }

    @Override
    public String getSqlStatement() {
        if (sql == null) {
            // concatenate batch of statements if present
            if (batchStatementList != null) {
                // append terminating character and new line after each batch statement
                sql = StringUtils.join(batchStatementList, ";\n");
            } else {
                // otherwise ask subclass for formatted SQL string
                sql = getFormattedSql();
            }
        }
        return sql;
    }

    /**
     * Returns the fully expanded SQL query that was or is executing in this statement. If SQL statement capturing is
     * disabled then this method may return <code>null</code>.
     * @return the formatted SQL query or <code>null</code> if unavailable
     */
    protected abstract String getFormattedSql();

    /**
     * Returns the value that is currently cached from the return value of {@link #getFormattedSql()} which is
     * lazily fetched in the first {@link #getSqlStatement()} call.
     * @return the cached SQL query or <code>null</code> if it has not yet been formatted
     */
    protected String getCachedSql() {
        return sql;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public int getSeqNo() {
        return statementId;
    }

    @Override
    public T getWrappedStatement() {
        return statement;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return statement.getConnection();
    }

    @Override
    public InstrumentedConnection getInstrumentedConnection() {
        return connection;
    }

    @Override
    public long getDurationMillis() {
        return (completionTimeNanos > 0 ? completionTimeNanos - startTimeNanos :
            System.currentTimeMillis() - startTimeNanos);
    }

    @Override
    public void addExecutionListener(StatementExecutionListener listener) {
        if (executionListeners == null) {
            executionListeners = new ArrayList<>();
        }
        executionListeners.add(listener);
    }

    /**
     * Reports
     * @param methodName
     * @param sql
     */
    protected void reportBeginExecution(String methodName, String sql) {
        // capture SQL statement as execution begins
        this.sql = sql;

        // mark as running, invoke callbacks
        running = true;
        startTimeNanos = timestampNanos();

        StatementExecutingEvent event = new StatementExecutingEvent(connection, Jdbceptor.timestampNanos(),
            this, methodName);
        ((InstrumentedConnectionImpl)connection).beforeExecutingStatement(event);

        if (executionListeners != null) {
            for (StatementExecutionListener listener : executionListeners) {
                listener.beforeExecutingStatement(event);
            }
        }
    }

    protected void reportStatementCompletion(String methodName, String sql, Exception exception) {
        running = false;
        completionTimeNanos = timestampNanos();
        long executionTimeNanos = completionTimeNanos - startTimeNanos;
        StatementExecutedEvent event = new StatementExecutedEvent(connection, completionTimeNanos,
            executionTimeNanos, this, exception, methodName);
        ((InstrumentedConnectionImpl)connection).statementExecuted(event);

        if (executionListeners != null) {
            for (StatementExecutionListener listener : executionListeners) {
                listener.statementExecuted(event);
            }
        }
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return statement.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return statement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        reportBeginExecution("executeUpdate", sql);
        try {
            int result = statement.executeUpdate(sql, autoGeneratedKeys);
            reportStatementCompletion("executeUpdate", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeUpdate", sql, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        reportBeginExecution("executeUpdate", sql);
        try {
            int result = statement.executeUpdate(sql, columnIndexes);
            reportStatementCompletion("executeUpdate", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeUpdate", sql, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        reportBeginExecution("executeUpdate", sql);
        try {
            int result = statement.executeUpdate(sql, columnNames);
            reportStatementCompletion("executeUpdate", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeUpdate", sql, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        reportBeginExecution("executeUpdate", sql);
        try {
            int result = statement.executeUpdate(sql);
            reportStatementCompletion("executeUpdate", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeUpdate", sql, e);
            throw e;
        }
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        reportBeginExecution("execute", sql);
        try {
            boolean result = statement.execute(sql, autoGeneratedKeys);
            reportStatementCompletion("execute", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("execute", sql, e);
            throw e;
        }
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        reportBeginExecution("execute", sql);
        try {
            boolean result = statement.execute(sql, columnIndexes);
            reportStatementCompletion("execute", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("execute", sql, e);
            throw e;
        }
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        reportBeginExecution("execute", sql);
        try {
            boolean result = statement.execute(sql, columnNames);
            reportStatementCompletion("execute", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("execute", sql, e);
            throw e;
        }
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        reportBeginExecution("execute", sql);
        try {
            boolean result = statement.execute(sql);
            reportStatementCompletion("execute", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("execute", sql, e);
            throw e;
        }
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        reportBeginExecution("executeQuery", sql);
        try {
            ResultSet results = statement.executeQuery(sql);
            reportStatementCompletion("executeQuery", sql, /*exception=*/null);
            return results;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeQuery", sql, e);
            throw e;
        }
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        // add statement to underlying Statement batch list first in case that fails
        statement.addBatch(sql);

        if (batchStatementList == null) {
            batchStatementList = new ArrayList<>();
        }
        batchStatementList.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        statement.clearBatch();
        batchStatementList = null;
    }

    @Override
    public int[] executeBatch() throws SQLException {
        String[] sqlStatements = (batchStatementList == null ? null :
            batchStatementList.toArray(new String[batchStatementList.size()]));
        if (sqlStatements != null) {
            reportBeginExecution("executeBatch", /*sql=*/null);
        }
        try {
            int[] results = statement.executeBatch();
            reportStatementCompletion("executeBatch",  /*sql=*/null, /*exception=*/null);
            return results;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeBatch",  /*sql=*/null, e);
            throw e;
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return statement.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return statement.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        statement.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return statement.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        statement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return statement.isCloseOnCompletion();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface == PreparedStatement.class || iface == Statement.class || iface == CallableStatement.class) {
            return (T)this;
        } else {
            return statement.unwrap(iface);
        }
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface == PreparedStatement.class || iface == Statement.class) {
            return true;
        } else {
            return statement.isWrapperFor(iface);
        }
    }

    @Override
    public void close() throws SQLException {
        // clear reference to facilitate garbage collection
        batchStatementList = null;
        executionListeners = null;
        statement.close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return statement.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        statement.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return statement.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        statement.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        statement.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return statement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        statement.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        statement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return statement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        statement.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        statement.setCursorName(name);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return statement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return statement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return statement.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        statement.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return statement.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        statement.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return statement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return statement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return statement.getResultSetType();
    }
}
