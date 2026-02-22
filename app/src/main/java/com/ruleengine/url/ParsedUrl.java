package com.ruleengine.url;

public record ParsedUrl(String host, String path, String query) {

    public String part(UrlPart urlPart) {
        return switch (urlPart) {
            case HOST -> host;
            case PATH -> path;
            case QUERY -> query;
        };
    }
}
