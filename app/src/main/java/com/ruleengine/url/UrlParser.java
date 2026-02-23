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

    private static final String SCHEME_SEPARATOR = "://";

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
        int hostStart = findHostStart(toParse, raw);

        // Find first '/' and '?' after hostStart to delimit host/path/query
        int pathStart = toParse.indexOf('/', hostStart);
        int queryStart = toParse.indexOf('?', hostStart);

        String host = extractHost(toParse, raw, hostStart, pathStart, queryStart);
        String path = extractPath(toParse, pathStart, queryStart);
        String file = extractFile(path);
        String query = extractQuery(toParse, queryStart);

        return new ParsedUrl(host, path, file, query);
    }

    /**
     * Finds the start index of the host by skipping past the scheme separator.
     * Returns 0 if no scheme is present.
     */
    private static int findHostStart(String toParse, String raw) {
        int schemeEnd = toParse.indexOf(SCHEME_SEPARATOR);
        if (schemeEnd > 0) {
            return schemeEnd + SCHEME_SEPARATOR.length();
        }
        if (schemeEnd == 0) {
            throw new IllegalArgumentException("Could not parse host from URL: " + raw);
        }
        return 0;
    }

    /**
     * Extracts and normalizes the host: finds the boundary, strips port, lowercases.
     */
    private static String extractHost(
            String toParse, String raw, int hostStart, int pathStart, int queryStart) {
        int hostEnd = firstDelimiterOrEnd(toParse, pathStart, queryStart);
        String host = toParse.substring(hostStart, hostEnd);

        // Strip port (e.g. "host:8080" → "host")
        int portIndex = host.indexOf(':');
        if (portIndex >= 0) {
            host = host.substring(0, portIndex);
        }

        if (host.isEmpty()) {
            throw new IllegalArgumentException("Could not parse host from URL: " + raw);
        }
        return host.toLowerCase();
    }

    /** Returns the earliest non-negative delimiter position, or end of string. */
    private static int firstDelimiterOrEnd(String toParse, int pathStart, int queryStart) {
        if (pathStart >= 0 && queryStart >= 0) {
            return Math.min(pathStart, queryStart);
        }
        if (pathStart >= 0) {
            return pathStart;
        }
        if (queryStart >= 0) {
            return queryStart;
        }
        return toParse.length();
    }

    /**
     * Extracts the path segment: present only if '/' appears before '?' (or there is no '?').
     */
    private static String extractPath(String toParse, int pathStart, int queryStart) {
        if (pathStart >= 0 && (queryStart < 0 || pathStart < queryStart)) {
            int pathEnd = queryStart >= 0 ? queryStart : toParse.length();
            return toParse.substring(pathStart, pathEnd);
        }
        return "";
    }

    /** Extracts the query string (everything after '?'), or empty if absent. */
    private static String extractQuery(String toParse, int queryStart) {
        return queryStart >= 0 ? toParse.substring(queryStart + 1) : "";
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
