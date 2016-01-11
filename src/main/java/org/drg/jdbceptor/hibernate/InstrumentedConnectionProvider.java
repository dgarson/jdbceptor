package org.drg.jdbceptor.hibernate;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.config.HibernateDataSourceConfiguration;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderAware;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderListener;
import org.drg.jdbceptor.hibernate.event.ConnectionProviderListenerSettings;
import org.drg.jdbceptor.hibernate.event.PostConnectionAcquisitionListener;
import org.drg.jdbceptor.hibernate.event.PostConnectionCloseListener;
import org.drg.jdbceptor.hibernate.event.PreConnectionAcquisitionListener;
import org.drg.jdbceptor.hibernate.event.PreConnectionCloseListener;
import org.drg.jdbceptor.impl.DataSourceManager;
import org.drg.jdbceptor.internal.DataSourceMember;
import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.connection.ConnectionProvider;
import org.hibernate.connection.ConnectionProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract base class for a {@link org.hibernate.connection.ConnectionProvider} implementation that will be used with
 * the Hibernate integration of Jdbceptor. This provider implementation may only be used when also using the provided
 * Jdbceptor-subclass of {@link org.hibernate.cfg.Configuration}.
 *
 * @author dgarson
 *
 * @see InstrumentedHibernateConfiguration
 */
public class InstrumentedConnectionProvider implements ConnectionProvider, DataSourceMember {

    public static final String DELEGATING_CONNECTION_PROVIDER_CLASS = "hibernate.connection.delegate_provider_class";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // configuration for the associated data source, handed off via a thread-local
    private final HibernateDataSourceConfiguration dataSourceConfig;
    private final String dataSourceId;
    private DataSourceManager dataSourceManager;

    // the real connection provider we are delegating to
    private ConnectionProvider realConnectionProvider;

    // cached JDBC connection URL
    private String jdbcUrl;

    // list of zero or more ConnectionProviderListener that are registered with this connection provider wrapper
    private final List<PreConnectionAcquisitionListener> preAcquisitionListeners = new ArrayList<>();
    private final List<PostConnectionAcquisitionListener> postAcquisitionListeners = new ArrayList<>();
    private final List<PreConnectionCloseListener> preCloseListeners = new ArrayList<>();
    private final List<PostConnectionCloseListener> postCloseListeners = new ArrayList<>();
    private final Map<Class<? extends ConnectionProviderListener>, ConnectionProviderListener> listenerMap =
        new HashMap<>();

    public InstrumentedConnectionProvider() {
        dataSourceConfig = InstrumentedHibernateConfiguration.getCurrentDataSourceConfig();
        dataSourceId = dataSourceConfig.getId();
        if (dataSourceConfig == null) {
            throw new IllegalStateException("You must use an InstrumentedHibernateConfiguration to build a " +
                "SessionFactory with Jdbceptor integration");
        }
    }

    /**
     * Creates and attaches any listeners that are declared for the connection provider in the data source config. This
     * method is called from the {@link #configure(Properties)} method.
     * @param props the hibernate properties
     * @see HibernateDataSourceConfiguration#getConnectionProviderListenerClasses()
     */
    private void configureListeners(Properties props) {
        // cleanup any possible listeners that were registered already, which will only be the case in tests
        removeAllListeners();

        // grab listener classes from the data source configuration
        Class<? extends ConnectionProviderListener>[] listenerClasses =
            dataSourceConfig.getConnectionProviderListenerClasses();
        if (listenerClasses == null) {
            log.warn("The data source configuration for '{}' should not return a <null> array of listener classes",
                dataSourceId);
        } else {
            int numListeners = 0;
            for (Class<? extends ConnectionProviderListener> listenerClass : listenerClasses) {
                ConnectionProviderListener listener = createListener(props, listenerClass);

                // once the listener has been created and initialized, add it to the proper callback list
                if (listener != null) {
                    addToListenerLists(listener);
                    numListeners++;
                }
            }
            log.trace("Attached {} listeners to connection provider for data source '{}'", numListeners, dataSourceId);
        }
    }

