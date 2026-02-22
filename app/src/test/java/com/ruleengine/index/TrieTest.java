package com.ruleengine.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrieTest {

    @Test
    void findPrefixesOfFindsExactMatch() {
        Trie<String> trie = new Trie<>();
        trie.insert("abc", "val1");
        List<String> result = trie.findPrefixesOf("abc");
        assertEquals(List.of("val1"), result);
    }

    @Test
    void findPrefixesOfFindsMultiplePrefixes() {
        Trie<String> trie = new Trie<>();
        trie.insert("/", "root");
        trie.insert("/api", "api");
        trie.insert("/api/users", "users");

        List<String> result = trie.findPrefixesOf("/api/users/123");
        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of("root", "api", "users")));
    }

    @Test
    void findPrefixesOfReturnsEmptyForNoMatch() {
        Trie<String> trie = new Trie<>();
        trie.insert("xyz", "val");
        List<String> result = trie.findPrefixesOf("abc");
        assertTrue(result.isEmpty());
    }

    @Test
    void findPrefixesOfMatchesEmptyKey() {
        Trie<String> trie = new Trie<>();
        trie.insert("", "empty");
        List<String> result = trie.findPrefixesOf("anything");
        assertEquals(List.of("empty"), result);
    }

    @Test
    void findSubstringsOfFindsMiddleMatch() {
        Trie<String> trie = new Trie<>();
        trie.insert("sport", "match");
        List<String> result = trie.findSubstringsOf("/category/sport/items");
        assertTrue(result.contains("match"));
    }

    @Test
    void findSubstringsOfFindsMultipleSubstrings() {
        Trie<String> trie = new Trie<>();
        trie.insert("ab", "v1");
        trie.insert("cd", "v2");
        List<String> result = trie.findSubstringsOf("abcd");
        assertTrue(result.containsAll(List.of("v1", "v2")));
    }

    @Test
    void findSubstringsOfAtStart() {
        Trie<String> trie = new Trie<>();
        trie.insert("hello", "val");
        List<String> result = trie.findSubstringsOf("helloworld");
        assertTrue(result.contains("val"));
    }

    @Test
    void findSubstringsOfAtEnd() {
        Trie<String> trie = new Trie<>();
        trie.insert("world", "val");
        List<String> result = trie.findSubstringsOf("helloworld");
        assertTrue(result.contains("val"));
    }

    @Test
    void findSubstringsOfNoMatch() {
        Trie<String> trie = new Trie<>();
        trie.insert("xyz", "val");
        List<String> result = trie.findSubstringsOf("abc");
        assertTrue(result.isEmpty());
    }

    @Test
    void multipleValuesForSameKey() {
        Trie<String> trie = new Trie<>();
        trie.insert("key", "v1");
        trie.insert("key", "v2");
        List<String> result = trie.findPrefixesOf("key");
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of("v1", "v2")));
    }
}
