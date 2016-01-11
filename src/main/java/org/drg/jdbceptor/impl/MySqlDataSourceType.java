package org.drg.jdbceptor.impl;

import com.mysql.jdbc.ConnectionImpl;
import com.mysql.jdbc.JDBC4Connection;
import com.mysql.jdbc.MySQLConnection;
import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.config.DataSourceType;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import java.sql.Connection;
import java.util.Date;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Built-in data source type implementation for a MySQL backend, providing parameter formatting and identifier
 * generation/retrieval.
 *
 * @author dgarson
 */
public class MySqlDataSourceType implements DataSourceType {

    private static final DateTimeFormatter SQL_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendHourOfDay(2).appendLiteral(':').appendMinuteOfHour(2).appendLiteral(':').appendSecondOfMinute(2)
        .toFormatter();

    private static final DateTimeFormatter SQL_DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendYear(4, 4).appendLiteral('-').appendMonthOfYear(2).appendLiteral('-').appendDayOfMonth(2)
        .toFormatter();

    private static final DateTimeFormatter JAVA_DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendYear(4, 4).appendLiteral('-').appendMonthOfYear(2).appendLiteral('-').appendDayOfMonth(2)
        .appendLiteral(' ').appendHourOfDay(2).appendLiteral(':').appendMinuteOfHour(2).appendLiteral(':')
        .appendSecondOfMinute(2).toFormatter();

    @Nonnull
    @Override
    public String getName() {
        return "MySQL";
    }

    @Nullable
    @Override
    public String formatParameter(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + escapeSqlString((String)value) + "'";
        } else if (value instanceof Date) {
            return "'" + JAVA_DATE_FORMATTER.print(((Date)value).getTime()) + "'";
        } else if (value instanceof java.sql.Date) {
            return "'" + SQL_DATE_FORMATTER.print(((java.sql.Date)value).getTime()) + "'";
        } else if (value instanceof java.sql.Time) {
            return "'" + SQL_TIME_FORMATTER.print(((java.sql.Time) value).getTime()) + "'";
        } else if (value instanceof Boolean) {
            // bit value for on/off
            return ((Boolean)value).booleanValue() ? "1" : "0";
        } else {
            return value.toString();
        }
    }

    @Nonnull
    @Override
    public String generateIdentifier(InstrumentedConnection connection) {
        Connection realConn = connection.getRealConnection();
        if (realConn instanceof MySQLConnection) {
            return "mysql-" + ((MySQLConnection) realConn).getId();
        } else {
            throw new IllegalArgumentException("Expected wrapped connection to be a MySQLConnection but found " +
                realConn.getClass() + " instead");
        }
    }

    protected static String escapeSqlString(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, j = str.length(); i < j; i++) {
            char c = str.charAt(i);
            if (c == '\'') {
                sb.append(c);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
