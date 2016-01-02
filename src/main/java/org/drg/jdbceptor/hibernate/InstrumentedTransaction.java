package org.drg.jdbceptor.hibernate;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.event.TransactionListener;
import org.hibernate.Transaction;

/**
 * Interface to be implemented by a framework-specific transactional wrapper. In the case of Hibernate, this uses the
 * &quot;org.hibernate.transaction&quot; package namespace.
 *
 * @author dgarson
 */
public interface InstrumentedTransaction extends Transaction {

    /**
     * Returns a unique identifier for this transaction.
     */
    String getTransactionId();

    /**
     * Returns the metadata-aware ConnectionProvider that is being used for connections that resulted in this
     * transaction.
     */
    MetadataAwareConnectionProvider getConnectionProvider();

    /**
     * Returns the timestamp when this transaction began. If this transaction has not yet begun (from the perspective
     * of MySQL), then this method will return zero.
     */
    long getOpenedTimestamp();

    /**
     * Returns the duration in milliseconds that this transaction has been active. If the transaction object has been
     * created but the transaction has not yet begun, then this method will return <code>-1</code>.
     */
    long getDurationMillis();

    /**
     * Returns the label associated with the database for which this transaction is managed.
     */
    String getDatabaseName();

    /**
     * Returns the Connection currently associated with this Transaction, if one has been leased already. If there is no
     * connection attached and connected, then this method will return <code>null</code> rather than forcing the
     * acquisition of one now.
     */
    InstrumentedConnection getConnection();

    /**
     * Attaches a single transaction listener instance to this transaction. Whenever this transaction performs any
     * operations, it will invoke callbacks on any attached listeners.
     */
    void addTransactionListener(TransactionListener listener);

    /**
     * Returns the optional user data object that may have been attached to this transaction.
     */
    Object getUserData();

    /**
     * Attaches a custom user data object to this transaction. This user data object will be removed automatically as
     * soon as the transaction is finished.
     */
    void setUserData(Object userData);
}
