package org.drg.jdbceptor.api;

/**
 * Implementations of this class can be used to identify the &quot;source&quot; of a particular
 * statement execution or, more commonly, acquiring a connection. The default implementation provides
 * stack trace and thread information.
 *
 * @author dgarson
 */
public interface SourceInfo {

    /**
     * Constant source info implementation that returns <code>true</code> when checking whether the source info feature
     * is disabled.
     * @see #isDisabled()
     */
    SourceInfo DISABLED = new SourceInfo() {
        @Override
        public String toReadableString() {
            return "<unavailable>";
        }

        @Override
        public boolean isDisabled() {
            return true;
        }
    };

    /**
     * Returns a human readable String representing this source info.
     */
    String toReadableString();

    /**
     * Checks whether this source info is a placeholder for this feature being disabled. This is
     * usually done to optimize state snapshot construction during instrumentation.
     */
    boolean isDisabled();
}
