package com.ruleengine.url;

/**
 * Represents the decomposed parts of a URL that conditions can target.
 *
 * <p>Given a URL like {@code http://host/path/file?query}, the parts are:
 * <ul>
 *   <li>{@link #HOST} — the hostname (e.g. {@code example.com})</li>
 *   <li>{@link #PATH} — the full path including file (e.g. {@code /path/file})</li>
 *   <li>{@link #FILE} — the last segment of the path (e.g. {@code file})</li>
 *   <li>{@link #QUERY} — the query string without the leading {@code ?} (e.g. {@code key=value})</li>
 * </ul>
 */
public enum UrlPart {
    HOST,
    PATH,
    FILE,
    QUERY
}
