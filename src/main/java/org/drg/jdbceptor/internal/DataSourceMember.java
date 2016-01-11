package org.drg.jdbceptor.internal;

import org.drg.jdbceptor.impl.DataSourceManager;

import javax.annotation.Nonnull;

/**
 * Internal contract for beans that are associated with a singular data source and are capable of returning a reference
 * to the data source registration.
 *
 * @author dgarson
 */
public interface DataSourceMember {

    /**
     * Returns the identifier for the owning data source.
     */
    @Nonnull String getDataSourceId();

    /**
     * Returns the manager for the data source with which this member is associated.
     */
    @Nonnull DataSourceManager getDataSourceManager();
}
