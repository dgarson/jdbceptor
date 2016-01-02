package org.drg.jdbceptor.impl;

import java.sql.Statement;

/**
 * Base statement implementation which is used for executing/working directly with SQL statements/queries instead of
 * using the &quot;java.sql&quot; prepared or callable statements.
 *
 * @author dgarson
 */
public class InstrumentedStatementImpl extends AbstractInstrumentedStatement<Statement> implements Statement {

    public InstrumentedStatementImpl(InstrumentedConnectionImpl connection, Statement statement, int statementId) {
        super(connection, statement, statementId);
    }

    @Override
    protected String getFormattedSql() {
        return getCachedSql();
    }
}

