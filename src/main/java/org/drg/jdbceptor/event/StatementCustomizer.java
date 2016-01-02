package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.api.InstrumentedStatement;

/**
 * Customizer that can be provided such that instrumented statements can be handled specially prior to being used
 * for JDBC operations, etc.
 */
public interface StatementCustomizer {

    /**
     * Customizes a statement that has just been created by a given connection.
     */
    void customizeStatement(InstrumentedConnection connection, InstrumentedStatement<?> statement);
}
