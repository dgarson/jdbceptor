package org.drg.jdbceptor.impl;

import java.sql.PreparedStatement;

/**
 * @author dgarson
 */
public class InstrumentedPreparedStatement extends AbstractParameterizedInstrumentedStatement<PreparedStatement>
{

    public InstrumentedPreparedStatement(InstrumentedConnectionImpl connection, PreparedStatement statement,
                                         int statementId, String sql, boolean captureQueryParameters) {
        super(connection, statement, statementId, sql, captureQueryParameters);
    }
}
