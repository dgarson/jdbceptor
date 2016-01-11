package org.drg.jdbceptor.impl;

import org.drg.jdbceptor.api.InstrumentedConnection;
import org.drg.jdbceptor.hibernate.HibernateAwareInstrumentedConnection;
import org.drg.jdbceptor.hibernate.InstrumentedTransaction;

/**
 * Created by dgarson on 1/3/16.
 */
public class StatementHelper {

    static String getTransactionIdOrNull(InstrumentedConnection connection) {
        InstrumentedTransaction transaction = (connection instanceof HibernateAwareInstrumentedConnection ?
            ((HibernateAwareInstrumentedConnection)connection).getCurrentTransaction() : null);
        return (transaction != null ? transaction.getTransactionId() : null);
    }

    static HibernateAwareInstrumentedConnection withHibernateSupportOrNull(InstrumentedConnection connection) {
        return (connection instanceof HibernateAwareInstrumentedConnection ?
            (HibernateAwareInstrumentedConnection)connection : null);
    }

}
