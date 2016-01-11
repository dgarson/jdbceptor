package org.drg.jdbceptor.event;

import org.drg.jdbceptor.api.InstrumentedConnection;

/**
 * Listener whose callback is invoked whenever a connection is opened. In the case of pooled connections, instances of
 * this listener can be attached to Hibernate-integration connections using the <code>addAcquisitionListener</code>
 * method in the {@link org.drg.jdbceptor.hibernate.HibernateAwareInstrumentedConnection}.
 *
 * @author dgarson
 * @see org.drg.jdbceptor.hibernate.HibernateAwareInstrumentedConnection#addAcquisitionListener
 * (ConnectionOpenedListener)
 */
public interface ConnectionOpenedListener {

    /**
     * Invoked whenever the parent connection is closed.
     * @param event an event object containing information about the connection that was just opened/acquired
     */
    void connectionOpened(ConnectionOpenedEvent event);
}
