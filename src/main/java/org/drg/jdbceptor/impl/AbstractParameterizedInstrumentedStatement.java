package org.drg.jdbceptor.impl;

import org.drg.jdbceptor.api.InstrumentedConnection;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author dgarson
 */
class AbstractParameterizedInstrumentedStatement<T extends PreparedStatement>
    extends AbstractInstrumentedStatement<T> implements PreparedStatement {

    private final String sql;
    private List<Object> paramList;

    // determines whether we want to capture full SQL queries or just gather execution times and support listeners
    protected final boolean captureQueryParameters;

    private String formattedSql;

    public AbstractParameterizedInstrumentedStatement(InstrumentedConnection connection, T statement,
                                                      int statementId, String sql, boolean captureQueryParameters) {
        super(connection, statement, statementId);
        this.captureQueryParameters = captureQueryParameters;
        this.sql = sql;
    }

    @Override
    public void close() throws SQLException {
        // some explicit clean-up for minor optimization
        paramList = null;
        formattedSql = null;

        super.close();
    }

    @Override
    protected String getFormattedSql() {
        if (formattedSql == null) {
            // if there are no parameters or we are not enabling query parameter capture, then return the raw SQL
            //      prior to parameter substitution
            if (paramList == null || !captureQueryParameters) {
                return sql;
            } else {
                formattedSql = formatSqlQuery();
            }
        }
        return formattedSql;
    }

    /**
     * Formats the parameters in this prepared statement such that the exact (raw) SQL can be captured rather than
     * seeing placeholders for indexed parameter values.
     */
    protected String formatSqlQuery() {
        StringBuilder formattedSql = new StringBuilder(sql.length() * 2);
        int lastPos = 0;
        int qpos = sql.indexOf('?', lastPos);
        int argIdx = 0;
        String argValue;
        while (qpos >= 0) {
            try {
                argValue = connection.getDataSourceManager().formatParameterValue(paramList.get(argIdx));
            } catch (IndexOutOfBoundsException ioobe) {
                argValue = "?";
            }

            argIdx++;
            formattedSql.append(sql.substring(lastPos, qpos));
            lastPos = qpos + 1;
            qpos = sql.indexOf('?', lastPos);
            formattedSql.append(argValue);
        }
        if (lastPos < sql.length()) {
            formattedSql.append(sql.substring(lastPos, sql.length()));
        }
        return formattedSql.toString();
    }

    @Override
    public boolean execute() throws SQLException {
        String sql = getFormattedSql();
        reportBeginExecution("execute", sql);
        try {
            boolean result = statement.execute();
            reportStatementCompletion("execute", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("execute", sql, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        String sql = getFormattedSql();
        reportBeginExecution("executeUpdate", sql);
        try {
            int result = statement.executeUpdate();
            reportStatementCompletion("executeUpdate", sql, /*exception=*/null);
            return result;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeUpdate", sql, e);
            throw e;
        }
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        String sql = getFormattedSql();
        reportBeginExecution("executeQuery", sql);
        try {
            ResultSet results = statement.executeQuery();
            reportStatementCompletion("executeQuery", sql, /*exception=*/null);
            return results;
        } catch (SQLException | RuntimeException e) {
            reportStatementCompletion("executeQuery", sql, e);
            throw e;
        }
    }

    protected void trackArgument(int paramIndex, Object value) {
        // do nothing if we are not capturing query parameter values
        if (!captureQueryParameters) {
            return;
        }
        if (paramList == null) {
            paramList = new ArrayList<>();
        }
        // if an object is being inserted out of sequence, fill up missing values with null
        int ix = paramIndex - 1;
        while (ix >= paramList.size()) {
            paramList.add(paramList.size(), null);
        }
        paramList.set(ix, value);
    }

    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        trackArgument(parameterIndex, "<NClob>");
        statement.setNClob(parameterIndex, reader);
    }

    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        trackArgument(parameterIndex, null);
        statement.setNull(parameterIndex, sqlType);
    }

    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setBoolean(parameterIndex, x);
    }

    public void setByte(int parameterIndex, byte x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setByte(parameterIndex, x);
    }

    public void setShort(int parameterIndex, short x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setShort(parameterIndex, x);
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setInt(parameterIndex, x);
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setLong(parameterIndex, x);
    }

    public void setFloat(int parameterIndex, float x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setFloat(parameterIndex, x);
    }

    public void setDouble(int parameterIndex, double x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setDouble(parameterIndex, x);
    }

    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setBigDecimal(parameterIndex, x);
    }

    public void setString(int parameterIndex, String x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setString(parameterIndex, x);
    }

    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        trackArgument(parameterIndex, "byte[" + x.length + "]");
        statement.setBytes(parameterIndex, x);
    }

    public void setDate(int parameterIndex, Date x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setDate(parameterIndex, x);
    }

    public void setTime(int parameterIndex, Time x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setTime(parameterIndex, x);
    }

    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setTimestamp(parameterIndex, x);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        trackArgument(parameterIndex, "<AsciiStream>");
        statement.setAsciiStream(parameterIndex, x, length);
    }

    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        trackArgument(parameterIndex, "<UnicodeStream>");
        statement.setUnicodeStream(parameterIndex, x, length);
    }

    public void setBinaryStream(int parameterIndex,
                                InputStream x, int length) throws SQLException {
        trackArgument(parameterIndex, "<BinaryStream>");
        statement.setBinaryStream(parameterIndex, x, length);
    }

    public void clearParameters() throws SQLException {
        paramList = null;
        statement.clearParameters();
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setObject(parameterIndex, x, targetSqlType);
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setObject(parameterIndex, x);
    }

    public void addBatch() throws SQLException {
        // add statement to underlying Statement batch list first in case that fails
        statement.addBatch();

        if (batchStatementList == null) {
            batchStatementList = new ArrayList<>();
        }
        batchStatementList.add(formatSqlQuery());
    }

    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        trackArgument(parameterIndex, "<CharStream>");
        statement.setCharacterStream(parameterIndex, reader, length);
    }

    public void setRef(int parameterIndex, Ref x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setRef(parameterIndex, x);
    }

    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        trackArgument(parameterIndex, "<Blob>");
        statement.setBlob(parameterIndex, x);
    }

    public void setClob(int parameterIndex, Clob x) throws SQLException {
        trackArgument(parameterIndex, "<Clob>");
        statement.setClob(parameterIndex, x);
    }

    public void setArray(int parameterIndex, Array x) throws SQLException {
        trackArgument(parameterIndex, "<Array>");
        statement.setArray(parameterIndex, x);
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        return statement.getMetaData();
    }

    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setDate(parameterIndex, x, cal);
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setTime(parameterIndex, x, cal);
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setTimestamp(parameterIndex, x, cal);
    }

    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        trackArgument(parameterIndex, null);
        statement.setNull(parameterIndex, sqlType, typeName);
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setURL(parameterIndex, x);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        return statement.getParameterMetaData();
    }

    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        statement.setRowId(parameterIndex, x);
    }

    public void setNString(int parameterIndex, String value) throws SQLException {
        trackArgument(parameterIndex, value);
        statement.setNString(parameterIndex, value);
    }

    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        trackArgument(parameterIndex, "<Reader>");
        statement.setNCharacterStream(parameterIndex, value, length);
    }

    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        trackArgument(parameterIndex, "<NClob>");
        statement.setNClob(parameterIndex, value);
    }

    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        trackArgument(parameterIndex, "<Reader>");
        statement.setClob(parameterIndex, reader, length);
    }

    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        trackArgument(parameterIndex, "<Blob>");
        statement.setBlob(parameterIndex, inputStream, length);
    }

    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        trackArgument(parameterIndex, "<NClob>");
        statement.setNClob(parameterIndex, reader, length);
    }

    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        trackArgument(parameterIndex, "<SQLXML>");
        statement.setSQLXML(parameterIndex, xmlObject);
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        trackArgument(parameterIndex, x);
        statement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        trackArgument(parameterIndex, "<AsciiStream>");
        statement.setAsciiStream(parameterIndex, x, length);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        trackArgument(parameterIndex, "<BinaryStream>");
        statement.setBinaryStream(parameterIndex, x, length);
    }

    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        trackArgument(parameterIndex, "<CharStream>");
        statement.setCharacterStream(parameterIndex, reader, length);
    }

    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        trackArgument(parameterIndex, "<AsciiStream>");
        statement.setAsciiStream(parameterIndex, x);
    }

    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        trackArgument(parameterIndex, "<BinaryStream>");
        statement.setBinaryStream(parameterIndex, x);
    }

    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        trackArgument(parameterIndex, "<CharStream>");
        statement.setCharacterStream(parameterIndex, reader);
    }

    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        trackArgument(parameterIndex, "<NCharacterStream>");
        statement.setNCharacterStream(parameterIndex, value);
    }

    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        trackArgument(parameterIndex, "<Clob>");
        statement.setClob(parameterIndex, reader);
    }

    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        trackArgument(parameterIndex, "<Blob>");
        statement.setBlob(parameterIndex, inputStream);
    }
}