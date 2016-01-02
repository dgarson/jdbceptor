package org.drg.jdbceptor.hibernate;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderAware;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderListener;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderListenerSettings;
import org.drg.jdbceptor.hibernate.event.PostConnectionAcquisitionListener;
import org.drg.jdbceptor.hibernate.event.PostConnectionCloseListener;
import org.drg.jdbceptor.hibernate.event.PreConnectionAcquisitionListener;
import org.drg.jdbceptor.hibernate.event.PreConnectionCloseListener;
import org.drg.jdbceptor.util.JdbcUrlUtils;
import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.connection.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Abstract base class for a {@link org.hibernate.connection.ConnectionProvider} implementation that will be used with
 * the Hibernate integration of Jdbceptor.
 *
 * @author dgarson
 */
public abstract class AbstractInstrumentedConnectionProvider implements
    ConnectionProvider, MetadataAwareConnectionProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Property that contains a comma-separated list of class names for ConnectionProviderListeners that may or may
     * not be enabled for a particular provider depending on annotation-defined conditions or interface-implemented
     * checker methods. </br>
     * This property is a Hibernate property and not a system property (or any other type).
     **/
    public static final String PROPERTY_CONNECTION_PROVIDER_LISTENERS = "jdbceptor.listeners";

    // cached JDBC connection URL
    private String jdbcUrl;

    // the database name, extracted from the JDBC URL
    protected String databaseName;

    private final HibernateAwareInstrumentationHandler instrumentationHandler;
    private HibernateConnectionResolver connectionResolver;

    // list of zero or more ConnectionProviderListener that are registered with this connection provider wrapper
    private final List<PreConnectionAcquisitionListener> preAcquisitionListeners = new ArrayList<>();
    private final List<PostConnectionAcquisitionListener> postAcquisitionListeners = new ArrayList<>();
    private final List<PreConnectionCloseListener> preCloseListeners = new ArrayList<>();
    private final List<PostConnectionCloseListener> postCloseListeners = new ArrayList<>();
    private final Map<Class<? extends ConnectionProviderListener>, ConnectionProviderListener> listenerMap =
        new HashMap<>();

    public AbstractInstrumentedConnectionProvider() {
        instrumentationHandler = (HibernateAwareInstrumentationHandler) Jdbceptor.getInstrumentationHandler();
    }

    @Override
    public final void configure(Properties props) throws HibernateException {
        // allow subclass to pre-process if desired
        preConfigure(props);

        // cache JDBC url
        jdbcUrl = props.getProperty(Environment.URL);
        databaseName = resolveDatabaseName(jdbcUrl, props);

        // do actual initialization in subclass impl.
        initialize(props);

        // attach listeners: look for listener definitions, defaults to a zero-token array of class names
        String[] listenerClassNames = StringUtils.split(props.getProperty(PROPERTY_CONNECTION_PROVIDER_LISTENERS, ""),
            ",");
        // reset any previously instantiated listeners in case we are using this method multiple times (such as in a test)
        removeAllListeners();
        int numListeners = 0;
        Class<? extends ConnectionProviderListener>[] listenerClasses;
        if (listenerClassNames.length == 0) {
            if (instrumentationHandler != null) {
                listenerClasses = instrumentationHandler.getConnectionProviderListenerClasses(this);
                if (listenerClasses == null) {
                    listenerClasses = new Class[0];
                }
            } else {
                listenerClasses = (Class<? extends ConnectionProviderListener>[])new Class[0];
            }
        } else {
            // loop through configured listeners and create/attach them
            List<Class<? extends ConnectionProviderListener>> listenerClassList = new ArrayList<>();
            for (String listenerClassName : listenerClassNames) {
                try {
                    listenerClassList.add(Class.forName(listenerClassName)
                        .asSubclass(ConnectionProviderListener.class));
                } catch (ClassNotFoundException cnfe) {
                    log.error("Unable to find ConnectionProviderListener class: {}", listenerClassName);
                } catch (ClassCastException cce) {
                    log.error("{} does not implement {}", listenerClassName,
                        ConnectionProviderListener.class.getName());
                }
            }
            listenerClasses = listenerClassList.toArray(new Class[listenerClassList.size()]);
        }
        for (Class<? extends ConnectionProviderListener> listenerClazz : listenerClasses) {
            // label that is updated throughout try block so exception message explicitly indicates where problem occurred
            try {
                ConnectionProviderListener listener = createListener(props, listenerClazz);
                // once we've created and initialized the listener, add it the appropriate listener lists
                if (listener != null) {
                    addToListenerLists(listener);
                    numListeners++;
                }

            } catch (Exception e) {
                log.error("Unable to attach {} to {}[{}] due to exception", listenerClazz,
                    getClass().getSimpleName(), databaseName, e);
            }
        }
        log.debug("Created {} interceptors for {}[{}]", numListeners, getClass().getSimpleName(), databaseName);

        // cache the connection resolver if available
        if (instrumentationHandler != null) {
            connectionResolver = instrumentationHandler.getConnectionResolver(this);
        }

        // post-configuration callback
        configured(props);
    }

    /**
     * Invoked immediately prior to the business logic in {@link #configure(Properties)} method.
     */
    protected void preConfigure(Properties props) {
        // no-op
    }

    /**
     * Resolves a human-readable label for a given JDBC URL, with the connection properties provided in case any
     * subclass wishes to use them to return a value. </br>
     * The default implementation simply returns the portion of the JDBC URL after the hostname but before any optional
     * query parameters.
     * @see JdbcUrlUtils#getDatabaseNameFromJdbcUrl(String)
     */
    protected String resolveDatabaseName(String jdbcUrl, Properties hibernateProperties) throws HibernateException {
        return JdbcUrlUtils.getDatabaseNameFromJdbcUrl(jdbcUrl);
    }

    /**
     * Creates a new {@link ConnectionProviderListenerSettings} object that will be passed into listeners being attached
     * to this connection provider.
     */
    protected ConnectionProviderListenerSettings createListenerSettings(Properties hibernateProperties) {
        return new ConnectionProviderListenerSettings(this, hibernateProperties);
    }

    /**
     * Overrideable replacement for {@link #configure(Properties)} so that subclasses can perform their own
     * initialization, but allows this abstract superclass to control the way it is invoked, allowing pre and post
     * processing.
     */
    protected abstract void initialize(Properties props) throws HibernateException;

    /**
     * Callback method invoked at the end of the {@link #configure(Properties)} call so that post-processing can be
     * done.
     * @param props the properties used to configure this connection provider
     */
    protected void configured(Properties props){
        // no-op by default, optional override
    }

    @Override
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String getDataSourceId() {
        return databaseName;
    }

    @Override
    public abstract void close() throws HibernateException;

    @Override
    public abstract boolean supportsAggressiveRelease();

    @Override
    public final Connection getConnection() throws SQLException {
        Connection conn;
        try {
            conn = executeOperation(/*existingConnection=*/null, /*isAcquisition=*/true,
                /*invokeFailureListeners=*/false);
        } catch (SQLException se) {
            // notify subclass of failure prior to invoking failure listeners
            SQLException rethrowException = connectionAcquisitionFailed(se);

            // invoke failure listeners
            invokeListeners(/*isBefore=*/false, /*isAcquisition=*/true, /*exception=*/se,  null);

            // rethrow exception
            if (rethrowException == null) {
                rethrowException = se;
            }
            throw rethrowException;
        }

        // invoke callback for subclass
        afterAcquisition(conn);

        return conn;
    }

    @Override
    public final void closeConnection(Connection conn) throws SQLException {
        // we must extract the InstrumentedConnection first since it may be unavailable if using a pooled connection
        //      and executeOperation(..) clears the 'inner' connection ref
        InstrumentedConnection connection = null;
        if (conn instanceof InstrumentedConnection) {
            connection = (InstrumentedConnection) conn;
        } else if (connectionResolver != null) {
            connection = connectionResolver.resolveInstrumentedConnection(conn);
        }

        try {
            beforeRelease(conn);
            executeOperation(conn, /*isAcquisition=*/false, /*invokeFailureListeners=*/true);
        } finally {
            // make sure to always call this so book-keeping elsewhere does not have major problems
            afterClose(connection, conn);
        }
    }

    /**
     * Acquires a new connection from the connection provider. This method is a replacement for the declared method
     * on the ConnectionProvider interface so that this abstract base class can orchestrate when it is called.
     * @see ConnectionProvider#getConnection()
     */
    protected abstract Connection acquireConnection() throws SQLException;

    private void afterAcquisition(Connection conn) {
        InstrumentedConnection connection = null;
        if (conn instanceof InstrumentedConnection) {
            connection = (InstrumentedConnection) conn;
        } else if (connectionResolver != null) {
            connection = connectionResolver.resolveInstrumentedConnection(conn);
        }
        if (connection != null && connection instanceof HibernateAwareInstrumentedConnection) {
            ((HibernateAwareInstrumentedConnection)connection).acquiredFromConnectionProvider(conn);
        }
        // invoke callback for subclass
        connectionAcquired(conn);
    }

    private void afterClose(InstrumentedConnection connection, Connection conn) {
        if (connection != null && connection instanceof HibernateAwareInstrumentedConnection) {
            ((HibernateAwareInstrumentedConnection)connection).releasedToConnectionProvider();
        }

        // invoke callback for subclass
        connectionReleased(conn);
    }

    /**
     * Callback that is invoked after prior to calling all post-acquisition callbacks for failures. This provides
     * subclasses the opportunity to either squelch or customize reactions to and respond to exceptions acquiring
     * connections outside of the listeners attached to this provider. </br>
     * After this method returns, the SQLException will be propagated up, unless another {@link SQLException} is
     * returned from this call.
     */
    protected SQLException connectionAcquisitionFailed(SQLException exception) {
        return exception;
    }

    /**
     * Callback that is invoked after all post-acquisition callbacks have been invoked.
     */
    protected abstract void connectionAcquired(Connection conn);

    /**
     * Closes an existing connection opened from this connection provider.
     * @see ConnectionProvider#closeConnection(Connection)
     */
    protected abstract void releaseConnection(Connection connection) throws SQLException;

    protected abstract void beforeRelease(Connection connection);

    /**
     * Callback that is invoked after all post-release callbacks have been invoked.
     */
    protected abstract void connectionReleased(Connection conn);

    /**
     * Executes the appropriate connection operation depending on the value of <strong>existingConnection</strong>. Whenever a connection is provided, we know that we are definitely going
     * to be performing a close operation, since no connection exists to be passed in when acquiring a connection. As such, if the <strong>existingConnection</strong> is found to be
     * <code>null</code> then we know that we are definitely acquiring/opening a new connection.
     * @param existingConnection the existing connection, if closing a connection, otherwise <code>null</code> for opening a connection
     * @param isAcquisition explicit declaration of whether this is an open vs. close (needed for unit testability)
     * @param invokeFailureListeners if true then failure handlers will be invoked automatically if an exception occurs, otherwise if <code>false</code> then the caller of this
     *                                   method is responsible for invoking the failure handlers in all subscribed and enabled interceptors in the chain
     * @return a connection that was successfully acquired, or <code>null</code> if <strong>existingConnection</strong> was provided and we closed the connection successfully
     * @throws SQLException on any database or connection pooling exceptions
     */
    private Connection executeOperation(Connection existingConnection, boolean isAcquisition, boolean invokeFailureListeners) throws SQLException {
        // invoke pre-* callbacks
        invokeListeners(/* isBefore=*/ true, /*isAcquisition=*/ isAcquisition, /*exc=*/null, /*connection=*/existingConnection);

        // invoke the actual operation
        Connection result;
        try {
            if (isAcquisition) {
                // acquire a connection from c3p0 and return it
                result = acquireConnection();
            } else {
                // simply release the connection and return null
                releaseConnection(existingConnection);
                result = null;
            }
        } catch (SQLException | RuntimeException e) {
            // we are catching these to make sure we can properly invoke the failure listeners that are attached to this operation.
            //  if we immediately propagated the exception up to the caller, then we would never invoke these listeners

            // invoke failure callbacks in interceptor(s), but only if the caller is not going to do so themselves
            if (invokeFailureListeners) {
                // make sure to pass in the 'existingConnection' value
                invokeListeners(/* isBefore=*/ false, /*isAcquisition=*/ isAcquisition, /*exc=*/e, /*connection=*/existingConnection);
            }

            // rethrow the exception
            throw e;
        }

        // if we get this far, then we succeeded at applying the operation and must invoke post-operation callbacks on interceptors
        invokeListeners(/* isBefore=*/ false, /*isAcquisition=*/ isAcquisition, /*exc=*/null, /*connection=*/result);

        // return the result
        return result;
    }

    /**
     * Invokes all subscribed interceptor callbacks for the specified operation.
     * @param isBefore true if invoking a pre vs. post listener handler method
     * @param isAcquisition true if acquiring a connection, false if releasing a connection
     * @param exception any exception that was caught while performing the operation requested
     * @param connection any connection that was being closed (on exception) or an opened connection (after successful
     *                  acquire), or <code>null</code> after a successful release and failed acquire
     */
    private void invokeListeners(boolean isBefore, boolean isAcquisition, Throwable exception, Connection connection) {
        for (ConnectionProviderListener listener :
                /* only loop over the listeners that are registered for this particular event type (pre/post open/close) */
            getListenersForEventType(isBefore, isAcquisition)) {
            // allow the listener itself to determine whether it is enabled and will have this callback invocation skipped or not
            try {
                if (isAcquisition) {
                    if (isBefore) {
                        ((PreConnectionAcquisitionListener) listener).beforeConnectionAcquisition(this);
                    } else {
                        PostConnectionAcquisitionListener postListener = (PostConnectionAcquisitionListener) listener;
                        if (exception != null) {
                            postListener.afterConnectionAcquisitionFailed(this, exception);
                        } else {
                            postListener.afterConnectionAcquired(this, connection);
                        }
                    }
                } else {
                    if (isBefore) {
                        ((PreConnectionCloseListener) listener).beforeClosingConnection(connection);
                    } else {
                        PostConnectionCloseListener postListener = (PostConnectionCloseListener) listener;
                        if (exception != null) {
                            postListener.afterConnectionClosingFailed(connection, exception);
                        } else {
                            postListener.afterConnectionClosed();
                        }
                    }
                }
            } catch (Throwable t) {
                if (log.isErrorEnabled()) {
                    if (isBefore) {
                        log.error(String.format("Unable to invoke listener handler for event [%s%s] on %s (database is %s)",
                            (isBefore ? "before" : "after"), (isAcquisition ? "ConnectionAcquired" : "ConnectionClosed"),
                            listener.getClass().getName(), databaseName), t);
                    } else {
                        log.error(String.format("Failed to invoke listener handler for event [%s%s] on %s (database is %s)",
                            (isBefore ? "before" : "after"), (isAcquisition ? "ConnectionAcquired" : "ConnectionClosed"),
                            listener.getClass().getName(), databaseName), t);
                    }
                }
            }
        }
    }


    private List<? extends ConnectionProviderListener> getListenersForEventType(boolean isBefore, boolean isAcquisition) {
        if (isAcquisition) {
            return (isBefore ? preAcquisitionListeners : postAcquisitionListeners);
        } else {
            return (isBefore ? preCloseListeners : postCloseListeners);
        }
    }

    <L extends ConnectionProviderListener> L getListenerOfType(Class<L> listenerType) {
        ConnectionProviderListener listener = listenerMap.get(listenerType);
        if (listener == null) {
            return null;
        }
        return listenerType.cast(listener);
    }

    /**
     * Creates an instance of the {@link ConnectionProviderListener} implementation defined in the
     * <strong>listenerClazz</strong> and both constructs an instance, passes in database settings for this connection
     * provider, and injects this ConnectionProviderWrapper into the listener instance
     * @param hibernateProperties the hibernate properties, which may be used by the listener to check if it is enabled/disabled, or to extract configurable values
     * @param listenerClazz the listener implementation class that should be used
     * @return the listener instance, without having its
     * {@link ConnectionProviderListener#initialize(ConnectionProviderListenerSettings)} method called yet
     */
    private ConnectionProviderListener createListener(Properties hibernateProperties, Class<? extends ConnectionProviderListener> listenerClazz) {
        // try to locate a constructor that takes ConnectionProviderListenerSettings object first
        Constructor<? extends ConnectionProviderListener> constructor;
        try {
            constructor = listenerClazz.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (Exception e) {
            // continue and try no-arg constructor
            log.error("Unable to find a no-arg constructor for ConnectionProviderListener implementation: {}",
                listenerClazz, e);
            return null;
        }

        // now that we know it is enabled, instantiate it and attach to this ConnectionProviderWrapper
        ConnectionProviderListener listener;
        try {
            // instantiate the new listener instance
            listener = constructor.newInstance();
            // pass in the Settings
            ConnectionProviderListenerSettings settings = createListenerSettings(hibernateProperties);

            // inject the parent (this) ConnectionProviderWrapper into the listener object we just created, but make sure to do so before we call initialize(..)
            if (listener instanceof ConnectionProviderAware) {
                ((ConnectionProviderAware)listener).setConnectionProvider(this);
            }

            listener.initialize(settings);

            return listener;
        } catch (Exception e) {
            log.error("Unable to instantiate listener of type {} for {}[{}]", listenerClazz, getClass().getSimpleName(),
                databaseName, e);
            return null;
        }
    }

    /**
     * Adds a given {@link ConnectionProviderListener} to its specific interface-implementing event listener types.
     * This allows us to optimize invocations to listeners based on the event so we are not forced to iterate over all
     * listeners that may not subscribe to that callback.
     *
     * @param listener the listener to add to appropriate listener type lists
     */
    private void addToListenerLists(ConnectionProviderListener listener) {
        Class<? extends ConnectionProviderListener> listenerClass = listener.getClass();
        Preconditions.checkState(!listenerMap.containsKey(listenerClass),
            "listener type [" + listenerClass.getSimpleName() + "] already attached to " + databaseName);
        listenerMap.put(listenerClass, listener);

        // attach to each phase-specific listener lists, one for each listener interface it implements
        if (listener instanceof PreConnectionAcquisitionListener) {
            preAcquisitionListeners.add((PreConnectionAcquisitionListener)listener);
        }
        if (listener instanceof PostConnectionAcquisitionListener) {
            postAcquisitionListeners.add((PostConnectionAcquisitionListener)listener);
        }
        if (listener instanceof PreConnectionCloseListener) {
            preCloseListeners.add((PreConnectionCloseListener)listener);
        }
        if (listener instanceof PostConnectionCloseListener) {
            postCloseListeners.add((PostConnectionCloseListener)listener);
        }
    }

    /**
     * Removes all listeners from this connection provider.
     * @see #addToListenerLists(org.drg.jdbceptor.hibernate.event.ConnectionProviderListener)
     */
    protected void removeAllListeners() {
        preAcquisitionListeners.clear();
        postAcquisitionListeners.clear();
        preCloseListeners.clear();
        postCloseListeners.clear();
    }
}
