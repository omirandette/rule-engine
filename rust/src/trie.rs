use std::collections::HashMap;

const ASCII_SIZE: usize = 128;
const NO_NODE: u32 = u32::MAX;

/// Arena-based node for the trie.
struct TrieNode<V: Clone> {
    ascii: [u32; ASCII_SIZE],
    extended: Option<HashMap<char, u32>>,
    values: Vec<V>,
}

impl<V: Clone> TrieNode<V> {
    fn new() -> Self {
        Self {
            ascii: [NO_NODE; ASCII_SIZE],
            extended: None,
            values: Vec::new(),
        }
    }

    fn child(&self, c: char) -> Option<u32> {
        if (c as u32) < ASCII_SIZE as u32 {
            let v = self.ascii[c as usize];
            if v == NO_NODE { None } else { Some(v) }
        } else {
            self.extended.as_ref().and_then(|m| m.get(&c).copied())
        }
    }

    fn child_or_create(nodes: &mut Vec<TrieNode<V>>, parent_idx: u32, c: char) -> u32 {
        let pi = parent_idx as usize;
        if (c as u32) < ASCII_SIZE as u32 {
            let idx = c as usize;
            let existing = nodes[pi].ascii[idx];
            if existing != NO_NODE {
                return existing;
            }
            let new_id = nodes.len() as u32;
            nodes.push(TrieNode::new());
            nodes[pi].ascii[idx] = new_id;
            new_id
        } else {
            // Ensure extended map exists
            if nodes[pi].extended.is_none() {
                nodes[pi].extended = Some(HashMap::with_capacity(4));
            }
            if let Some(&id) = nodes[pi].extended.as_ref().unwrap().get(&c) {
                return id;
            }
            let new_id = nodes.len() as u32;
            nodes.push(TrieNode::new());
            nodes[pi].extended.as_mut().unwrap().insert(c, new_id);
            new_id
        }
    }
}

/// A generic character-based trie that maps string keys to lists of values.
///
/// Uses arena-based storage with `Vec<TrieNode>` and `u32` indices.
/// Supports prefix queries via `find_prefixes_of`.
pub struct Trie<V: Clone> {
    nodes: Vec<TrieNode<V>>,
    empty_key_values: Vec<V>,
    has_keys: bool,
}

impl<V: Clone> Trie<V> {
    /// Creates a new empty trie.
    pub fn new() -> Self {
        let mut nodes = Vec::new();
        nodes.push(TrieNode::new()); // root = index 0
        Self {
            nodes,
            empty_key_values: Vec::new(),
            has_keys: false,
        }
    }

    /// Returns `true` if this trie contains no entries.
    pub fn is_empty(&self) -> bool {
        !self.has_keys && self.empty_key_values.is_empty()
    }

    /// Inserts a value associated with the given key.
    pub fn insert(&mut self, key: &str, value: V) {
        self.has_keys = true;
        if key.is_empty() {
            self.empty_key_values.push(value);
            return;
        }
        let mut current: u32 = 0;
        for c in key.chars() {
            current = TrieNode::child_or_create(&mut self.nodes, current, c);
        }
        self.nodes[current as usize].values.push(value);
    }

    /// Invokes the callback for each value whose key is a prefix of the input.
    pub fn find_prefixes_of(&self, input: &str, callback: &mut impl FnMut(&V)) {
        for v in &self.empty_key_values {
            callback(v);
        }
        let mut current: u32 = 0;
        for c in input.chars() {
            match self.nodes[current as usize].child(c) {
                Some(next) => {
                    current = next;
                    for v in &self.nodes[current as usize].values {
                        callback(v);
                    }
                }
                None => return,
            }
        }
    }

    /// Invokes the callback for each value whose key is a prefix of the input char slice.
    pub fn find_prefixes_of_chars(&self, input: &[char], callback: &mut impl FnMut(&V)) {
        for v in &self.empty_key_values {
            callback(v);
        }
        let mut current: u32 = 0;
        for &c in input {
            match self.nodes[current as usize].child(c) {
                Some(next) => {
                    current = next;
                    for v in &self.nodes[current as usize].values {
                        callback(v);
                    }
                }
                None => return,
            }
        }
    }

    /// Returns all values whose keys are prefixes of the given input.
    pub fn find_prefixes_of_collect(&self, input: &str) -> Vec<V> {
        let mut result = Vec::new();
        self.find_prefixes_of(input, &mut |v| result.push(v.clone()));
        result
    }
}

impl<V: Clone> Default for Trie<V> {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // --- Generic Trie<String> tests (from TrieTest.java) ---

    #[test]
    fn find_prefixes_of_finds_exact_match() {
        let mut trie = Trie::new();
        trie.insert("abc", "val1".to_string());
        let result = trie.find_prefixes_of_collect("abc");
        assert_eq!(vec!["val1"], result);
    }

    #[test]
    fn find_prefixes_of_finds_multiple_prefixes() {
        let mut trie = Trie::new();
        trie.insert("/", "root".to_string());
        trie.insert("/api", "api".to_string());
        trie.insert("/api/users", "users".to_string());

        let result = trie.find_prefixes_of_collect("/api/users/123");
        assert_eq!(3, result.len());
        assert!(result.contains(&"root".to_string()));
        assert!(result.contains(&"api".to_string()));
        assert!(result.contains(&"users".to_string()));
    }

