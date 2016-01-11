package org.drg.jdbceptor.util;

import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.hibernate.InstrumentedJDBCContext;
import org.hibernate.HibernateException;
import org.hibernate.cfg.Environment;
import org.hibernate.jdbc.ConnectionManager;
import org.hibernate.jdbc.JDBCContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.util.Properties;

/**
 * @author dgarson
 */
public class JdbcUtils {

    private static final String cachedHostname = resolveHostname();

    /**
     * Returns the hostname for this machine.
     */
    public static String getHostname() {
        return cachedHostname;
    }

    private static String resolveHostname() {
        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException uhe) {
            return "localhost";
        }
        return addr.getCanonicalHostName();
    }

    /**
     * Extracts the JDBC url from the associated Hibernate property.
     * @param hibernateProperties the hibernate properties
     * @return the JDBC url
     * @throws HibernateException if the JDBC url property does not exist in <strong>hibernateProperties</strong>
     */
    public static String getJdbcUrl(Properties hibernateProperties) throws HibernateException {
        String jdbcUrlString = hibernateProperties.getProperty(Environment.URL);
        if (StringUtils.isEmpty(jdbcUrlString)) {
            throw new HibernateException(String.format("JDBC URL is not specified in '%s' parameter", Environment.URL));
        }
        return jdbcUrlString;
    }

    /**
     * Extracts the database name from the provided JDBC connection url.
     */
    public static String getDatabaseNameFromJdbcUrl(String jdbcUrlString) {
        // extract database name from the JDBC URL
        int lastSlash = jdbcUrlString.lastIndexOf('/');
        int queryPos = jdbcUrlString.indexOf('?');
        return (queryPos > 0 ? jdbcUrlString.substring(lastSlash + 1, queryPos) : jdbcUrlString.substring(lastSlash + 1));
    }

    /**
     * Returns the current {@link java.sql.Connection} in a connection provider using the ConnectionManager or will
     * return <code>null</code> if there is no attached connection, rather than lazily opening one when this method is
     * called.
     * @param jdbcContext the jdbc context
     */
    public static Connection getConnectionFromJdbcContext(JDBCContext jdbcContext) {
        // return value from getAttachedConnection() if available, this way we can avoid connection allocation sometimes
        if (jdbcContext instanceof InstrumentedJDBCContext) {
            return ((InstrumentedJDBCContext)jdbcContext).getAttachedConnection();
        }
        // otherwise use the ConnectionManager directly...
        ConnectionManager connectionManager = jdbcContext.getConnectionManager();
        return (connectionManager != null && connectionManager.isCurrentlyConnected() ?
            connectionManager.getConnection() : null);
    }

    private JdbcUtils() {
        // no instantiation
    }
}
