package org.drg.jdbceptor.hibernate.event;

import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.hibernate.HibernateException;

/**
 * Interceptor API for Hibernate transactions that are ultimately going thru a jdbceptor Driver instance.
 *
 * @author dgarson
 */
public interface TransactionListener {

    /**
     * Callback that is invoked when the transaction is begun, from the perspective of the application server.
     */
    void transactionBegan(InstrumentedTransaction transaction);

    /**
     * Callback that is invoked when a transaction is committed successfully to the database.
     */
    void transactionCommitted(InstrumentedTransaction transaction);

    /**
     * Callback that is invoked when a transaction commit failed, prior to any attempted rollbacks.
     */
    void transactionCommitFailed(InstrumentedTransaction transaction, HibernateException e);

    /**
     * Callback that is invoked when a transaction was attempted to be committed but it could not be and it was rolled
     * back.
     */
    void transactionRolledBack(InstrumentedTransaction transaction);

    /**
     * Callback that is invoked when a transaction rollback fails.
     */
    void transactionRollbackFailed(InstrumentedTransaction transaction, HibernateException e);
}

