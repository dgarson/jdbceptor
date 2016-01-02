package org.drg.jdbceptor.hibernate;

import org.drg.jdbceptor.hibernate.event.TransactionListener;

/**
 * Pluggable interface that provides a way to customize new instances of {@link InstrumentedTransaction} prior to a
 * that instance being returned to Hibernate. This allows user code to attach {@link TransactionListener} or perform any
 * other customization of such Transactions prior to even being started.
 *
 * @author dgarson
 */
public interface TransactionCustomizer {

    /**
     * Customizes a new transaction before returning it to Hibernate for use.
     */
    void customizeTransaction(InstrumentedTransaction transaction);
}