    @Override
    public final void configure(Properties props) throws HibernateException {
        // allow subclass to pre-process if desired
        preConfigure(props);

        // cache JDBC url
        jdbcUrl = props.getProperty(Environment.URL);

        // create the real connection provider instance
        //      restore the real connection provider class name from the delegating property name
        String delegateConnectionProviderClass = props.getProperty(DELEGATING_CONNECTION_PROVIDER_CLASS);
        if (StringUtils.isBlank(delegateConnectionProviderClass)) {
            throw new IllegalStateException("Missing delegating connection provider class name in Hibernate " +
                "property '" + DELEGATING_CONNECTION_PROVIDER_CLASS + "'");
        }
        props.setProperty(Environment.CONNECTION_PROVIDER, delegateConnectionProviderClass);
        realConnectionProvider = ConnectionProviderFactory.newConnectionProvider(props);
        log.trace("Created real connection provider of type {} connected to {}", delegateConnectionProviderClass,
            jdbcUrl);

        // do actual initialization in subclass impl.
        initialize(props);

        // configure our connection provider lists
        configureListeners(props);

        // post-configuration callback
        configured(props);
        log.info("Finished initializing InstrumentedConnectionProvider for data source '{}'", dataSourceId);
    }

    void setDataSourceManager(@Nonnull DataSourceManager dataSourceManager) {
        Preconditions.checkNotNull(dataSourceManager, "dataSourceManager was null");
        Preconditions.checkArgument(dataSourceManager.getId().equals(dataSourceId), "unable to use dataSourceManager " +
            "for '" + dataSourceManager.getId() + "' with a different data source: " + dataSourceId);
        this.dataSourceManager = dataSourceManager;
    }

    /**
     * Invoked immediately prior to the business logic in {@link #configure(Properties)} method.
     */
    protected void preConfigure(Properties props) {
        // no-op
    }

    /**
     * Overrideable callback invoked by {@link #configure(Properties)} so that subclasses can perform their own
     * initialization, but allows this abstract superclass to control the way it is invoked, allowing pre and post
     * processing.
     */
    protected void initialize(Properties props) throws HibernateException {
        // no-op
    }

    /**
     * Callback method invoked at the end of the {@link #configure(Properties)} call so that post-processing can be
     * done.
     * @param props the properties used to configure this connection provider
     */
    protected void configured(Properties props){
        // no-op by default, optional override
    }

    /**
     * Creates a new {@link ConnectionProviderListenerSettings} object that will be passed into listeners being attached
     * to this connection provider.
     */
    protected ConnectionProviderListenerSettings createListenerSettings(Properties hibernateProperties) {
        return new ConnectionProviderListenerSettings(this, hibernateProperties);
    }

    /**
     * Callback that is invoked after prior to calling all post-acquisition callbacks for failures. This provides
     * subclasses the opportunity to either squelch or customize reactions to and respond to exceptions acquiring
     * connections outside of the listeners attached to this provider. </br>
     * After this method returns, the SQLException will be propagated up, unless another {@link SQLException} is
     * returned from this call.
     */
    protected SQLException connectionAcquisitionFailed(@Nonnull SQLException exception) {
        return exception;
    }

    /**
     * Callback that is invoked after acquiring a connection thru {@link #getConnection()} but prior to invoking any of
     * the registered post-acquisition callbacks.
     * @param instrConn the instrumented connection instance
     * @param acquiredConn the exact connection instance that was acquired from the underlying provider
     */
    protected void connectionAcquired(@Nonnull InstrumentedConnection instrConn, @Nonnull Connection acquiredConn) {
        // no-op
    }

    /**
     * Callback that is invoked immediately prior to calling {@link ConnectionProvider#closeConnection(Connection)} for
     * the provided <strong>connection</strong>.
     */
    protected void beforeRelease(@Nonnull InstrumentedConnection instrConn, @Nonnull Connection closingConn) {
        // no-op
    }

