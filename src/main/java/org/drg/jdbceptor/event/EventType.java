package org.drg.jdbceptor.event;

import org.drg.jdbceptor.config.DataSourceConfiguration;

/**
 * Interface that must be implemented by any enumeration or class that wants to be used as a return value for the
 * {@link ConnectionEvent#getEventType()} method. </br>
 * An implementation is provided for standard connection events in {@link ConnectionEventType} along with another for
 * Hibernate integration ({@link org.drg.jdbceptor.hibernate.event.HibernateEventType}).
 *
 * @author dgarson
 * @see ConnectionEventType
 * @see org.drg.jdbceptor.hibernate.event.HibernateEventType
 */
public interface EventType {

    /**
     * Returns the display name for this event type based on whether the event is being fired for a connection that is
     * wrapped in a connection pool vs. a standard connection (no-wrapping).
     * @param dataSourceReg the data source registration
     */
    String getName(DataSourceConfiguration dataSourceReg);


}
