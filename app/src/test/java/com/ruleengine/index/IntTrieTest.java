package com.ruleengine.index;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntTrieTest {

    private List<Integer> collect(IntTrie trie, String input) {
        List<Integer> result = new ArrayList<>();
        trie.findPrefixesOf(input, result::add);
        return result;
    }

    @Test
    void findPrefixesOfFindsExactMatch() {
        IntTrie trie = new IntTrie();
        trie.insert("abc", 1);
        assertEquals(List.of(1), collect(trie, "abc"));
    }

    @Test
    void findPrefixesOfFindsMultiplePrefixes() {
        IntTrie trie = new IntTrie();
        trie.insert("/", 10);
        trie.insert("/api", 20);
        trie.insert("/api/users", 30);

        List<Integer> result = collect(trie, "/api/users/123");
        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of(10, 20, 30)));
    }

    @Test
    void findPrefixesOfReturnsEmptyForNoMatch() {
        IntTrie trie = new IntTrie();
        trie.insert("xyz", 1);
        assertTrue(collect(trie, "abc").isEmpty());
    }

    @Test
    void findPrefixesOfMatchesEmptyKey() {
        IntTrie trie = new IntTrie();
        trie.insert("", 42);
        assertEquals(List.of(42), collect(trie, "anything"));
    }

    @Test
    void multipleValuesForSameKey() {
        IntTrie trie = new IntTrie();
        trie.insert("key", 1);
        trie.insert("key", 2);
        List<Integer> result = collect(trie, "key");
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(1, 2)));
    }

    @Test
    void charArrayOverload() {
        IntTrie trie = new IntTrie();
        trie.insert("cba", 10);

        List<Integer> result = new ArrayList<>();
        char[] buf = "cba".toCharArray();
        trie.findPrefixesOf(buf, buf.length, result::add);
        assertEquals(List.of(10), result);
    }

    @Test
    void charArrayWithShorterLength() {
        IntTrie trie = new IntTrie();
        trie.insert("ab", 1);
        trie.insert("abc", 2);

        List<Integer> result = new ArrayList<>();
        char[] buf = {'a', 'b', 'c', 'd'};
        trie.findPrefixesOf(buf, 2, result::add);
        assertEquals(List.of(1), result);
    }

    @Test
    void isEmptyWhenNew() {
        assertTrue(new IntTrie().isEmpty());
    }

    @Test
    void isNotEmptyAfterInsert() {
        IntTrie trie = new IntTrie();
        trie.insert("a", 1);
        assertFalse(trie.isEmpty());
    }

    @Test
    void isNotEmptyAfterEmptyKeyInsert() {
        IntTrie trie = new IntTrie();
        trie.insert("", 1);
        assertFalse(trie.isEmpty());
    }

    @Test
    void nonAsciiCharacters() {
        IntTrie trie = new IntTrie();
        trie.insert("\u00E9l\u00E8ve", 1);
        trie.insert("\u00E9", 2);
        List<Integer> result = collect(trie, "\u00E9l\u00E8ve/page");
        assertTrue(result.containsAll(List.of(1, 2)));
    }

    @Test
    void multipleEmptyKeyValues() {
        IntTrie trie = new IntTrie();
        trie.insert("", 1);
        trie.insert("", 2);
        trie.insert("", 3);
        List<Integer> result = collect(trie, "anything");
        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of(1, 2, 3)));
    }

    @Test
    void manyValuesGrowsArray() {
        IntTrie trie = new IntTrie();
        for (int i = 0; i < 10; i++) {
            trie.insert("key", i);
        }
        List<Integer> result = collect(trie, "key");
        assertEquals(10, result.size());
    }
}
