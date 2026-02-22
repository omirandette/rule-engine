package com.ruleengine.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AhoCorasickTest {

    @Test
    void findsSinglePattern() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("he", "val");
        ac.build();
        List<String> result = ac.search("she");
        assertTrue(result.contains("val"));
    }

    @Test
    void findsMultiplePatterns() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("he", "v1");
        ac.insert("she", "v2");
        ac.insert("his", "v3");
        ac.insert("hers", "v4");
        ac.build();

        List<String> result = ac.search("shers");
        assertTrue(result.contains("v1"), "should find 'he'");
        assertTrue(result.contains("v2"), "should find 'she'");
        assertTrue(result.contains("v4"), "should find 'hers'");
        assertFalse(result.contains("v3"), "should not find 'his'");
    }

    @Test
    void findsOverlappingPatterns() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("ab", "v1");
        ac.insert("bc", "v2");
        ac.build();
        List<String> result = ac.search("abc");
        assertTrue(result.containsAll(List.of("v1", "v2")));
    }

    @Test
    void noMatchReturnsEmpty() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("xyz", "val");
        ac.build();
        List<String> result = ac.search("abc");
        assertTrue(result.isEmpty());
    }

    @Test
    void throwsIfSearchBeforeBuild() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("test", "val");
        assertThrows(IllegalStateException.class, () -> ac.search("test"));
    }

    @Test
    void throwsIfInsertAfterBuild() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.build();
        assertThrows(IllegalStateException.class, () -> ac.insert("test", "val"));
    }

    @Test
    void emptyPatternMatchesAnyText() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("", "empty");
        ac.build();
        List<String> result = ac.search("anything");
        assertTrue(result.contains("empty"));
    }

    @Test
    void findsPatternAtEnd() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("sport", "val");
        ac.build();
        List<String> result = ac.search("/category/sport");
        assertTrue(result.contains("val"));
    }

    @Test
    void findsPatternInMiddle() {
        AhoCorasick<String> ac = new AhoCorasick<>();
        ac.insert("sport", "val");
        ac.build();
        List<String> result = ac.search("/category/sport/items");
        assertTrue(result.contains("val"));
    }
}
