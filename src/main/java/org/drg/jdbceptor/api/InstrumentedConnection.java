package org.drg.jdbceptor.api;

import org.drg.jdbceptor.event.ConnectionClosedListener;
import org.drg.jdbceptor.event.StatementExecutionListener;
import org.drg.jdbceptor.impl.DataSourceManager;
import org.drg.jdbceptor.internal.DataSourceMember;
import org.drg.jdbceptor.internal.MetadataAwareConnection;
import org.drg.jdbceptor.internal.UserDataStorage;

import java.sql.Connection;

/**
 * Wrapper around {@link Connection} that provides JDBC instrumentation support and allows subscribing to close
 * events. It also supports logical open/close vs. physical open/close in the cases where a Connection Pool is used.
 *
 * @author dgarson
 */
public interface InstrumentedConnection extends UserDataStorage, MetadataAwareConnection, DataSourceMember, Connection {

    /**
     * Returns the data source manager for this connection. This method will always return a value for databases
     * using the Jdbceptor driver, even if the instrumentation is disabled.
     */
    DataSourceManager getDataSourceManager();

    /**
     * Checks whether this connection is being instrumented or whether it may have been constructed in a pass-through
     * mode to bypass instrumentation but still use an implementation of this class in all cases.
     */
    boolean isInstrumented();

    /**
     * Checks if these connections are being wrapped and managed by a connection pool.
     */
    boolean isPooled();

    /**
     * Returns an identifier for this connection
     */
    String getConnectionId();

    /**
     * Returns the most recent but incomplete instrumented statement that began executing on this connection but
     * has not yet returned from its query, or <code>null</code> if there is no active statement executing thru
     * this connection.
     */
    InstrumentedStatement<?> getExecutingStatement();

    /**
     * Returns the instrumented, wrapped connection which will never be <code>null</code>. All calls that go through
     * implementations of this interface eventually invoke the same operation on the real, underlying connection.
     */
    Connection getRealConnection();

    /**
     * Returns the timestamp in nanoseconds when this connection was most recently opened. In the case of a pooled
     * connection, this timestamp represents the most recent lease time rather than the physical opened timestamp.
     */
    long getOpenedTimestampNanos();

    /**
     * Returns the logical duration in milliseconds that this connection has been held for, or negative one (-1) if the
     * connection is not checked out.
     */
    long getCheckoutDurationNanos();

    /**
     * Returns the timestamp when this connection was acquired (logically) if this is different then when it was first
     * opened (physically). This is primarily used for connection pooling use cases.
     */
//    long getAcquisitionTimestamp();

    /**
     * Attaches an event listener that will be notified whenever this connection is closed. In the case of a pooled
     * connection, listeners will be notified when the pooled connection is released into the pool instead.
     */
    void addCloseListener(ConnectionClosedListener listener);

    /**
     * Attaches a statement event listener that will be invoked prior and after all statements are executed through
     * this connection.
     */
    void addStatementListener(StatementExecutionListener listener);

}