package org.drg.jdbceptor.config;

import org.drg.jdbceptor.internal.DataSourceMember;

import javax.annotation.Nonnull;

/**
 * Contract for a generator that can produce identifiers to be assigned to an object instance in Jdbceptor. This can be
 * used for both connection and transaction ID generation.
 *
 * @author dgarson
 */
public interface IdentifierGenerator<T extends DataSourceMember> {

    /**
     * Generates an identifier for a non-null object created and managed by Jdbceptor.
     */
    @Nonnull String generateIdentifier(@Nonnull T owner);
}