    /**
     * Callback that is invoked immediately after calling {@link ConnectionProvider#closeConnection(Connection)} for
     * the provided <strong>connection</strong>.
     */
    protected void connectionReleased(@Nonnull InstrumentedConnection instrConn, @Nonnull Connection closedConn) {
        // no-op
    }

    /**
     * Callback that is invoked prior to performing the actual {@link ConnectionProvider#close()} on the delegate
     * connection provider, allowing a subclass to hook into this event.
     */
    protected void onClose() {
        // no-op
    }

    /**
     * Returns the real connection provider that this instrumented wrapper is delegating to. This may return a value of
     * <code>null</code> if the {@link #configure(Properties)} method has not yet been invoked.
     */
    @Nullable
    public ConnectionProvider getWrappedConnectionProvider() {
        return realConnectionProvider;
    }

    /**
     * Returns the JDBC url that this connection provider will be using for establishing connections.
     */
    @Nonnull
    public final String getJdbcUrl() {
        return jdbcUrl;
    }

    @Nonnull
    @Override
    public final String getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public final DataSourceManager getDataSourceManager() {
        return dataSourceManager;
    }

    @Override
    public final void close() throws HibernateException {
        onClose();
        realConnectionProvider.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return realConnectionProvider.supportsAggressiveRelease();
    }

    @Override
    public final Connection getConnection() throws SQLException {
        Connection acquiredConn;
        try {
            acquiredConn = executeOperation(/*existingConnection=*/null, /*isAcquisition=*/true,
                /*invokeFailureListeners=*/false);
        } catch (SQLException se) {
            // notify subclass of failure prior to invoking failure listeners
            SQLException rethrowException = connectionAcquisitionFailed(se);

            // invoke failure listeners
            invokeListeners(/*isBefore=*/false, /*isAcquisition=*/true, /*exception=*/se, /*connection=*/null);

            // rethrow exception
            if (rethrowException == null) {
                rethrowException = se;
            }
            throw rethrowException;
        }

        // invoke callback for subclass
        afterAcquisition(acquiredConn);

        return acquiredConn;
    }

    @Override
    public final void closeConnection(Connection closedConn) throws SQLException {
        // we must extract the InstrumentedConnection first since it may be unavailable if using a pooled connection
        //      and executeOperation(..) clears the 'inner' connection ref
        InstrumentedConnection instrumentedConn = null;
        if (closedConn instanceof InstrumentedConnection) {
            // we are using the instrumented driver directly underneath this connection provider, without nesting
            instrumentedConn = (InstrumentedConnection) closedConn;
        } else if (closedConn != null) {
            // if using c3p0, or any other nested data source, we want to attempt to resolve the nested connection impl.
            //      that was created by Jdbceptor
            instrumentedConn = dataSourceManager.resolveInstrumentedConnection(closedConn);
        }

        try {
            beforeRelease(instrumentedConn, closedConn);
            executeOperation(closedConn, /*isAcquisition=*/false, /*invokeFailureListeners=*/true);
        } finally {
            // make sure to always call this so book-keeping elsewhere does not have major problems
            afterClose(instrumentedConn, closedConn);
        }
    }

    private void afterAcquisition(Connection acquiredConn) {
        InstrumentedConnection instrumentedConn = dataSourceManager.resolveInstrumentedConnection(acquiredConn,
            /*useDefaultNull=*/true);

        // connection-pool-specific callback
        if (instrumentedConn != null && instrumentedConn instanceof HibernateAwareInstrumentedConnection) {
            ((HibernateAwareInstrumentedConnection) instrumentedConn).acquiredFromConnectionProvider(acquiredConn);
        }

        // invoke callback for subclass
        connectionAcquired(instrumentedConn, acquiredConn);
    }

    private void afterClose(InstrumentedConnection instrumentedConn, Connection closedConn) {
        // connection-pool-specific callback
        if (instrumentedConn != null && instrumentedConn instanceof HibernateAwareInstrumentedConnection) {
            ((HibernateAwareInstrumentedConnection)instrumentedConn).releasedToConnectionProvider();
        }

        // invoke callback for subclass
        connectionReleased(instrumentedConn, closedConn);
    }

