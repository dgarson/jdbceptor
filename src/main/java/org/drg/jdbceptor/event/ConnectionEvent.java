package org.drg.jdbceptor.event;

import com.google.common.base.Ticker;
import org.drg.jdbceptor.api.InstrumentedConnection;

import java.util.concurrent.TimeUnit;

/**
 * Provides a base superclass for properties available from a reference to any event generated from an
 * InstrumentedConnection or from any of its child elements, such as statements or, in the case of Hibernate
 * integration, transactions. </br>
 * The timestamp is provided as an argument rather than being retrieved since it both takes a system call and because if
 * we want to use any mock {@link com.google.common.base.Ticker} for testing we must make sure that the Ticker, wherever
 * it is, is being used to get that timestamp.
 *
 * @author dgarson
 */
public abstract class ConnectionEvent {

    protected final InstrumentedConnection connection;
    protected final EventType type;
    protected final long timestampNanos;
    protected final boolean pooled;
    protected final Object source;

    protected ConnectionEvent(InstrumentedConnection connection, EventType type,
                              long timestampNanos, boolean pooled, Object source) {
        this.connection = connection;
        this.type = type;
        this.timestampNanos = timestampNanos;
        this.pooled = pooled;
        this.source = source;
    }

    /**
     * Returns the connection associated with this event.
     */
    public InstrumentedConnection getConnection() {
        return connection;
    }

    /**
     * Returns the instant that this event occurred, in nanoseconds, since the Unix Epoch. This is the unit captured by
     * the event because it is the unit of measure for values returned from {@link Ticker#read()}.
     */
    public long getTimestampNanos() {
        return timestampNanos;
    }

    /**
     * Returns the timestamp of this event in milliseconds since the Unix Epoch.
     */
    public long getTimestampMillis() {
        return TimeUnit.MILLISECONDS.convert(timestampNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Returns the type of this event.
     */
    public EventType getEventType() {
        return type;
    }

    /**
     * Returns the <i>event source</i> which can vary depending on the event implementation.
     */
    public Object getEventSource() {
        return source;
    }

    /**
     * Checks whether connection pools are being used and therefore this event represents a logical acquisition
     * instead of establishment of a physical connection to the database.
     */
    public boolean isPooled() {
        return pooled;
    }
}
