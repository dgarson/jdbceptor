package org.hibernate.cfg;

import org.drg.jdbceptor.hibernate.impl.ProxiedBatcherFactoryImpl;
import org.hibernate.jdbc.BatcherFactory;

/**
 * Class containing helper methods for dealing with {@link Settings} objects. Located in the hibernate package namespace
 * so that package-private setters can be invoked.
 *
 * @author dgarson
 */
public class SettingsHelper {

    public static void setupProxyBatcherFactory(Settings settings) {
        BatcherFactory currentFactory = settings.getBatcherFactory();
        if (!(currentFactory instanceof ProxiedBatcherFactoryImpl)) {
            BatcherFactory proxiedFactory = new ProxiedBatcherFactoryImpl(currentFactory);
            settings.setBatcherFactory(proxiedFactory);
        }
    }

    private SettingsHelper() {
        // no instantiation
    }
}
