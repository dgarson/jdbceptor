package org.drg.jdbceptor.hibernate;

import org.drg.jdbceptor.api.InstrumentedStatement;

import java.sql.Statement;

/**
 * Hibernate extension of the InstrumentedStatement interface which provides support for Hibernate-specific features
 * such as Transaction instrumentation.
 *
 * @author dgarson
 * @see InstrumentedTransaction
 */
public interface InstrumentedHibernateStatement<T extends Statement> extends InstrumentedStatement<T> {

    /**
     * Returns an optional identifier representing the currently active transaction in which this statement is being
     * executed.
     */
    String getTransactionId();
}
