package org.drg.jdbceptor.event;

import org.drg.jdbceptor.config.DataSourceConfiguration;

/**
 * Standard implementation providing elements for:
 * <ul>
 *     <li>connection opened/acquired</li>
 *     <li>connection closed/released</li>
 *     <li>prior to statement execution</li>
 *     <li>after statement execution</li>
 * </ul>
 *
 * @author dgarson
 */
public enum ConnectionEventType implements EventType {
    CONNECTION_OPENED("Connection Opened", "Connection Acquired"),
    CONNECTION_CLOSED("Connection Closed", "Connection Released"),
    STATEMENT_EXECUTING("Executing Statement"),
    STATEMENT_EXECUTED("Executed Statement"),
    //
    ;

    private final String standardName;
    private final String poolingName;

    ConnectionEventType(String standardName, String poolingName) {
        this.standardName = standardName;
        this.poolingName = poolingName;
    }

    ConnectionEventType(String standardName) {
        this(standardName, standardName);
    }

    @Override
    public String getName(DataSourceConfiguration dataSourceReg) {
        return (dataSourceReg.isPoolingConnections() ? poolingName : standardName);
    }
}
