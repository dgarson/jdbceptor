package org.drg.jdbceptor.internal;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Interface that applies to any instrumented type that can store and look up values by a unique key. Usually
 * this is used for properties that might relate to a particular Connection or Statement and would be pushed
 * and retrieved from user code. </br>
 * <strong>NOTE: </strong> methods in this class must be thread-safe and will often be synchronized on, so make sure not
 * to use highly concurrent access to the user data.
 */
@ThreadSafe
public interface UserDataStorage {

    /**
     * Retrieves a value with the given key, or <code>null</code> if the key does not exist.
     */
    Object getUserData(String key);

    /**
     * Sets the given user data <strong>value</strong> associated with a specified <strong>key</strong>.
     * If any other value exists with this key then it will be replaced.
     */
    void setUserData(String key, Object value);

    /**
     * Removes a user data element with a given <strong>key</strong>
     */
    void removeUserData(String key);

    /**
     * Clears all user data from this object.
     */
    void clearUserData();
}
