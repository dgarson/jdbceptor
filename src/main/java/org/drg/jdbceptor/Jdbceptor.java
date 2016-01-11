package org.drg.jdbceptor;

import com.google.common.base.Ticker;
import org.drg.jdbceptor.config.DataSourceConfiguration;
import org.drg.jdbceptor.config.DataSourceType;
import org.drg.jdbceptor.config.JdbceptorConfiguration;
import org.drg.jdbceptor.impl.DataSourceManager;
import org.drg.jdbceptor.impl.DataSourceUtils;
import org.drg.jdbceptor.impl.ProxyDataSourceConfiguration;
import org.drg.jdbceptor.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nonnull;

/**
 * Global constants and helper methods for the Jdbceptor driver.
 *
 * @author dgarson
 */
public class Jdbceptor {

    private static final Logger log = LoggerFactory.getLogger(Jdbceptor.class);

    // start using the default system timer
    private static Ticker ticker = Ticker.systemTicker();

    // global jdbceptor configuration
    private static JdbceptorConfiguration globalConfig = new NoopJdbceptorConfiguration();

    // registry of data source types
    private static final ConcurrentMap<String, DataSourceType> dataSourceTypes = new ConcurrentHashMap<>();

    // registry of data sources by ids
    private static final ConcurrentMap<String, DataSourceConfiguration> dataSourceConfigurations =
        new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, DataSourceManager> dataSourceManagers = new ConcurrentHashMap<>();

    /**
     * Registers a data source configuration that is populated with real data. If any placeholder configurations exist,
     * this will cause them to delegate to the provided real configuration.
     */
    public static DataSourceManager registerDataSourceConfiguration(DataSourceConfiguration dataSourceConfig) {
        String dataSourceId = dataSourceConfig.getId();
        if (dataSourceConfig instanceof ProxyDataSourceConfiguration) {
            throw new IllegalArgumentException("Unable to register a 'real' data source configuration for '" +
                dataSourceId + "' when the provided configuration object is a Proxy instance!");
        }
        DataSourceManager dataSourceManager = dataSourceManagers.get(dataSourceId);
        if (dataSourceManager != null) {
            throw new IllegalStateException("Cannot re-register data source configuration for '" + dataSourceId + "'");
        }
        DataSourceConfiguration registeredConfig = dataSourceConfigurations.get(dataSourceId);
        if (registeredConfig != null && DataSourceUtils.isDataSourceDefined(registeredConfig)) {
            if (dataSourceConfig == registeredConfig) {
                log.warn("Attempted to register the *exact* same configuration reference for data source '{}'",
                    dataSourceId);
            } else {
                throw new IllegalArgumentException("Unable to register a data source '" + dataSourceConfig.getId() +
                    "' that is already registered with a different, non-proxy configuration!");
            }
        }
        DataSourceConfiguration realConfig;
        if (registeredConfig == null) {
            log.warn("Missing data source registration for '{}'", dataSourceId);
            realConfig = dataSourceConfig;
        } else if (registeredConfig instanceof ProxyDataSourceConfiguration) {
            // make sure to inject the real config into the proxy
            ((ProxyDataSourceConfiguration)registeredConfig).setRealConfig(dataSourceConfig);
            realConfig = registeredConfig;
        } else {
            realConfig = dataSourceConfig;
        }

        return createAndRegisterManager(realConfig);
    }

    /**
     * Retrieves the data source manager for a given data source id, constructing it from the configuration object if
     * one exists and is already defined.
     */
    public static DataSourceManager getDataSourceManager(String dataSourceId) {
        DataSourceManager dataSourceManager = dataSourceManagers.get(dataSourceId);
        if (dataSourceManager == null) {
            DataSourceConfiguration config = dataSourceConfigurations.get(dataSourceId);
            if (config == null) {
                throw new IllegalStateException("No data source exists with id '" + dataSourceId + "'");
            } else if ((config instanceof ProxyDataSourceConfiguration)
                && !((ProxyDataSourceConfiguration)config).isDefined()) {
                throw new IllegalStateException("Unable to build a data source manager for a reference data source ('" +
                    dataSourceId + "') that has not yet been officially defined");
            }
            dataSourceManager = createAndRegisterManager(config);
        }
        return dataSourceManager;
    }

    /**
     * Retrieves an existing configuration for the named data source, or creates a new placeholder configuration for it
     * that will be later converted into a real configuration, or have an exception thrown if no real configuration was
     * defined for that data source id.
     * @param dataSourceId the data source identifier
     * @return a non-null configuration object
     */
    public static DataSourceConfiguration getDataSourceConfiguration(String dataSourceId) {
        DataSourceConfiguration config = dataSourceConfigurations.get(dataSourceId);
        if (config == null) {
            ProxyDataSourceConfiguration newConfig = new ProxyDataSourceConfiguration(dataSourceId);
            config = dataSourceConfigurations.putIfAbsent(dataSourceId, newConfig);
            // only non-null if when we called putIfAbsent(..) it already existed
            if (config == null) {
                config = newConfig;
            }
        }
        return config;
    }

    private static DataSourceManager createAndRegisterManager(DataSourceConfiguration realConfig) {
        DataSourceManager newDataSourceManager = new DataSourceManager(realConfig);
        DataSourceManager dataSourceManager = dataSourceManagers.putIfAbsent(realConfig.getId(), newDataSourceManager);
        if (dataSourceManager == null) {
            dataSourceManager = newDataSourceManager;
        }
        return dataSourceManager;
    }

    /**
     * Returns the ticker for Jdbceptor to use when grabbing timestamps. This can be used to mock timestamps for test
     * cases.
     */
    public static @Nonnull Ticker getTicker() {
        return ticker;
    }

    /**
     * Convenience method for returning the value of {@link Ticker#read()} for the current ticker.
     */
    public static long timestampNanos() {
        return ticker.read();
    }

    /**
     * Sets a new ticker to use. If invoked outside of a JUnit, TestNG, or Selenium test case then this method will
     * throw an exception.
     * @throws IllegalArgumentException if <strong>newTicker</strong> is <code>null</code>
     * @throws UnsupportedOperationException if not running in test case
     */
    public static void setTicker(@Nonnull Ticker newTicker) {
        if (newTicker == null) {
            throw new IllegalArgumentException("Cannot specify a null ticker");
        } else if (!ReflectionUtils.isRunningInTest()) {
            throw new UnsupportedOperationException("Unable to set a custom ticker unless in a test case!");
        }
        ticker = newTicker;
    }

    /**
     * Returns the global Jdbceptor configuration instance.
     */
    public static JdbceptorConfiguration getSharedConfig() {
        return globalConfig;
    }

    /**
     * XXX TODO(dgarson) remove this
     * @param globalConfig the new global configuration to inject
     */
    public static void setGlobalConfig(JdbceptorConfiguration globalConfig) {
        Jdbceptor.globalConfig = globalConfig;
    }

    private static class NoopJdbceptorConfiguration implements JdbceptorConfiguration {
        @Override
        public boolean isCaptureQueryParametersEnabled() {
            return false;
        }
    }
}
