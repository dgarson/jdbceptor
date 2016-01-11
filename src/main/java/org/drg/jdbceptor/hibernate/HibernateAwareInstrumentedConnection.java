package org.drg.jdbceptor.hibernate;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.DataSourceConfiguration;
import org.drg.jdbceptor.event.ConnectionClosedListener;
import org.drg.jdbceptor.event.ConnectionOpenedListener;

import java.sql.Connection;

/**
 * Specialization of {@link org.drg.jdbceptor.api.InstrumentedConnection} that is aware of Hibernate-level transactions
 * that are being managed within it.
 *
 * @author dgarson
 */
public interface HibernateAwareInstrumentedConnection extends InstrumentedConnection {

    /**
     * Returns any currently active transaction for this connection, or <code>null</code> if there is none or
     * transaction tracking is not enabled.
     */
    InstrumentedTransaction getCurrentTransaction();

    /**
     * Explicitly injects the current transaction active for this connection, or <code>null</code> to indicate that no
     * transaction is currently active. This is used by the {@link InstrumentedTransactionFactory} to keep the
     * connection up to date with the transaction instrumentation.
     */
    //void setCurrentTransaction(InstrumentedTransaction transaction);

    /**
     * Returns a connection that represents a pooled connection around this physical, instrumented connection. </br>
     * <strong>NOTE:</strong> this property must be injected explicitly in order to support pooling.
     * @see org.drg.jdbceptor.config.ConnectionResolver
     */
    Connection getPooledConnection();

    /**
     * Invoked when this connection is released back to the connection provider that it was previously acquired from.
     * This is used to support logical connections being independent of physical connections, such as when using a
     * connection pool such as c3p0.
     */
    void releasedToConnectionProvider();

    /**
     * Invoked when this connection is acquired from the connection provider. This may correspond with a physical
     * connection acquisition but in the case of connection pools, logical connections may not always line up one-to-one
     */
    void acquiredFromConnectionProvider(Connection connection);

    /**
     * Attaches an event listener that will be notified whenever the physical connection is closed when a connection
     * pool is wrapping the Jdbceptor instrumented connections. When no connection pool is being used, the standard
     * logical connection close listener should be used instead.
     * @see #addCloseListener(ConnectionClosedListener)
     */
    void addPhysicalCloseListener(ConnectionClosedListener closeListener);

    /**
     * Attaches an event listener that will be notified whenever a pooled connection is acquired and wrapping this
     * connection instance.
     * @see DataSourceConfiguration#isPoolingConnections()
     */
    void addAcquisitionListener(ConnectionOpenedListener openListener);
}
