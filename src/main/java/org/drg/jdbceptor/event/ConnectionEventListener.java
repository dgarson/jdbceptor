package org.drg.jdbceptor.event;

import javax.annotation.Nonnull;

/**
 * Interface that must be implemented by any listener subscribing to any connection or statement events.
 */
public interface ConnectionEventListener<E extends ConnectionEvent> {

    /**
     * Handles a single connection event. The source of this event can be any connection for which this listener is
     * subscribed.
     * @param event the event object
     */
    void onEvent(@Nonnull  E event);
}
