package com.ruleengine.url;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlParserTest {

    @Test
    void parsesFullUrl() {
        ParsedUrl url = UrlParser.parse("https://example.com/path?key=value");
        assertEquals("example.com", url.host());
        assertEquals("/path", url.path());
        assertEquals("key=value", url.query());
    }

    @Test
    void autoPrependsScheme() {
        ParsedUrl url = UrlParser.parse("example.com/path");
        assertEquals("example.com", url.host());
        assertEquals("/path", url.path());
    }

    @Test
    void lowercasesHost() {
        ParsedUrl url = UrlParser.parse("https://EXAMPLE.COM/Path");
        assertEquals("example.com", url.host());
        assertEquals("/Path", url.path());
    }

    @Test
    void handlesEmptyPath() {
        ParsedUrl url = UrlParser.parse("https://example.com");
        assertEquals("example.com", url.host());
        assertEquals("", url.path());
        assertEquals("", url.file());
    }

    @Test
    void handlesEmptyQuery() {
        ParsedUrl url = UrlParser.parse("https://example.com/path");
        assertEquals("", url.query());
    }

    @Test
    void handlesComplexQuery() {
        ParsedUrl url = UrlParser.parse("https://example.com/search?q=hello&lang=en");
        assertEquals("q=hello&lang=en", url.query());
    }

    @Test
    void throwsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> UrlParser.parse(null));
    }

    @Test
    void throwsOnBlank() {
        assertThrows(IllegalArgumentException.class, () -> UrlParser.parse("  "));
    }

    @Test
    void partAccessorWorks() {
        ParsedUrl url = UrlParser.parse("https://example.com/path?q=1");
        assertEquals("example.com", url.part(UrlPart.HOST));
        assertEquals("/path", url.part(UrlPart.PATH));
        assertEquals("path", url.part(UrlPart.FILE));
        assertEquals("q=1", url.part(UrlPart.QUERY));
    }

    @Test
    void handlesSubdomain() {
        ParsedUrl url = UrlParser.parse("https://www.shop.example.ca/products");
        assertEquals("www.shop.example.ca", url.host());
        assertEquals("/products", url.path());
    }

    @Test
    void extractsFileFromPath() {
        ParsedUrl url = UrlParser.parse("https://example.com/category/sport/items");
        assertEquals("items", url.file());
    }

    @Test
    void fileIsEmptyForTrailingSlash() {
        ParsedUrl url = UrlParser.parse("https://example.com/path/");
        assertEquals("", url.file());
    }

    @Test
    void fileIsEmptyForRootPath() {
        ParsedUrl url = UrlParser.parse("https://example.com/");
        assertEquals("", url.file());
    }

    @Test
    void fileFromSingleSegmentPath() {
        ParsedUrl url = UrlParser.parse("https://example.com/index.html");
        assertEquals("index.html", url.file());
    }
}
