package com.ruleengine.url;

import java.net.URI;

/**
 * Parses raw URL strings into {@link ParsedUrl} records.
 *
 * <p>Handles missing schemes by auto-prepending {@code http://} and
 * normalizes the host to lowercase. The path and query are kept as-is.
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
        // Auto-prepend scheme if missing so URI can parse host/path correctly
        if (!toParse.contains("://")) {
            toParse = "http://" + toParse;
        }

        URI uri = URI.create(toParse);

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Could not parse host from URL: " + raw);
        }
        host = host.toLowerCase();

        String path = uri.getPath();
        if (path == null) {
            path = "";
        }

        String file = extractFile(path);

        String query = uri.getQuery();
        if (query == null) {
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
