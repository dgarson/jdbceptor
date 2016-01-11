package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;

/**
 * Event object for after a statement is about to be executed, but has not yet started running, through an instrumented
 * connection.
 *
 * @author dgarson
 */
public class StatementExecutingEvent extends ConnectionEvent {

    private final String methodName;

    public StatementExecutingEvent(InstrumentedConnection connection, long timestampNanos,
                                   InstrumentedStatement<?> statement, String methodName) {
        super(connection, ConnectionEventType.STATEMENT_EXECUTING, timestampNanos, connection.isPooled(), statement);
        this.methodName = methodName;
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
}
