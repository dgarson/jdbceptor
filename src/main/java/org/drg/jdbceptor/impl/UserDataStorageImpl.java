package org.drg.jdbceptor.impl;

import org.drg.jdbceptor.internal.UserDataStorage;

import java.util.HashMap;
import java.util.Map;

/**
 * General base implementation for an object capable of having user-data attached and retrieved from it.
 *
 * @author dgarson
 */
public class UserDataStorageImpl implements UserDataStorage {

    /**
     * Optional map of user data elements. This map is lazily constructed when first storing user data values. </br>
     * <strong>NOTE: </strong> user data stored here will be reset upon logical close of this connection
     */
    protected Map<String, Object> userData;

    @Override
    public synchronized Object getUserData(String key) {
        return (key == null || userData == null ? null : userData.get(key));
    }

    @Override
    public synchronized void setUserData(String key, Object value) {
        if (userData == null) {
            userData = new HashMap<>();
        }
        userData.put(key, value);
    }

    @Override
    public synchronized void removeUserData(String key) {
        if (userData != null) {
            userData.remove(key);
        }
    }

    @Override
    public synchronized void clearUserData() {
        if (userData != null) {
            userData.clear();
        }
    }
}
