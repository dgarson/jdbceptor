package org.drg.jdbceptor.hibernate.config;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.DataSourceConfiguration;
import org.drg.jdbceptor.hibernate.InstrumentedConnectionProvider;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.drg.jdbceptor.hibernate.TransactionCustomizer;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderListener;

/**
 * Specialized configuration for a data source that uses Hibernate, providing additional capabilities such
 * as support for connection pools along with transaction tracking.
 *
 * @author dgarson
 */
public interface HibernateDataSourceConfiguration extends DataSourceConfiguration {

    /**
     * Checks whether <strong>any</strong> transaction instrumentation will/should be performed thru Hibernate session
     * factories using this instrumentation handler with the JDBCeptor driver. <br>
     * <strong>NOTE:</strong> this is a global configuration option and is not checked for each connection. If this
     * method returns <code>false</code> then no transaction instrumentation will ever be performed.
     */
    boolean isInstrumentTransactionsEnabled();

    /**
     * Checks whether we are capturing sequences of statements within a Hibernate transaction, if Hibernate is being
     * used and the {@link org.drg.jdbceptor.hibernate.InstrumentedTransactionFactory} is configured to be used.
     * </br>
     * <strong>OVERHEAD NOTICE</strong>: Enabling this has a memory overhead and depending on: </br>
     * <ol>
     *     <li>the number of statements within a given transaction and </li>
     *     <li>the size of the SQL queries being executed within those statements</li>
     * </ol>.
     * This may incur too high of a cost, depending on how the other feature checking methods, namely
     * {@link org.drg.jdbceptor.config.FeatureChecker#shouldInstrumentConnection()}, are implemented. If they are very
     * restrictive, then the overhead induced by this feature may be minimized due to the frequency it is being used
     * overall.
     */
    boolean isTransactionStatementCaptureEnabled();

    /**
     * Returns an optional <i>customizer</i> for newly constructed transactions. This is an optional component of the
     * Hibernate integration and returning <code>null</code> if customization is not necessary.
     */
    TransactionCustomizer getTransactionCustomizer();

    /**
     * Generates a transaction identifier for a given connection and instrumented transaction.
     * @param seqNo the sequence number for the transaction within the connection
     * @see org.drg.jdbceptor.hibernate.InstrumentedTransactionFactory
     */
    String generateTransactionId(InstrumentedConnection connection, InstrumentedTransaction transaction, int seqNo);

    /**
     * Returns an array containing the Hibernate {@link org.drg.jdbceptor.hibernate.event.ConnectionProviderListener}
     * classes that should be used for this data source.
     * @see ConnectionProviderListener
     */
    Class<? extends ConnectionProviderListener>[] getConnectionProviderListenerClasses();
}
