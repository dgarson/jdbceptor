package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedStatement;

/**
 * Listener interface where implementations can be attached to an instance of {@link InstrumentedStatement}
 * and invoked before or after execution of that statement. StatementExecutionListeners do <strong>NOT</strong>
 * support batch statement execution callbacks.</br>
 * <strong>NOTE:</strong> statement execution listeners that are explicitly attached to a statement will be invoked
 * anyway, even if the overall feature is disabled.
 *
 * @author dgarson
 */
public interface StatementExecutionListener {

    /**
     * Invoked whenever a statement is about to be executed thru the driver. The raw SQL is not always available at this
     * point because the driver has not yet intercepted the SQL. </br>
     * Technically it could provide information for prepared calls but this would create inconsistencies with the
     * parameters available in intercepted SQL query execution.
     */
    void beforeExecutingStatement(StatementExecutingEvent event);

    /**
     * Invoked after a statement finishes being executed. Note that the <strong>sql</strong> argument will not be
     * provided if the statement is a batch statement, to prevent large string concatenation. If desired, the SQL can
     * be extracted through the {@link InstrumentedStatement#getSqlStatement()} method.
     */
    void statementExecuted(StatementExecutedEvent event);
}
