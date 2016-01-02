package org.drg.jdbceptor.api;

import org.drg.jdbceptor.event.StatementExecutionListener;

import java.sql.Statement;

/**
 * Parent interface implemented by all instrumented delegate classes for SQL Statements:
 * <ul>
 *     <li>InstrumentedPreparedStatement</li>
 *     <li>InstrumentedCallableStatement</li>
 *     <li>InstrumentedStatementImpl</li>
 * </ul>
 *
 * @author dgarson
 */
public interface InstrumentedStatement<T extends Statement> extends Statement, UserDataStorage {

    /**
     * Returns a sequence number assigned to this statement indicative of its order being executed against a particular
     * connection, and guaranteed uniqueness within that connection's statements.
     * @return the statement id
     */
    int getSeqNo();

    /**
     * Checks whether this statement is still being executed.
     * @return true if still executing, false if finished
     */
    boolean isRunning();

    /**
     * Returns the amount of time that this statement was (or has been) executing for.
     */
    long getDurationMillis();

    /**
     * Returns the connection instance that created this statement.
     */
    InstrumentedConnection getInstrumentedConnection();

    /**
     * Returns the wrapped statement.
     */
    T getWrappedStatement();

    /**
     * Adds a listener to this statement which will be invoked immediately prior to execution and immediately after the
     * query is done executing.
     */
    void addExecutionListener(StatementExecutionListener executionListener);

    /**
     * Returns an optional identifier representing the currently active transaction in which this statement is being
     * executed.
     */
    String getTransactionId();

    /**
     * Returns the SQL query executed for this statement. If this statement is a prepared or callable statement, then
     * the returned query may or may not include question marks for parameterized queries, depending on whether query
     * parameter capturing is enabled thru {@link InstrumentationHandler#isCaptureQueryParametersEnabled()}.
     */
    String getSqlStatement();
}