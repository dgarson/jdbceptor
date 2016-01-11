package org.drg.jdbceptor.config;

/**
 * Interface that should be implemented per database server and that formats query parameters from paramaterized
 * statements such that the output matches the query that would be executed manually against the database.
 *
 * @author dgarson
 */
public interface QueryParameterFormatter {

    /**
     * Formats the provided <strong>value</strong>.
     */
    String formatParameter(Object value);
}
