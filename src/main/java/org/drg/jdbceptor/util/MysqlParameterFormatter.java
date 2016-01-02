package org.drg.jdbceptor.util;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import java.util.Date;

/**
 * Formatting helper methods used when wrapping a MySQL driver and leveraging the parameterized SQL query callbacks.
 *
 * @author dgarson
 */
public class MysqlParameterFormatter {

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

    public static String formatParameter(Object value) {
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