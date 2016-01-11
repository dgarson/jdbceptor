package org.drg.jdbceptor.impl;

import com.google.common.base.Preconditions;
import org.drg.jdbceptor.api.ConnectionCustomizer;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.ConnectionResolver;
import org.drg.jdbceptor.config.DataSourceConfiguration;
import org.drg.jdbceptor.config.FeatureChecker;
import org.drg.jdbceptor.config.QueryParameterFormatter;
import org.drg.jdbceptor.event.ConnectionClosedEvent;
import org.drg.jdbceptor.event.ConnectionClosedListener;
import org.drg.jdbceptor.event.ConnectionOpenedEvent;
import org.drg.jdbceptor.event.ConnectionOpenedListener;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;
import org.drg.jdbceptor.hibernate.TransactionCustomizer;
import org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized logic for managing callbacks and invoking associated listeners, etc. for a single data source. This
 * contains mostly logic that would otherwise live in the Jdbceptor class itself, but is here to avoid polluting the
 * actually Driver implementation class. </br>
 *
 * XXX TODO(dgarson) add hibernate integration support to this class, or create a separate subclass, but i think this one is good
 *
 * @author dgarson
 */
public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);

    private final String id;
    private final ConnectionResolver connectionResolver;
    private final ConnectionCustomizer connectionCustomizer;
    private final TransactionCustomizer transactionCustomizer;
    private final FeatureChecker features;
    private final QueryParameterFormatter queryParamFormatter;
    private final boolean pooled;
    private final boolean instrumented;

    private final boolean usingHibernate;

    /**
     * Local counters used for generating new transaction and connection identifiers
     */
    private final AtomicLong nextConnectionId = new AtomicLong();
    private final AtomicLong nextTransactionId = new AtomicLong();

    /**
     * Logical opened event listeners - in the case of pooling, these will be automatically attached to physical
     * connections after they are first established.
     */
    private final List<ConnectionOpenedListener> logicalConnectionOpenedListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionClosedListener> logicalConnectionClosedListeners = new ArrayList<>();

    /**
     * List of connection opened event listeners that wish to subscribe to physical connection events when Jdbceptor is
     * being used underneath and integrated with a connection pool.
     */
    private final List<ConnectionOpenedListener> physicalConnectionOpenedListeners = new CopyOnWriteArrayList<>();
    private final List<ConnectionClosedListener> physicalConnectionClosedListeners = new CopyOnWriteArrayList<>();

    public DataSourceManager(DataSourceConfiguration config) {
        Preconditions.checkNotNull(config, "DataSourceConfiguration was not provided");
        this.id = config.getId();
        this.features = config.getFeatureChecker();
        this.usingHibernate = (config instanceof HibernateDataSourceConfiguration);
        this.connectionResolver = config.getConnectionResolver();
        this.connectionCustomizer = config.getConnectionCustomizer();
        this.queryParamFormatter = config.getQueryParameterFormatter();
        this.transactionCustomizer = (usingHibernate ?
            ((HibernateDataSourceConfiguration)config).getTransactionCustomizer() : null);
        this.pooled = config.isPoolingConnections();
        this.instrumented = config.isInstrumented();
    }

    /**
     * Returns the id for this data source.
     * @see DataSourceConfiguration#getId()
     */
    public String getId() {
        return id;
    }

    /**
     * Returns true if any instrumentation might be enabled and false if all instrumentation is and will remain disabled
     * for this data source.
     */
    public boolean isInstrumented() {
        return instrumented;
    }

    /**
     * Returns <code>true</code> if connections are being pooled for this data source and <code>false</code> otherwise.
     * @see DataSourceConfiguration#isPoolingConnections()
     */
    public boolean isPoolingConnections() {
        return pooled;
    }

    /**
     * Returns the feature checker to use to determine if instrumentation should be enabled at a given moment at
     * runtime.
     */
    public FeatureChecker getFeatures() {
        return features;
    }

    /**
     * Formats a parameter value so that it can be output as part of a parameterized query being dumped such that the
     * output matches exactly what could be input into the SQL client and run against the database. This is used for
     * Prepared/Callable statement parameter capturing.
     */
    public String formatParameterValue(Object paramValue) {
        // special case for null
        if (paramValue == null) {
            return null;
        } else if (queryParamFormatter == null) {
            return paramValue.toString();
        } else {
            return queryParamFormatter.formatParameter(paramValue);
        }
    }

    /**
     * Resolves any native identifier for a connection, generating one if none can be resolved automatically.
     * @param connection the connection to get an ID for
     * @return some String uniquely identifying the connection
     */
    public String getConnectionId(InstrumentedConnection connection) {
        if (connectionResolver != null) {

        }
        // XXX TODO(dgarson) figure out how to properly abstract this... should it be in an existing class/interface?
        return UUID.randomUUID().toString();
    }

    /**
     * Attaches a listener that will be notified whenever a connection is opened. If wrapped by a connection pool, then
     * this event will only be fired when logically <i>acquiring</i> a connection from the pool rather than establishing
     * a physical connection.
     */
    public void addConnectionOpenedListener(ConnectionOpenedListener listener) {
        Preconditions.checkState(!logicalConnectionOpenedListeners.contains(listener), "listener already registered: " +
            listener);
        logicalConnectionOpenedListeners.add(listener);
    }

    public void removeConnectionOpenedListener(ConnectionOpenedListener listener) {
        logicalConnectionOpenedListeners.remove(listener);
    }

    /**
     * Attaches a listener that will be notified whenever a physical connection is opened. If wrapped by a connection
     * pool, this event will not be fired for logical acquisitions and instead only invoked when allocating new physical
     * connections when growing the connection pool.
     */
    public void addPhysicalConnectionOpenedListener(ConnectionOpenedListener listener) {
        if (pooled) {
            Preconditions.checkState(!physicalConnectionOpenedListeners.contains(listener),
                "listener already registered: " + listener);
            physicalConnectionOpenedListeners.add(listener);
        } else if (!logicalConnectionOpenedListeners.contains(listener)) {
            logicalConnectionOpenedListeners.add(listener);
        }
    }

    public void removePhysicalConnectionOpenedListener(ConnectionOpenedListener listener) {
        if (pooled) {
            physicalConnectionOpenedListeners.remove(listener);
        } else {
            logicalConnectionClosedListeners.remove(listener);
        }
    }

    /**
     * Customizes a new <strong>connection</strong> using any registered customizers, prior to invoking logical
     * connection opened event callbacks, but after invoking physical connection event event callbacks.
     */
    public void customizeConnection(InstrumentedConnection connection) {
        if (connectionCustomizer != null) {
            connectionCustomizer.customizeConnection(connection);
        }
    }

    /**
     * Customizes a new <strong>transaction</strong> using any registered customizers, prior to invoking logical
     * transaction begin/commit/rollback callbacks.
     */
    public void customizeTransaction(InstrumentedTransaction transaction) {
        if (transactionCustomizer != null) {
            transactionCustomizer.customizeTransaction(transaction);
        }
    }

    /**
     * Resolves the correct {@link InstrumentedConnection} from Hibernate in cases where a connection pool may be used
     * and the connection being used by the user is in fact wrapping an {@link InstrumentedConnection}, meaning just
     * trying to cast may fail with a class cast exception. </br>
     * This method is provided here for convenience only.
     * @see ConnectionResolver#resolveInstrumentedConnection(Connection)
     */
    public InstrumentedConnection resolveInstrumentedConnection(Connection connection) {
        return resolveInstrumentedConnection(connection, /*useDefaultNull=*/false);
    }

    public InstrumentedConnection resolveInstrumentedConnection(Connection connection, boolean useDefaultNull) {
        if (connection == null) {
            return null;
        } else if (connection instanceof InstrumentedConnection) {
            return (InstrumentedConnection)connection;
        } else if (connectionResolver != null) {
            // delegate to the resolver if present
            return connectionResolver.resolveInstrumentedConnection(connection);
        } else if (useDefaultNull) {
            return null;
        } else {
            // assert that this is never the case, as this would mean Jdbceptor integration is very, very broken
            throw new IllegalStateException("Unable to coerce connection of type '" + connection.getClass() +
                "' to an InstrumentedConnection. Did you mean to register a ConnectionResolver for data source '" +
                id + "'?");
        }
    }

    /**
     * Invoked whenever a connection is opened through the wrapped driver. </br>
     * This method will only invoke callbacks when a connection pool is being used for this data source and a separate
     * close event must be triggered here rather than just with logical open/close.
     */
    public void physicalConnectionOpened(ConnectionOpenedEvent event) {
        if (!event.isPooled()) {
            // already handled thru logicalConnectionClosed(..)
            log.warn("physicalConnectionOpened was called for {} but isPooled = false", getId());
            return;
        }
        // invoke physical event listeners only
        for (ConnectionOpenedListener listener : physicalConnectionOpenedListeners) {
            listener.connectionOpened(event);
        }
    }

    /**
     * Invoked whenever a connection is closed through the wrapped driver. </br>
     * This method will only invoke callbacks when a connection pool is being used for this data source and a separate
     * close event must be triggered here rather than just with logical open/close.
     */
    public void physicalConnectionClosed(ConnectionClosedEvent event) {
        if (!event.isPooled()) {
            // already handled thru logicalConnectionClosed(..)
            log.warn("physicalConnectionClosed was called for {} but isPooled = false", getId());
            return;
        }
        for (ConnectionClosedListener listener : physicalConnectionClosedListeners) {
            listener.connectionClosed(event);
        }
    }

    /**
     * Invoked whenever a logical connection is opened. </br>
     */
    public void logicalConnectionOpened(ConnectionOpenedEvent event) {
        for (ConnectionOpenedListener listener : logicalConnectionOpenedListeners) {
            listener.connectionOpened(event);
        }
    }

    /**
     * Invoked whenever a logical connection is released. </br>
     */
    public void logicalConnectionClosed(ConnectionClosedEvent event) {
        for (ConnectionClosedListener listener : logicalConnectionClosedListeners) {
            listener.connectionClosed(event);
        }
    }
}
