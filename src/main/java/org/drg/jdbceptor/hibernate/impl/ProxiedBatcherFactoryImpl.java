package org.drg.jdbceptor.hibernate.impl;

import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.ScrollMode;
import org.hibernate.dialect.Dialect;
import org.hibernate.jdbc.Batcher;
import org.hibernate.jdbc.BatcherFactory;
import org.hibernate.jdbc.ConnectionManager;
import org.hibernate.jdbc.Expectation;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by dgarson on 1/4/16.
 */
public class ProxiedBatcherFactoryImpl implements BatcherFactory {

    private static final Batcher NULL_BATCHER = new NullBatcherImpl();

    private final ThreadLocal<Boolean> createNullBatchers = new ThreadLocal<>();

    private final BatcherFactory realFactory;

    public ProxiedBatcherFactoryImpl(BatcherFactory realFactory) {
        this.realFactory = realFactory;
    }

    void beforeProxyJdbcContextConstruction() {
        createNullBatchers.set(Boolean.TRUE);
    }

    void afterProxyJdbcContextConstruction() {
        createNullBatchers.set(Boolean.FALSE);
    }

    public BatcherFactory getRealFactory() {
        return realFactory;
    }

    @Override
    public Batcher createBatcher(ConnectionManager connectionManager, Interceptor interceptor) {
        if (createNullBatchers.get()) {
            // can be a singleton since it does not change behavior between threads
            return NULL_BATCHER;
        } else {
            return realFactory.createBatcher(connectionManager, interceptor);
        }
    }

    private static class NullBatcherImpl implements Batcher {

        @Override
        public PreparedStatement prepareQueryStatement(String sql, boolean scrollable,
                                                       ScrollMode scrollMode) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public void closeQueryStatement(PreparedStatement ps, ResultSet rs) throws SQLException {
        }

        @Override
        public CallableStatement prepareCallableQueryStatement(String sql, boolean scrollable,
                                                       ScrollMode scrollMode) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public PreparedStatement prepareSelectStatement(String sql) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql,
                                                  boolean useGetGeneratedKeys) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql,
                                                  String[] columnNames) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public CallableStatement prepareCallableStatement(String sql) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public void closeStatement(PreparedStatement ps) throws SQLException {
        }

        @Override
        public PreparedStatement prepareBatchStatement(String sql) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public CallableStatement prepareBatchCallableStatement(String sql) throws SQLException, HibernateException {
            return null;
        }

        @Override
        public void addToBatch(Expectation expectation) throws SQLException, HibernateException {
        }

        @Override
        public void executeBatch() throws HibernateException {
        }

        @Override
        public void closeStatements() {}

        @Override
        public ResultSet getResultSet(PreparedStatement ps) throws SQLException {
            return null;
        }

        @Override
        public ResultSet getResultSet(CallableStatement ps, Dialect dialect) throws SQLException {
            return null;
        }

        @Override
        public void abortBatch(SQLException sqle) {}

        @Override
        public void cancelLastQuery() throws HibernateException {}

        @Override
        public boolean hasOpenResources() {
            return false;
        }

        @Override
        public String openResourceStatsAsString() {
            return null;
        }

        @Override
        public Connection openConnection() throws HibernateException {
            return null;
        }

        @Override
        public void closeConnection(Connection conn) throws HibernateException {}

        @Override
        public void setTransactionTimeout(int seconds) {}

        @Override
        public void unsetTransactionTimeout() {}
    }

}
