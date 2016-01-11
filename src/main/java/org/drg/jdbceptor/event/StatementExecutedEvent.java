package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;

/**
 * Event object for after a statement has been executed through an instrumented connection.
 *
 * @author dgarson
 */
public class StatementExecutedEvent extends ConnectionEvent {

    private final String methodName;
    private final Exception exception;
    private final long executionTimeNanos;

    public StatementExecutedEvent(InstrumentedConnection connection, long timestampNanos, long executionTimeNanos,
                                  InstrumentedStatement<?> statement, Exception exception, String methodName) {
        super(connection, ConnectionEventType.STATEMENT_EXECUTED, timestampNanos, connection.isPooled(), statement);
        this.exception = exception;
        this.methodName = methodName;
        this.executionTimeNanos = executionTimeNanos;
    }

    /**
     * Returns the name of the SQL query execution method, such as &quot;executeQuery&quot; or &quot;executeUpdate&quot;
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Convenience getter to return the event source as an instrumented statement.
     */
    public InstrumentedStatement<?> getStatement() {
        return (InstrumentedStatement<?>)getEventSource();
    }

    /**
     * Checks whether this statement executed successfully or failed.
     */
    public boolean hasError() {
        return exception != null;
    }

    /**
     * Returns the exception that was caught when the statement failed to execute or <code>null</code> if it executed
     * and returned without errors.
     */
    public Exception getException() {
        return exception;
    }

    /**
     * Returns the duration in nanoseconds that this statement executed for.
     */
    public long getElapsedNanos() {
        return executionTimeNanos;
    }
}
