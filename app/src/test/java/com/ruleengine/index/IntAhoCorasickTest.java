package com.ruleengine.index;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntAhoCorasickTest {

    private List<Integer> search(IntAhoCorasick ac, String text) {
        List<Integer> result = new ArrayList<>();
        ac.search(text, result::add);
        return result;
    }

    @Test
    void findsSinglePattern() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("he", 1);
        ac.build();
        assertTrue(search(ac, "she").contains(1));
    }

    @Test
    void findsMultiplePatterns() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("he", 1);
        ac.insert("she", 2);
        ac.insert("his", 3);
        ac.insert("hers", 4);
        ac.build();

        List<Integer> result = search(ac, "shers");
        assertTrue(result.contains(1), "should find 'he'");
        assertTrue(result.contains(2), "should find 'she'");
        assertTrue(result.contains(4), "should find 'hers'");
        assertFalse(result.contains(3), "should not find 'his'");
    }

    @Test
    void findsOverlappingPatterns() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("ab", 1);
        ac.insert("bc", 2);
        ac.build();
        List<Integer> result = search(ac, "abc");
        assertTrue(result.containsAll(List.of(1, 2)));
    }

    @Test
    void noMatchReturnsEmpty() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("xyz", 1);
        ac.build();
        assertTrue(search(ac, "abc").isEmpty());
    }

    @Test
    void throwsIfSearchBeforeBuild() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("test", 1);
        assertThrows(IllegalStateException.class, () -> ac.search("test", _ -> {}));
    }

    @Test
    void throwsIfInsertAfterBuild() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.build();
        assertThrows(IllegalStateException.class, () -> ac.insert("test", 1));
    }

    @Test
    void emptyPatternMatchesAnyText() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("", 42);
        ac.build();
        assertTrue(search(ac, "anything").contains(42));
    }

    @Test
    void findsPatternAtEnd() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("sport", 1);
        ac.build();
        assertTrue(search(ac, "/category/sport").contains(1));
    }

    @Test
    void findsPatternInMiddle() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("sport", 1);
        ac.build();
        assertTrue(search(ac, "/category/sport/items").contains(1));
    }

    @Test
    void isEmptyWhenNew() {
        assertTrue(new IntAhoCorasick().isEmpty());
    }

    @Test
    void isNotEmptyAfterInsert() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("test", 1);
        assertFalse(ac.isEmpty());
    }

    @Test
    void isNotEmptyAfterEmptyPatternInsert() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("", 1);
        assertFalse(ac.isEmpty());
    }

    @Test
    void nonAsciiPattern() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("\u00E9l\u00E8ve", 1);
        ac.build();
        assertTrue(search(ac, "un \u00E9l\u00E8ve ici").contains(1));
    }

    @Test
    void multipleEmptyPatternValues() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("", 1);
        ac.insert("", 2);
        ac.insert("", 3);
        ac.build();
        List<Integer> result = search(ac, "text");
        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of(1, 2, 3)));
    }

    @Test
    void failureLinkMergesOutput() {
        IntAhoCorasick ac = new IntAhoCorasick();
        ac.insert("abc", 1);
        ac.insert("bc", 2);
        ac.insert("c", 3);
        ac.build();

        List<Integer> result = search(ac, "abc");
        assertTrue(result.containsAll(List.of(1, 2, 3)));
    }

    @Test
    void manyPatternsStressTest() {
        IntAhoCorasick ac = new IntAhoCorasick();
        for (int i = 0; i < 100; i++) {
            ac.insert("pattern" + i, i);
        }
        ac.build();
        List<Integer> result = search(ac, "this has pattern42 and pattern7 inside");
        assertTrue(result.contains(42));
        assertTrue(result.contains(7));
    }
}
