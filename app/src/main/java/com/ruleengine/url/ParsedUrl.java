package com.ruleengine.url;

/**
 * Immutable representation of a parsed URL, decomposed into its constituent parts.
 *
 * @param host  the hostname, normalized to lowercase (e.g. {@code "example.com"})
 * @param path  the full path (e.g. {@code "/category/sport/items"})
 * @param file  the last segment of the path (e.g. {@code "items"}), empty string if none
 * @param query the query string without the leading {@code ?}, empty string if absent
 */
public record ParsedUrl(String host, String path, String file, String query) {

    /**
     * Returns the value of the specified URL part.
     *
     * @param urlPart the part to retrieve
     * @return the string value of that part
     */
    public String part(UrlPart urlPart) {
        return switch (urlPart) {
            case HOST -> host;
            case PATH -> path;
            case FILE -> file;
            case QUERY -> query;
        };
    }
}
