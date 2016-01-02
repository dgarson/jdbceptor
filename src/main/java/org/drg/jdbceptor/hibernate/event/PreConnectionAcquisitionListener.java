package org.drg.jdbceptor.hibernate.event;

import org.drg.jdbceptor.hibernate.MetadataAwareConnectionProvider;

/**
 * Listener type that is invoked immediately prior to acquiring a new connection from the underlying connection provider
 * implementation (e.g. c3p0).
 *
 * @author dgarson
 * @created 6/5/15
 */
public interface PreConnectionAcquisitionListener extends ConnectionProviderListener {

    /**
     * Callback invoked immediately prior to attempting to acquire a connection.
     *
     * @param connectionProvider the connection provider being used to acquire the new connection
     */
    void beforeConnectionAcquisition(MetadataAwareConnectionProvider connectionProvider);
}
