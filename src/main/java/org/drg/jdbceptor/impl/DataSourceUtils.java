package org.drg.jdbceptor.impl;

import org.drg.jdbceptor.config.DataSourceConfiguration;

/**
 * Internal helper methods used for managing DataSourceConfigurations and InstrumentedConnections.
 *
 * @author dgarson
 */
public class DataSourceUtils {

    /**
     * Checks if a data source is &quot;defined&quot;. In this context, that means whether it is a proxy instance
     * <strong>AND</strong> it has not yet been populated with a real DataSourceConfiguration.
     * @see ProxyDataSourceConfiguration#setRealConfig(DataSourceConfiguration)
     */
    public static boolean isDataSourceDefined(DataSourceConfiguration config) {
        if (config == null) {
            return false;
        } else if (config instanceof ProxyDataSourceConfiguration) {
            return ((ProxyDataSourceConfiguration)config).isDefined();
        } else {
            // it exists and is not a proxy so it definitely is defined
            return true;
        }
    }

    private DataSourceUtils() {
        // no instantiation
    }
}