    /**
     * Executes the appropriate connection operation depending on the value of <strong>existingConnection</strong>.
     * Whenever a connection is provided, we know that we are definitely going
     * to be performing a close operation, since no connection exists to be passed in when acquiring a connection. As
     * such, if the <strong>existingConnection</strong> is found to be
     * <code>null</code> then we know that we are definitely acquiring/opening a new connection.
     *
     * @param existingConnection the existing connection, if closing a connection, otherwise <code>null</code> for
     *                              opening a connection
     * @param isAcquisition explicit declaration of whether this is an open vs. close (needed for unit testability)
     * @param invokeFailureListeners if true then failure handlers will be invoked automatically if an exception occurs,
     *                                  otherwise if <code>false</code> then the caller of this method is responsible
     *                                  for invoking the failure handlers in all subscribed and enabled interceptors in the chain
     * @return a connection that was successfully acquired, or <code>null</code> if <strong>existingConnection</strong>
     *          was provided and we closed the connection successfully
     * @throws SQLException on any database or connection pooling exceptions
     */
    private Connection executeOperation(Connection existingConnection, boolean isAcquisition,
                                        boolean invokeFailureListeners) throws SQLException {
        // invoke pre-* callbacks
        invokeListeners(/* isBefore=*/ true, /*isAcquisition=*/ isAcquisition, /*exc=*/null,
            /*connection=*/existingConnection);

        // invoke the actual operation
        Connection result;
        try {
            if (isAcquisition) {
                // acquire a connection using the delegate connection provider
                result = realConnectionProvider.getConnection();

                // invoke internal callbacks before registered listeners are called
                afterAcquireBeforeCallbacks(result);
            } else {
                // simply close the connection and return null
                realConnectionProvider.closeConnection(existingConnection);
                result = null;
            }
        } catch (SQLException | RuntimeException e) {
            // we are catching these to make sure we can properly invoke the failure listeners that are attached to this
            //      operation. if we immediately propagated the exception up to the caller, then we would never invoke
            //      these listeners

            // invoke failure callbacks in interceptor(s), but only if the caller is not going to do so themselves
            if (invokeFailureListeners) {
                // make sure to pass in the 'existingConnection' value
                invokeListeners(/* isBefore=*/ false, /*isAcquisition=*/ isAcquisition, /*exc=*/e,
                    /*connection=*/existingConnection);
            }

            // rethrow the exception
            throw e;
        }


        // if we get this far, then we succeeded at applying the operation and must invoke post-operation callbacks
        //          on interceptors
        invokeListeners(/* isBefore=*/ false, /*isAcquisition=*/ isAcquisition, /*exc=*/null, /*connection=*/result);

        // return the result
        return result;
    }

