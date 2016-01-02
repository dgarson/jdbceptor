package org.drg.jdbceptor.hibernate.event;

import org.drg.jdbceptor.hibernate.MetadataAwareConnectionProvider;

import java.sql.Connection;

/**
 * Listener type that listens only to post-connection acquisition events, which includes both successful acquisitions and failures.
 *
 * @author dgarson
 * @created 6/5/15
 */
public interface PostConnectionAcquisitionListener extends ConnectionProviderListener {

    /**
     * Callback invoked immediately after <strong>successfully</strong> acquiring a connection.
     * @param connectionProvider the connection provider invoking this callback
     * @param connection the acquired connection
     */
    void afterConnectionAcquired(MetadataAwareConnectionProvider connectionProvider, Connection connection);

    /**
     * Callback invoked when an exception occurs (<strong>failure</strong>) during connection acquisition.
     * @param connectionProvider the connection provider invoking this callback
     * @param exc the exception thrown
     */
    void afterConnectionAcquisitionFailed(MetadataAwareConnectionProvider connectionProvider, Throwable exc);
}
