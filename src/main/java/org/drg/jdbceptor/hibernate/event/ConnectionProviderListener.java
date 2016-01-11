package org.drg.jdbceptor.hibernate.event;

import java.util.Properties;

/**
 * Interface abstraction around an interceptor that should be invoked immediately after acquiring new connections and
 * immediately before releasing used connections.
 *
 * @author dgarson
 * @created 6/5/15
 */
public interface ConnectionProviderListener {

    /**
     * Initializes this interceptor given information about the ConnectionProvider and Hibernate configuration.
     *
     * @param settings listener &quot;settings&quot; and ConnectionProvider reference
     * @throws Exception on any exception initializing this listener
     * @see org.drg.jdbceptor.hibernate.InstrumentedConnectionProvider#createListenerSettings(Properties)
     */
    void initialize(ConnectionProviderListenerSettings settings) throws Exception;
}
