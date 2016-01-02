package org.drg.jdbceptor.hibernate.event;

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
     * @see com.fitbit.jdbceptor.hibernate.AbstractInstrumentedConnectionProvider#createListenerSettings()
     *
     * @param settings listener &quot;settings&quot; and ConnectionProvider reference
     */
    void initialize(ConnectionProviderListenerSettings settings);
}
