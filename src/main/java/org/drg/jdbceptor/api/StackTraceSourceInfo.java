package org.drg.jdbceptor.api;

import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation of the SourceInfo interface which provides stack trace and thread information
 * for the moment when this object is constructed.
 *
 * @author dgarson
 */
public class StackTraceSourceInfo implements SourceInfo {

    private final StackTraceElement[] stackTraceElements;
    private final String threadName;
    private final long threadId;

    public StackTraceSourceInfo() {
        Thread thread = Thread.currentThread();
        stackTraceElements = thread.getStackTrace();
        threadName = thread.getName();
        threadId = thread.getId();
    }

    public StackTraceElement[] getStackTraceElements() {
        return stackTraceElements;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    @Override
    public String toReadableString() {
        return "Thread#" + threadId + " [" + threadName + "]\n" +
            StringUtils.join(stackTraceElements, "\n\tat ");
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public String toString() {
        return toReadableString();
    }
}