    #[test]
    fn find_prefixes_of_returns_empty_for_no_match() {
        let mut trie = Trie::new();
        trie.insert("xyz", "val".to_string());
        let result = trie.find_prefixes_of_collect("abc");
        assert!(result.is_empty());
    }

    #[test]
    fn find_prefixes_of_matches_empty_key() {
        let mut trie = Trie::new();
        trie.insert("", "empty".to_string());
        let result = trie.find_prefixes_of_collect("anything");
        assert_eq!(vec!["empty"], result);
    }

    #[test]
    fn multiple_values_for_same_key() {
        let mut trie = Trie::new();
        trie.insert("key", "v1".to_string());
        trie.insert("key", "v2".to_string());
        let result = trie.find_prefixes_of_collect("key");
        assert_eq!(2, result.len());
        assert!(result.contains(&"v1".to_string()));
        assert!(result.contains(&"v2".to_string()));
    }

    // --- Trie<u32> tests (from IntTrieTest.java) ---

    fn collect_u32(trie: &Trie<u32>, input: &str) -> Vec<u32> {
        trie.find_prefixes_of_collect(input)
    }

    #[test]
    fn int_find_prefixes_of_finds_exact_match() {
        let mut trie = Trie::new();
        trie.insert("abc", 1u32);
        assert_eq!(vec![1], collect_u32(&trie, "abc"));
    }

    #[test]
    fn int_find_prefixes_of_finds_multiple_prefixes() {
        let mut trie = Trie::new();
        trie.insert("/", 10u32);
        trie.insert("/api", 20u32);
        trie.insert("/api/users", 30u32);

        let result = collect_u32(&trie, "/api/users/123");
        assert_eq!(3, result.len());
        assert!(result.contains(&10));
        assert!(result.contains(&20));
        assert!(result.contains(&30));
    }

    #[test]
    fn int_find_prefixes_of_returns_empty_for_no_match() {
        let mut trie = Trie::new();
        trie.insert("xyz", 1u32);
        assert!(collect_u32(&trie, "abc").is_empty());
    }

    #[test]
    fn int_find_prefixes_of_matches_empty_key() {
        let mut trie = Trie::new();
        trie.insert("", 42u32);
        assert_eq!(vec![42], collect_u32(&trie, "anything"));
    }

    #[test]
    fn int_multiple_values_for_same_key() {
        let mut trie = Trie::new();
        trie.insert("key", 1u32);
        trie.insert("key", 2u32);
        let result = collect_u32(&trie, "key");
        assert_eq!(2, result.len());
        assert!(result.contains(&1));
        assert!(result.contains(&2));
    }

    #[test]
    fn char_array_overload() {
        let mut trie = Trie::new();
        trie.insert("cba", 10u32);

        let mut result = Vec::new();
        let chars: Vec<char> = "cba".chars().collect();
        trie.find_prefixes_of_chars(&chars, &mut |v| result.push(*v));
        assert_eq!(vec![10u32], result);
    }

    #[test]
    fn char_array_with_shorter_length() {
        let mut trie = Trie::new();
        trie.insert("ab", 1u32);
        trie.insert("abc", 2u32);

        let mut result = Vec::new();
        let chars: Vec<char> = "abcd".chars().collect();
        trie.find_prefixes_of_chars(&chars[..2], &mut |v| result.push(*v));
        assert_eq!(vec![1u32], result);
    }

    #[test]
    fn is_empty_when_new() {
        assert!(Trie::<u32>::new().is_empty());
    }

    #[test]
    fn is_not_empty_after_insert() {
        let mut trie = Trie::new();
        trie.insert("a", 1u32);
        assert!(!trie.is_empty());
    }

    #[test]
    fn is_not_empty_after_empty_key_insert() {
        let mut trie = Trie::new();
        trie.insert("", 1u32);
        assert!(!trie.is_empty());
    }

    #[test]
    fn non_ascii_characters() {
        let mut trie = Trie::new();
        trie.insert("\u{00E9}l\u{00E8}ve", 1u32);
        trie.insert("\u{00E9}", 2u32);
        let result = collect_u32(&trie, "\u{00E9}l\u{00E8}ve/page");
        assert!(result.contains(&1));
        assert!(result.contains(&2));
    }

    #[test]
    fn multiple_empty_key_values() {
        let mut trie = Trie::new();
        trie.insert("", 1u32);
        trie.insert("", 2u32);
        trie.insert("", 3u32);
        let result = collect_u32(&trie, "anything");
        assert_eq!(3, result.len());
        assert!(result.contains(&1));
        assert!(result.contains(&2));
        assert!(result.contains(&3));
    }

    #[test]
    fn many_values_grows_array() {
        let mut trie = Trie::new();
        for i in 0..10u32 {
            trie.insert("key", i);
        }
        let result = collect_u32(&trie, "key");
        assert_eq!(10, result.len());
    }

}
