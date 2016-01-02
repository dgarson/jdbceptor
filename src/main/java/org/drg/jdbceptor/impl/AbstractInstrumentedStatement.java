package org.drg.jdbceptor.impl;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;
import org.drg.jdbceptor.event.StatementExecutionListener;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-private abstract base class for implementations of {@link InstrumentedStatement}.
 *
 * @author dgarson
 */
abstract class AbstractInstrumentedStatement<T extends Statement>
    implements InstrumentedStatement<T>, Statement {

    protected final T statement;
    protected final InstrumentedConnectionImpl connection;
    protected final String transactionId;
    protected final int statementId;

    private boolean running;

    // timestamp when statement began executing SQL against the database
    private long startTimeMillis;
    private long completionTimeMillis;

    // optional batch statement list to track SQL queries in the batch if we are tracking statements and using batching
    protected List<String> batchStatementList;

    // optional list of execution listeners
    private List<StatementExecutionListener> executionListeners;

    // optional SQL statement that is cached whenever an execute(String, ..) method is called so that it can be
    // captured when committing/rolling back a transaction
    private String sql;

    // optional user data that will be created upon first use
    private Map<String, Object> userDataMap;

    protected AbstractInstrumentedStatement(InstrumentedConnectionImpl connection, T statement, int statementId) {
        this.statement = statement;
        this.transactionId = connection.getTransactionId();
        this.connection = connection;
        this.statementId = statementId;
    }

    @Override
    public String getSqlStatement() {
        if (sql == null) {
            sql = getFormattedSql();
        }
        return sql;
    }

    protected abstract String getFormattedSql();

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
    public Object getUserData(String key) {
        return (userDataMap != null ? userDataMap.get(key) : null);
    }

    @Override
    public void setUserData(String key, Object value) {
        if (userDataMap == null) {
            userDataMap = new HashMap<>();
        }
        userDataMap.put(key, value);
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
        return (completionTimeMillis > 0 ? completionTimeMillis - startTimeMillis :
            System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public void addExecutionListener(StatementExecutionListener listener) {
        if (executionListeners == null) {
            executionListeners = new ArrayList<>();
        }
        executionListeners.add(listener);
    }

    protected void reportBeginExecution(String methodName, String sql) {
        // capture SQL statement as execution begins
        this.sql = sql;

        // mark as running, invoke callbacks
        running = true;
        startTimeMillis = System.currentTimeMillis();

        connection.executingStatement(this, methodName, sql);

        if (executionListeners != null) {
            for (StatementExecutionListener listener : executionListeners) {
                listener.beforeExecutingStatement(this, sql);
            }
        }
    }

    protected void reportStatementCompletion(String methodName, String sql, Exception exception) {
        running = false;
        completionTimeMillis = System.currentTimeMillis();
        long executionTimeMillis = completionTimeMillis - startTimeMillis;
        connection.statementExecuted(this, methodName, sql, executionTimeMillis, exception);

        if (executionListeners != null) {
            for (StatementExecutionListener listener : executionListeners) {
                listener.statementExecuted(this, sql, executionTimeMillis, exception);
            }
        }
    }

    protected void reportBeginBatchExecution(String methodName, String sqlStatements[]) {
        running = true;
        startTimeMillis = System.currentTimeMillis();
        connection.executingBatchStatements(this, methodName, sqlStatements);
    }

    protected void reportBatchStatementCompletion(String methodName, String[] sqlStatements, Exception exception) {
        running = false;
        completionTimeMillis = System.currentTimeMillis();
        long executionTimeMillis = completionTimeMillis - startTimeMillis;
        connection.batchStatementsExecuted(this, methodName, sqlStatements, executionTimeMillis, exception);
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
            reportBeginBatchExecution("executeBatch", sqlStatements);
        }
        try {
            int[] results = statement.executeBatch();
            reportBatchStatementCompletion("executeBatch", sqlStatements, /*exception=*/null);
            return results;
        } catch (SQLException | RuntimeException e) {
            reportBatchStatementCompletion("executeBatch", sqlStatements, e);
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