    /**
     * Apply customization logic, if applicable. We must always do this before invoking our listener callbacks since the
     * customizer's actions may be necessary for callbacks to function properly.
     * @param acquiredConn the acquired connection
     */
    private void afterAcquireBeforeCallbacks(Connection acquiredConn) {
        if (acquiredConn == null) {
            return;
        }

        // customize if necessary
        InstrumentedConnection instrumentedConn = dataSourceManager.resolveInstrumentedConnection(acquiredConn,
            /*useDefaultNull=*/true);
        if (instrumentedConn != null) {
            dataSourceManager.customizeConnection(instrumentedConn);
        }
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
            String methodName = "<unknown>";
            try {
                if (isAcquisition) {
                    if (isBefore) {
                        methodName = "beforeConnectionAcquisition";
                        ((PreConnectionAcquisitionListener) listener).beforeConnectionAcquisition(this);
                    } else {
                        PostConnectionAcquisitionListener postListener = (PostConnectionAcquisitionListener) listener;
                        if (exception != null) {
                            methodName = "afterConnectionAcquisitionFailed";
                            postListener.afterConnectionAcquisitionFailed(this, exception);
                        } else {
                            methodName = "afterConnectionAcquired";
                            postListener.afterConnectionAcquired(this, connection);
                        }
                    }
                } else {
                    if (isBefore) {
                        methodName = "beforeClosingConnection";
                        ((PreConnectionCloseListener) listener).beforeClosingConnection(connection);
                    } else {
                        PostConnectionCloseListener postListener = (PostConnectionCloseListener) listener;
                        if (exception != null) {
                            methodName = "afterConnectionClosingFailed";
                            postListener.afterConnectionClosingFailed(connection, exception);
                        } else {
                            methodName = "afterConnectionClosed";
                            postListener.afterConnectionClosed();
                        }
                    }
                }
            } catch (Throwable t) {
                if (log.isErrorEnabled()) {
                    log.error("Unable to invoke {} from data source '{}' for listener of type: {}", methodName,
                        dataSourceId, listener.getClass(), t);
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
     * @param hibernateProps the hibernate properties, which may be used by the listener to check if it is
     *          enabled/disabled, or to extract configurable values
     * @param listenerClazz the listener implementation class that should be used
     * @return the listener instance that was created or <code>null</code> if there are any exceptions constructing or
     *          initializing the new listener
     */
    private ConnectionProviderListener createListener(Properties hibernateProps, Class<? extends ConnectionProviderListener> listenerClazz) {
        // try to locate a constructor that takes ConnectionProviderListenerSettings object first
        Constructor<? extends ConnectionProviderListener> constructor;
        try {
            constructor = listenerClazz.getDeclaredConstructor();

            // make it accessible if it is not already
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
        } catch (NoSuchMethodException nsme) {
            log.error("Unable to attach connection provider listener to data source '{}' because it does not declare " +
                "a zero-argument constructor: {}", dataSourceId, listenerClazz);
            return null;
        } catch (SecurityException se) {
            log.error("Unable to make constructor accessible for listener class '{}' for data source '{}'",
                listenerClazz, dataSourceId, se);
            return null;
        }

        // now that we know it is accessible, instantiate it and attach to this InstrumentedConnectionProvider
        ConnectionProviderListener listener;
        try {
            // instantiate the new listener instance
            listener = constructor.newInstance();
        } catch (IllegalAccessException iae) {
            log.error("Unable to access constructor for listener class '{}' for data source '{}'", listenerClazz,
                dataSourceId, iae);
            return null;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            log.error("Unable to create new instance of '{}' for data source '{}' because constructor threw in an " +
                "exception", listenerClazz, dataSourceId, cause);
            return null;
        } catch (InstantiationException ie) {
            Throwable cause = (ie.getCause() != null ? ie.getCause() : ie);
            log.error("Unable to create new instance of '{}' to attach to data source '{}' due to an unexpected " +
                "exception", listenerClazz, dataSourceId, cause);
            return null;
        }

        // create the listener settings object, which can be done in a subclass and therefore may throw an exception
        ConnectionProviderListenerSettings settings;
        try {
            // pass in the Settings, which a subclass of this provider can override
            settings = createListenerSettings(hibernateProps);
        } catch (Exception e) {
            log.error("Encountered exception while creating listener settings for data source: '{}'", dataSourceId, e);
            return null;
        }

        // finish the initialization and return to caller
        try {
            // inject the parent (this) InstrumentedConnectionProvider into the listener object we just created, but
            //      make sure to do so before we call initialize(..) method
            if (listener instanceof ConnectionProviderAware) {
                ((ConnectionProviderAware) listener).setConnectionProvider(this);
            }

            // allow the listener to initialize itself
            listener.initialize(settings);

            // listener has been fully configured, now return it
            return listener;
        } catch (Exception e) {
            log.error("Unable to initialize listener '{}' for data source '{}'", listenerClazz, dataSourceId, e);
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
        if (listenerMap.containsKey(listenerClass)) {
            throw new IllegalStateException("There is already a listener attached to data source '" + dataSourceId +
                "' with the same implementation class: " + listenerClass);
        }

        // add to the connection provider's map of singleton listeners
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
