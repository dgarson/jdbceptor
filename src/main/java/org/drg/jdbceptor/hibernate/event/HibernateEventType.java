package org.drg.jdbceptor.hibernate.event;

import org.drg.jdbceptor.config.DataSourceConfiguration;
import org.drg.jdbceptor.event.EventType;

/**
 * Specialized event type implementation for Hibernate-specific Jdbceptor events. All other events used with Jdbceptor
 * are provided by {@link org.drg.jdbceptor.event.ConnectionEventType}.
 *
 * @author dgarson
 */
public enum HibernateEventType implements EventType {
    TRANSACTION_BEGAN("Began Transaction"),
    TRANSACTION_COMMITTING("Committing Transaction"),
    TRANSACTION_ROLLING_BACK("Rolling Back Transaction"),
    TRANSACTION_COMMITTED("Committed Transaction"),
    TRANSACTION_ROLLED_BACK("Rolled Back Transaction"),
    ;

    private final String name;

    HibernateEventType(String name) {
        this.name = name;
    }

    @Override
    public String getName(DataSourceConfiguration dataSourceReg) {
        return name;
    }
}
