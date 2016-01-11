package org.drg.jdbceptor.hibernate.impl;

import org.drg.jdbceptor.hibernate.InstrumentedJDBCContext;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.jdbc.BatcherFactory;
import org.hibernate.jdbc.ConnectionManager;
import org.hibernate.jdbc.JDBCContext;
import org.hibernate.transaction.TransactionFactory;

import java.sql.Connection;

import javax.annotation.Nonnull;

/**
 * Proxy subclass of the {@link JDBCContext} that allows for a target instance to be used and all methods
 * delegated to it, outside of keeping track of whether a connection has been acquired or is already owned by the
 * associated {@link org.hibernate.jdbc.ConnectionManager}.
 *
 * @author dgarson
 */
public class InstrumentedJDBCContextImpl extends JDBCContext implements InstrumentedJDBCContext {

    private boolean connectionAttached;

    InstrumentedJDBCContextImpl(ProxiedContextImpl ownerProxy, Connection connection, Interceptor interceptor) {
        super(ownerProxy, connection, interceptor);
        this.connectionAttached = (connection != null);
    }

    @Override
    public void connectionOpened() {
        super.connectionOpened();

        // mark as attached
        connectionAttached = true;
    }

    @Override
    public void connectionCleanedUp() {
        super.connectionCleanedUp();

        // mark as detached
        connectionAttached = false;
    }



    @Override
    public Connection getAttachedConnection() {
        return (connectionAttached ? getConnectionManager().getConnection() : null);
    }

    /**
     * Checks whether a <i>real</i> connection is attached to this context and is being managed by the owning
     * {@link #getConnectionManager()} instance.
     * @return true if a connection exists, false otherwise (even if logically open)
     */
    public boolean isConnectionAttached() {
        return connectionAttached;
    }

    /**
     * Creates a proxied &quot;owner&quot; instance of the {@link org.hibernate.jdbc.JDBCContext.Context} interface, so
     * that we can intercept callbacks. This is used to create the first parameter value for the instrumented JDBC
     * context factory method.
     */
    @Nonnull
    public static ProxiedContextImpl createProxyContext(@Nonnull TransactionFactory transactionFactory,
                                                 @Nonnull JDBCContext realContext) {
        return new ProxiedContextImpl(transactionFactory, realContext);
    }

    /**
     * Static factory method that will create {@link InstrumentedJDBCContextImpl} given a proxied Context object along
     * with the real, underlying JDBCContext instance. This requires that the {@link ProxiedBatcherFactoryImpl} be
     * present in the {@link org.hibernate.cfg.Settings} for this factory, which will be done automatically when using
     * the {@link org.drg.jdbceptor.hibernate.InstrumentedHibernateConfiguration} class. </br>
     * We must use a factory method since we cannot put this logic in the constructor due to lack of no-arg constructor
     * in the base implementation.
     * @see #createProxyContext(TransactionFactory, JDBCContext)
     */
    @Nonnull
    public static InstrumentedJDBCContext createInstrumentedContext(@Nonnull JDBCContext.Context owner,
                                                             @Nonnull JDBCContext realContext) {
        if (owner == null || !(owner instanceof ProxiedContextImpl)) {
            throw new IllegalArgumentException("Cannot construct an InstrumentedJDBCContext unless using a proxied " +
                "instance of the JDBCContext.Context object!");
        }
        // create the fake context wrapper
        ProxiedContextImpl proxyOwner = (ProxiedContextImpl)owner;
        Interceptor interceptor = realContext.getFactory().getInterceptor();
        ConnectionManager connManager = realContext.getConnectionManager();
        Connection conn;
        // we must handle the special case where we already have a user-supplied connection AND it is connected
        if (connManager.isSuppliedConnection() && connManager.isCurrentlyConnected()) {
            conn = connManager.getConnection();
        } else {
            conn = null;
        }
        BatcherFactory batcherFactory = realContext.getFactory().getSettings().getBatcherFactory();
        if (!(batcherFactory instanceof ProxiedBatcherFactoryImpl)) {
            throw new IllegalStateException("expected batcherFactory to be an instance of ProxiedBatcherFactoryImpl " +
                "but was of type " + batcherFactory.getClass().getName());
        }
        ProxiedBatcherFactoryImpl proxiedBatcherFactory = (ProxiedBatcherFactoryImpl) batcherFactory;
        // temporarily turn off batcher construction
        InstrumentedJDBCContext wrappedCtx;
        try {
            // turn off createBatcher(..) for a moment
            proxiedBatcherFactory.beforeProxyJdbcContextConstruction();
            wrappedCtx = new InstrumentedJDBCContextImpl(proxyOwner, conn, interceptor);

            // perform any post-configuration
            proxyOwner.finishedConstruction();

            // return the wrapped JDBCContext implementation
            return wrappedCtx;
        } finally {
            // always make sure we restore default behavior of the wrapped BatcherFactory
            proxiedBatcherFactory.afterProxyJdbcContextConstruction();
        }
    }
}
