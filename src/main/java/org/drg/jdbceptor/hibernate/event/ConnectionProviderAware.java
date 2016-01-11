package org.drg.jdbceptor.hibernate.event;

import org.drg.jdbceptor.hibernate.InstrumentedConnectionProvider;

/**
 * Awareness interface that indicates that the ConnectionProviderListener needs to have its parent ConnectionProviderWrapper injected into it.
 *
 * @author dgarson
 * @created 6/5/15
 */
public interface ConnectionProviderAware {

    /**
     * Injects the {@link com.fitbit.jdbceptor.hibernate.AbstractInstrumentedConnectionProvider} that has this
     * interceptor in its chain.
     * @param connectionProvider the (parent) connection provider
     */
    void setConnectionProvider(InstrumentedConnectionProvider connectionProvider);
}
