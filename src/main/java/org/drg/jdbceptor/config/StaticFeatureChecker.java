package org.drg.jdbceptor.config;

import com.google.common.base.Preconditions;

/**
 * Default implementation of the feature checker that uses constant feature flags such that they will
 * always be enabled/disabled for the entire runtime.
 *
 * @author dgarson
 */
public class StaticFeatureChecker implements FeatureChecker {

    private boolean captureStatementsEnabled;
    private boolean instrumentationEnabled;
    private boolean mutable;

    /**
     * Creates a new static feature checker with predefined enabled/disabled flags for instrumenting connections as well
     * as statements.
     * @param mutable if true then the setters may be used on this checker, otherwise invoking a setter will throw an
     *              {@link IllegalStateException}
     */
    public StaticFeatureChecker(boolean instrumentationEnabled, boolean captureStatementsEnabled,
                                boolean mutable) {
        this.captureStatementsEnabled = captureStatementsEnabled;
        this.instrumentationEnabled = instrumentationEnabled;
        this.mutable = mutable;
    }

    /**
     * Checks whether this feature checker may have its state changed after construction. This is useful for certain
     * situations such as unusual startup sequence or in unit/component tests.
     */
    public boolean isMutable() {
        return mutable;
    }

    @Override
    public boolean shouldInstrumentConnection() {
        return instrumentationEnabled;
    }

    /**
     * Sets whether connection instrumentation is enabled.
     * @throws IllegalStateException if this checker is not mutable
     */
    public void setInstrumentationEnabled(boolean instrumentationEnabled) {
        Preconditions.checkState(mutable, "Cannot modify an immutable feature checker");
        this.instrumentationEnabled = instrumentationEnabled;
    }

    @Override
    public boolean shouldCaptureStatements() {
        return captureStatementsEnabled;
    }

    /**
     * Sets whether statement capturing is enabled.
     * @throws IllegalStateException if this checker is not mutable
     */
    public void setCaptureStatementsEnabled(boolean captureStatementsEnabled) {
        Preconditions.checkState(mutable, "Cannot modify an immutable feature checker");
        this.captureStatementsEnabled = captureStatementsEnabled;
    }
}
