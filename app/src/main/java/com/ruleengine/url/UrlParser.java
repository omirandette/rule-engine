package com.ruleengine.url;

/**
 * Parses raw URL strings into {@link ParsedUrl} records.
 *
 * <p>Uses fast {@code indexOf}/{@code substring} parsing instead of {@link java.net.URI}
 * to avoid the overhead of full RFC-compliant URI decomposition. Only the fields needed
 * by the rule engine (host, path, file, query) are extracted.
 *
 * <p>Handles missing schemes, normalizes the host to lowercase, and strips ports.
 */
public final class UrlParser {

    private UrlParser() {}

    /**
     * Parses a raw URL string into its constituent parts.
     *
     * @param raw the URL string to parse (scheme is optional)
     * @return a {@link ParsedUrl} with host, path, file, and query
     * @throws IllegalArgumentException if the input is null, blank, or has no parseable host
     */
    public static ParsedUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("URL must not be null or blank");
        }

        String toParse = raw.strip();

        // Skip past scheme if present; reject empty scheme (e.g. "://bad")
        int hostStart;
        int schemeEnd = toParse.indexOf("://");
        if (schemeEnd > 0) {
            hostStart = schemeEnd + 3;
        } else if (schemeEnd == 0) {
            throw new IllegalArgumentException("Could not parse host from URL: " + raw);
        } else {
            hostStart = 0;
        }

        // Find first '/' and '?' after hostStart to delimit host/path/query
        int pathStart = toParse.indexOf('/', hostStart);
        int queryStart = toParse.indexOf('?', hostStart);

        // Host ends at whichever delimiter comes first, or at end of string
        int hostEnd;
        if (pathStart >= 0 && queryStart >= 0) {
            hostEnd = Math.min(pathStart, queryStart);
        } else if (pathStart >= 0) {
            hostEnd = pathStart;
        } else if (queryStart >= 0) {
            hostEnd = queryStart;
        } else {
            hostEnd = toParse.length();
        }

        // Strip port (e.g. "host:8080" → "host")
        String host = toParse.substring(hostStart, hostEnd);
        int portIndex = host.indexOf(':');
        if (portIndex >= 0) {
            host = host.substring(0, portIndex);
        }

        if (host.isEmpty()) {
            throw new IllegalArgumentException("Could not parse host from URL: " + raw);
        }
        host = host.toLowerCase();

        // Extract path: only if '/' appears before '?' (or there is no '?')
        String path;
        if (pathStart >= 0 && (queryStart < 0 || pathStart < queryStart)) {
            int pathEnd = queryStart >= 0 ? queryStart : toParse.length();
            path = toParse.substring(pathStart, pathEnd);
        } else {
            path = "";
        }

        String file = extractFile(path);

        // Extract query: everything after '?'
        String query;
        if (queryStart >= 0) {
            query = toParse.substring(queryStart + 1);
        } else {
            query = "";
        }

        return new ParsedUrl(host, path, file, query);
    }

    /**
     * Extracts the last segment (file) from a path string.
     * For {@code "/category/sport/items"} returns {@code "items"}.
     * Returns empty string for empty paths or paths ending with {@code /}.
     */
    private static String extractFile(String path) {
        if (path.isEmpty()) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }
}
