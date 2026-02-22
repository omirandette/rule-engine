package com.ruleengine.url;

import java.net.URI;

public final class UrlParser {

    private UrlParser() {}

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

        String query = uri.getQuery();
        if (query == null) {
            query = "";
        }

        return new ParsedUrl(host, path, query);
    }
}
