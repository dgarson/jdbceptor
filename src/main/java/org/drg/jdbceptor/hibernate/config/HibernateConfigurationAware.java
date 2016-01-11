package org.drg.jdbceptor.hibernate.config;

import org.drg.jdbceptor.hibernate.InstrumentedHibernateConfiguration;

import javax.annotation.Nonnull;

/**
 * Interface that can be implemented by either a {@link org.hibernate.transaction.TransactionFactory} or an instance of
 * {@link org.drg.jdbceptor.hibernate.InstrumentedConnectionProvider} so that it receives an injection of the owning
 * Configuration instance.
 */
public interface HibernateConfigurationAware {

    /**
     * Injects the Hibernate configuration object into this component.
     */
    void setHibernateConfiguration(@Nonnull InstrumentedHibernateConfiguration config);
}
