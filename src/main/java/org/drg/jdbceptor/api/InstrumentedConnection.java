package org.drg.jdbceptor.api;

import org.drg.jdbceptor.event.ConnectionClosedListener;
import org.drg.jdbceptor.event.StatementCustomizer;
import org.drg.jdbceptor.spi.MetadataAwareConnection;

import java.lang.reflect.Method;
import java.sql.Connection;

/**
 * Wrapper around {@link Connection} that provides JDBC instrumentation support and allows subscribing to close
 * events. It also supports logical open/close vs. physical open/close in the cases where a Connection Pool is used.
 *
 * @author dgarson
 */
public interface InstrumentedConnection extends UserDataStorage, MetadataAwareConnection, Connection {

    /**
     * Checks whether this connection is being instrumented or whether it may have been constructed in a pass-through
     * mode to bypass instrumentation but still use an implementation of this class in all cases.
     */
    boolean isInstrumented();

    /**
     * Returns a textual label for this connection, generally a logical name.
     */
    String getDataSourceId();

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
     * Returns an identifier for this connection
     */
    String getConnectionId();

    /**
     * Sets the identifier for this connection.
     */
    // XXX FIXME delete this?
    // void setConnectionId(String connectionId);

    /**
     * Returns the timestamp in milliseconds when this connection was most recently opened. In the case of a pooled
     * connection, this timestamp represents the most recent lease time rather than the physical opened timestamp.
     */
    long getOpenedTimestamp();

    /**
     * Returns the timestamp when this connection was acquired (logically) if this is different then when it was first
     * opened (physically). This is primarily used for connection pooling use cases.
     */
    long getAcquisitionTimestamp();

    /**
     * Attaches an event listener that will be notified whenever this connection is closed. In the case of a pooled
     * connection, listeners will be notified when the pooled connection is released into the pool.
     */
    void addCloseListener(ConnectionClosedListener listener);

    /**
     * Attaches a listener that will be notified whenever a prepared or callable statement is created for this
     * connection, allowing user code to attach {@link org.drg.jdbceptor.event.StatementExecutionListener} or anything
     * else that should be done prior to executing such a statement.
     * @param customizer a customize that will have a callback invoked for each constructed InstrumentedStatement
     */
    void addStatementCustomizer(StatementCustomizer customizer);

    /**
     * Special method designed to be used when the C3P0ProxyConnection is being used such that a method invoked on the
     * raw connection (this object) can return the raw connection since C3P0 does not provide an easy way to do this
     * without hacking around a private field.
     * @return this object
     * @see com.mchange.v2.c3p0.C3P0ProxyConnection#rawConnectionOperation(Method, Object, Object[])
     * @see com.mchange.v2.c3p0.C3P0ProxyConnection#RAW_CONNECTION
     */
    @SuppressWarnings("unused")
    InstrumentedConnection getThis();

}