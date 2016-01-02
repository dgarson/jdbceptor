package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedStatement;

/**
 * Listener interface where implementations can be attached to an instance of {@link org.drg.jdbceptor..}
 * and invoked before or after execution of that statement. StatementExecutionListeners do <strong>NOT</strong>
 * support batch statement execution callbacks.</br>
 * <strong>NOTE:</strong> statement execution listeners that are explicitly attached to a statement will be invoked
 * anyway, even if the overall feature is disabled.
 *
 * @author dgarson
 */
public interface StatementExecutionListener {

    /**
     * Invoked whenever a statement is about to be executed thru the driver. The raw SQL is not available at this point
     * because the driver has not yet intercepted the SQL. </br>
     * Technically it could provide information for prepared calls but this would create inconsistencies with the
     * parameters available in intercepted SQL query execution.
     * @param statement the statement that is being executed
     * @param sql the SQL statement that is about to be executed
     */
    void beforeExecutingStatement(InstrumentedStatement<?> statement, String sql);

    /**
     * Invoked after the statement finishes being executed.
     * @param statement the executed statement
     * @param sql the SQL query that was executed
     * @param executionTimeMillis the number of milliseconds it took to execute the statement
     * @param exception exception that was thrown during statement execution; this will only be non-null if the
     *                  statement failed
     */
    void statementExecuted(InstrumentedStatement<?> statement, String sql, long executionTimeMillis,
                           Exception exception);
}
