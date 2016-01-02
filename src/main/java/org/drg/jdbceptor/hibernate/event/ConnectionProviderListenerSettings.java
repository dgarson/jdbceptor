package org.drg.jdbceptor.hibernate.event;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.drg.jdbceptor.hibernate.MetadataAwareConnectionProvider;

import java.util.Properties;

/**
 * Object that encapsulates settings/referenced objects used by implementations of {@link ConnectionProviderListener}.
 * Having this class allows us to use reflection more safely by expecting interceptors to always take a single
 * constructor argument that is of this type.
 * </br>
 * This class should be subclassed if additional properties are desired by the listener implementations.
 *
 * @author dgarson
 * @created 5/28/15
 */
public class ConnectionProviderListenerSettings {

    private final MetadataAwareConnectionProvider connectionProvider;
    private final Properties hibernateProperties;

    public ConnectionProviderListenerSettings(MetadataAwareConnectionProvider connectionProvider,
                                              Properties hibernateProperties) {
        this.connectionProvider = connectionProvider;
        this.hibernateProperties = hibernateProperties;
    }

    /**
     * Returns the identifier for the database that the parent connection provider has been configured to connect to.
     * @see MetadataAwareConnectionProvider#getDataSourceId()
     */
    public String getDatabaseName() {
        return connectionProvider.getDataSourceId();
    }

    /**
     * Returns the connection provider to which the listener is being attached.
     */
    public MetadataAwareConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    /**
     * Returns the Hibernate properties used to configure the parent ConnectionProvider.
     */
    public Properties getHibernateProperties() {
        return hibernateProperties;
    }

    /**
     * Gets a Hibernate property with an optional default value.
     *
     * @see Properties#getProperty(String, String)
     * @see #getHibernateProperties()
     */
    public String getProperty(String property, String defValue) {
        return hibernateProperties.getProperty(property, defValue);
    }

    /**
     * Gets a Hibernate property for a given name, returning <code>null</code> if that property does not exist.
     *
     * @see Properties#getProperty(String)
     * @see #getHibernateProperties()
     */
    public String getProperty(String property) {
        return hibernateProperties.getProperty(property);
    }

    /**
     * Gets a Hibernate property for a given name and converts it to a boolean value, returning the
     * <strong>defaultValue</strong> if
     * the property is null/empty/unparsable.
     *
     * @param property the property name
     * @param defaultValue the default value to return
     */
    public boolean getBooleanProperty(String property, boolean defaultValue) {
        String value = hibernateProperties.getProperty(property);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return BooleanUtils.toBoolean(value);
    }
}
