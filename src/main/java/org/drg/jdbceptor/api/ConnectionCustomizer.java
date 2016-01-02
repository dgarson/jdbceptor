package org.drg.jdbceptor.api;

/**
 * Customizer interface for the {@link InstrumentedConnection} that can be returned from the instrumentation handler
 * and have it invoked immediately after certain connection operations are performed thru the Jdbceptor driver.
 *
 * @author dgarson
 */
public interface ConnectionCustomizer {

    /**
     * Customizes a <strong>connection</strong> that has just been opened. This may involve modifying the user data
     * elements on a given connection or doing some book-keeping.
     */
    void customizeConnection(InstrumentedConnection connection);
}
