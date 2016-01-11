package org.drg.jdbceptor.hibernate.impl;

import org.hibernate.ConnectionReleaseMode;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.jdbc.JDBCContext;
import org.hibernate.transaction.TransactionFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;

/**
 * Internal, proxy implementation of the JDBCContext.Context interface so that we can construct a proxy JDBCContext with
 * a delegate without having to create an actual {@link org.hibernate.jdbc.Batcher} which would otherwise be constructed
 * in {@link JDBCContext#JDBCContext(JDBCContext.Context, Connection, Interceptor)}.
 *
 * @author dgarson
 */
class ProxiedContextImpl implements JDBCContext.Context {

    private final SessionFactoryImplementor sessionFactory;
    private final TransactionFactory transactionFactory;

    // reference to the real JDBCContext.Context object, which is a SessionImpl or StatelessSessionImpl
    private final JDBCContext.Context realContext;

    // whether we should be delegating yet
    private boolean delegating;

    public ProxiedContextImpl(TransactionFactory transactionFactory, JDBCContext jdbcContext) {
        this.transactionFactory = transactionFactory;
        this.sessionFactory = jdbcContext.getFactory();

        // grab the real instance so we can delegate after invoking 'restoreBehavior()'
        this.realContext = (JDBCContext.Context)sessionFactory.getCurrentSession();
    }

    /**
     * Restores the normal behavior of this proxied context implementation, causing it to delegate all of its method
     * calls to the underlying {@link #realContext} reference.
     */
    void finishedConstruction() {
        delegating = true;
    }

    @Override
    public void afterTransactionBegin(Transaction tx) {
        if (delegating) {
            realContext.afterTransactionBegin(tx);
        }
    }

    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        if (delegating) {
            realContext.beforeTransactionCompletion(tx);
        }
    }

    @Override
    public void afterTransactionCompletion(boolean success, Transaction tx) {
        if (delegating) {
            realContext.afterTransactionCompletion(success, tx);
        }
    }

    @Override
    public ConnectionReleaseMode getConnectionReleaseMode() {
        if (delegating) {
            return realContext.getConnectionReleaseMode();
        } else {
            // use default here since it should be ignored anyway
            return transactionFactory.getDefaultReleaseMode();
        }
    }

    @Override
    public boolean isAutoCloseSessionEnabled() {
        if (delegating) {
            return realContext.isAutoCloseSessionEnabled();
        } else {
            // XXX TODO(dgarson) what should this return? should be no-op either way...
            return true;
        }
    }

    @Override
    public SessionFactoryImplementor getFactory() {
        return sessionFactory;
    }

    @Override
    public boolean isClosed() {
        if (delegating) {
            return realContext.isClosed();
        } else {
            // we can never be closed prior to being flipped to delegating to the real context
            return false;
        }
    }

    @Override
    public boolean isFlushModeNever() {
        if (delegating) {
            return realContext.isFlushModeNever();
        } else {
            // default to false?
            return false;
        }
    }

    @Override
    public boolean isFlushBeforeCompletionEnabled() {
        if (delegating) {
            return realContext.isFlushBeforeCompletionEnabled();
        } else {
            // default to true?
            return true;
        }
    }

    @Override
    public void managedFlush() {
        if (delegating) {
            realContext.managedFlush();
        }
    }

    @Override
    public boolean shouldAutoClose() {
        if (delegating) {
            return realContext.shouldAutoClose();
        } else {
            // default to false?
            return false;
        }
    }

    @Override
    public void managedClose() {
        if (delegating) {
            realContext.managedClose();
        }
    }

    private static class ContextWrappingInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return null;
        }
    }
}
