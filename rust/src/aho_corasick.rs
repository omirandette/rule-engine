use std::collections::{HashMap, VecDeque};

const ASCII_SIZE: usize = 128;
const NO_STATE: u32 = u32::MAX;

/// Build-phase node for the Aho-Corasick automaton.
struct BuildNode<V: Clone> {
    ascii: [u32; ASCII_SIZE],
    extended: Option<HashMap<char, u32>>,
    output: Vec<V>,
}

/// A generic Aho-Corasick automaton for multi-pattern substring matching.
///
/// Uses a DFA with array-indexed transitions for ASCII characters and a
/// HashMap fallback for non-ASCII. After `build()`, the goto function is
/// fully completed so search requires no failure-link chasing.
pub struct AhoCorasick<V: Clone> {
    // Build phase
    build_nodes: Option<Vec<BuildNode<V>>>,
    empty_pattern_values: Vec<V>,
    has_patterns: bool,

    // Search phase (populated by build)
    goto_table: Vec<[u32; ASCII_SIZE]>,
    extended_goto: Vec<Option<HashMap<char, u32>>>,
    output: Vec<Box<[V]>>,
    built: bool,
}

impl<V: Clone> AhoCorasick<V> {
    /// Creates a new empty automaton.
    pub fn new() -> Self {
        let root = BuildNode {
            ascii: [NO_STATE; ASCII_SIZE],
            extended: None,
            output: Vec::new(),
        };
        Self {
            build_nodes: Some(vec![root]),
            empty_pattern_values: Vec::new(),
            has_patterns: false,
            goto_table: Vec::new(),
            extended_goto: Vec::new(),
            output: Vec::new(),
            built: false,
        }
    }

    /// Returns `true` if no patterns have been inserted.
    pub fn is_empty(&self) -> bool {
        !self.has_patterns && self.empty_pattern_values.is_empty()
    }

    /// Inserts a pattern with an associated value.
    ///
    /// # Panics
    /// Panics if called after `build()`.
    pub fn insert(&mut self, pattern: &str, value: V) {
        assert!(!self.built, "Cannot insert after build()");
        self.has_patterns = true;

        if pattern.is_empty() {
            self.empty_pattern_values.push(value);
            return;
        }

        let nodes = self.build_nodes.as_mut().unwrap();
        let mut state = 0u32;
        for c in pattern.chars() {
            let next = Self::get_goto_build(nodes, state, c);
            if next == NO_STATE {
                let new_id = nodes.len() as u32;
                Self::set_goto_build(nodes, state, c, new_id);
                nodes.push(BuildNode {
                    ascii: [NO_STATE; ASCII_SIZE],
                    extended: None,
                    output: Vec::new(),
                });
                state = new_id;
            } else {
                state = next;
            }
        }
        nodes[state as usize].output.push(value);
    }

    /// Constructs the automaton by computing failure links and completing the DFA.
    pub fn build(&mut self) {
        let nodes = self.build_nodes.take().unwrap();
        let state_count = nodes.len();

        // Copy to mutable search-phase structures
        let mut goto: Vec<[u32; ASCII_SIZE]> = nodes.iter().map(|n| n.ascii).collect();
        let mut extended: Vec<Option<HashMap<char, u32>>> =
            nodes.iter().map(|n| n.extended.clone()).collect();
        let mut output: Vec<Vec<V>> = nodes.into_iter().map(|n| n.output).collect();

        let mut failure = vec![0u32; state_count];
        let mut queue = VecDeque::new();

        // Phase 1: init depth-1 states
        for c in 0..ASCII_SIZE {
            let child = goto[0][c];
            if child == NO_STATE {
                goto[0][c] = 0; // self-loop on root
            } else {
                failure[child as usize] = 0;
                queue.push_back(child);
            }
        }
        if let Some(ref ext) = extended[0] {
            for &child in ext.values() {
                failure[child as usize] = 0;
                queue.push_back(child);
            }
        }

        // Phase 2: compute failure links
        while let Some(current) = queue.pop_front() {
            let cur = current as usize;

            for c in 0..ASCII_SIZE {
                let child = goto[cur][c];
                if child != NO_STATE {
                    let f = Self::follow_failure(&goto, &extended, &failure, current, c as u8 as char);
                    failure[child as usize] = f;
                    Self::merge_output(&mut output, child as usize, f as usize);
                    queue.push_back(child);
                }
            }

            if let Some(ext) = extended[cur].clone() {
                for (&c, &child) in &ext {
                    let f = Self::follow_failure(&goto, &extended, &failure, current, c);
                    failure[child as usize] = f;
                    Self::merge_output(&mut output, child as usize, f as usize);
                    queue.push_back(child);
                }
            }
        }

        // Phase 3: complete DFA
        // Seed with root's children
        for c in 0..ASCII_SIZE {
            let child = goto[0][c];
            if child != 0 {
                queue.push_back(child);
            }
        }
        if let Some(ref ext) = extended[0] {
            for &child in ext.values() {
                queue.push_back(child);
            }
        }

        while let Some(current) = queue.pop_front() {
            let cur = current as usize;
            let fail = failure[cur] as usize;

            for c in 0..ASCII_SIZE {
                if goto[cur][c] == NO_STATE {
                    goto[cur][c] = goto[fail][c]; // inherit from failure
                } else {
                    queue.push_back(goto[cur][c]);
                }
            }

            // Enqueue extended children BEFORE inheriting
            if let Some(ref ext) = extended[cur].clone() {
                for &child in ext.values() {
                    if child != 0 {
                        queue.push_back(child);
                    }
                }
            }

            // Inherit extended transitions from failure state
            if let Some(fail_ext) = extended[fail].clone() {
                let ext = extended[cur].get_or_insert_with(|| HashMap::with_capacity(4));
                for (c, target) in fail_ext {
                    ext.entry(c).or_insert(target);
                }
            }
        }

        self.goto_table = goto;
        self.extended_goto = extended;
        self.output = output.into_iter().map(|v| v.into_boxed_slice()).collect();
        self.built = true;
    }

    /// Searches the text and invokes the callback for each matching value.
    ///
    /// # Panics
    /// Panics (in debug builds) if `build()` has not been called.
    pub fn search(&self, text: &str, callback: &mut impl FnMut(&V)) {
        debug_assert!(self.built, "Must call build() before search()");

        for v in &self.empty_pattern_values {
            callback(v);
        }
        let mut state = 0u32;
        for c in text.chars() {
            state = self.next_state(state, c);
            for v in &*self.output[state as usize] {
                callback(v);
            }
        }
    }

    /// Byte-oriented search. Iterates `text.as_bytes()` directly, using
    /// the goto table for bytes < 128 and resetting to state 0 for
    /// bytes >= 128 (safe since all patterns are ASCII).
    pub fn search_bytes(&self, text: &str, callback: &mut impl FnMut(&V)) {
        debug_assert!(self.built, "Must call build() before search_bytes()");

        for v in &self.empty_pattern_values {
            callback(v);
        }
        let mut state = 0u32;
        for &b in text.as_bytes() {
            if b < 128 {
                state = self.goto_table[state as usize][b as usize];
            } else {
                state = 0;
            }
            for v in &*self.output[state as usize] {
                callback(v);
            }
        }
    }

    /// Searches the text and returns all matching values.
    pub fn search_collect(&self, text: &str) -> Vec<V> {
        let mut result = Vec::new();
        self.search(text, &mut |v| result.push(v.clone()));
        result
    }

    fn get_goto_build(nodes: &[BuildNode<V>], state: u32, c: char) -> u32 {
        if (c as u32) < ASCII_SIZE as u32 {
            nodes[state as usize].ascii[c as usize]
        } else {
            nodes[state as usize]
                .extended
                .as_ref()
                .and_then(|m| m.get(&c).copied())
                .unwrap_or(NO_STATE)
        }
    }

    fn set_goto_build(nodes: &mut [BuildNode<V>], state: u32, c: char, target: u32) {
        if (c as u32) < ASCII_SIZE as u32 {
            nodes[state as usize].ascii[c as usize] = target;
        } else {
            nodes[state as usize]
                .extended
                .get_or_insert_with(|| HashMap::with_capacity(4))
                .insert(c, target);
        }
    }

    fn follow_failure(
        goto: &[[u32; ASCII_SIZE]],
        extended: &[Option<HashMap<char, u32>>],
        failure: &[u32],
        parent: u32,
        c: char,
    ) -> u32 {
        let mut state = failure[parent as usize];
        while state != 0 && Self::get_goto_search(goto, extended, state, c) == NO_STATE {
            state = failure[state as usize];
        }
        let target = Self::get_goto_search(goto, extended, state, c);
        if target != NO_STATE { target } else { 0 }
    }

    fn get_goto_search(
        goto: &[[u32; ASCII_SIZE]],
        extended: &[Option<HashMap<char, u32>>],
        state: u32,
        c: char,
    ) -> u32 {
        if (c as u32) < ASCII_SIZE as u32 {
            goto[state as usize][c as usize]
        } else {
            extended[state as usize]
                .as_ref()
                .and_then(|m| m.get(&c).copied())
                .unwrap_or(NO_STATE)
        }
    }

    fn merge_output(output: &mut [Vec<V>], state: usize, fail_state: usize) {
        if output[fail_state].is_empty() {
            return;
        }
        let fail_out = output[fail_state].clone();
        output[state].extend(fail_out);
    }

    fn next_state(&self, state: u32, c: char) -> u32 {
        if (c as u32) < ASCII_SIZE as u32 {
            self.goto_table[state as usize][c as usize]
        } else {
            self.extended_goto[state as usize]
                .as_ref()
                .and_then(|m| m.get(&c).copied())
                .unwrap_or(0) // unknown non-ASCII → root
        }
    }
}

impl<V: Clone> Default for AhoCorasick<V> {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // --- Generic AhoCorasick<String> tests (from AhoCorasickTest.java) ---

    #[test]
    fn finds_single_pattern() {
        let mut ac = AhoCorasick::new();
        ac.insert("he", "val".to_string());
        ac.build();
        let result = ac.search_collect("she");
        assert!(result.contains(&"val".to_string()));
    }

    #[test]
    fn finds_multiple_patterns() {
        let mut ac = AhoCorasick::new();
        ac.insert("he", "v1".to_string());
        ac.insert("she", "v2".to_string());
        ac.insert("his", "v3".to_string());
        ac.insert("hers", "v4".to_string());
        ac.build();

        let result = ac.search_collect("shers");
        assert!(result.contains(&"v1".to_string()), "should find 'he'");
        assert!(result.contains(&"v2".to_string()), "should find 'she'");
        assert!(result.contains(&"v4".to_string()), "should find 'hers'");
        assert!(!result.contains(&"v3".to_string()), "should not find 'his'");
    }

    #[test]
    fn finds_overlapping_patterns() {
        let mut ac = AhoCorasick::new();
        ac.insert("ab", "v1".to_string());
        ac.insert("bc", "v2".to_string());
        ac.build();
        let result = ac.search_collect("abc");
        assert!(result.contains(&"v1".to_string()));
        assert!(result.contains(&"v2".to_string()));
    }

    #[test]
    fn no_match_returns_empty() {
        let mut ac = AhoCorasick::new();
        ac.insert("xyz", "val".to_string());
        ac.build();
        let result = ac.search_collect("abc");
        assert!(result.is_empty());
    }

    #[test]
    #[should_panic(expected = "Must call build()")]
    fn panics_if_search_before_build() {
        let mut ac = AhoCorasick::new();
        ac.insert("test", "val".to_string());
        ac.search_collect("test");
    }

    #[test]
    #[should_panic(expected = "Cannot insert after build()")]
    fn panics_if_insert_after_build() {
        let mut ac: AhoCorasick<String> = AhoCorasick::new();
        ac.build();
        ac.insert("test", "val".to_string());
    }

    #[test]
    fn empty_pattern_matches_any_text() {
        let mut ac = AhoCorasick::new();
        ac.insert("", "empty".to_string());
        ac.build();
        let result = ac.search_collect("anything");
        assert!(result.contains(&"empty".to_string()));
    }

    #[test]
    fn finds_pattern_at_end() {
        let mut ac = AhoCorasick::new();
        ac.insert("sport", "val".to_string());
        ac.build();
        let result = ac.search_collect("/category/sport");
        assert!(result.contains(&"val".to_string()));
    }

    #[test]
    fn finds_pattern_in_middle() {
        let mut ac = AhoCorasick::new();
        ac.insert("sport", "val".to_string());
        ac.build();
        let result = ac.search_collect("/category/sport/items");
        assert!(result.contains(&"val".to_string()));
    }

    #[test]
    fn non_ascii_pattern() {
        let mut ac = AhoCorasick::new();
        ac.insert("\u{00E9}l\u{00E8}ve", "found".to_string());
        ac.build();
        let result = ac.search_collect("un \u{00E9}l\u{00E8}ve ici");
        assert!(result.contains(&"found".to_string()));
    }

    // --- AhoCorasick<u32> tests (from IntAhoCorasickTest.java) ---

    fn search_u32(ac: &AhoCorasick<u32>, text: &str) -> Vec<u32> {
        ac.search_collect(text)
    }

    #[test]
    fn int_finds_single_pattern() {
        let mut ac = AhoCorasick::new();
        ac.insert("he", 1u32);
        ac.build();
        assert!(search_u32(&ac, "she").contains(&1));
    }

    #[test]
    fn int_finds_multiple_patterns() {
        let mut ac = AhoCorasick::new();
        ac.insert("he", 1u32);
        ac.insert("she", 2u32);
        ac.insert("his", 3u32);
        ac.insert("hers", 4u32);
        ac.build();

        let result = search_u32(&ac, "shers");
        assert!(result.contains(&1), "should find 'he'");
        assert!(result.contains(&2), "should find 'she'");
        assert!(result.contains(&4), "should find 'hers'");
        assert!(!result.contains(&3), "should not find 'his'");
    }

    #[test]
    fn int_finds_overlapping_patterns() {
        let mut ac = AhoCorasick::new();
        ac.insert("ab", 1u32);
        ac.insert("bc", 2u32);
        ac.build();
        let result = search_u32(&ac, "abc");
        assert!(result.contains(&1));
        assert!(result.contains(&2));
    }

    #[test]
    fn int_no_match_returns_empty() {
        let mut ac = AhoCorasick::new();
        ac.insert("xyz", 1u32);
        ac.build();
        assert!(search_u32(&ac, "abc").is_empty());
    }

    #[test]
    #[should_panic(expected = "Must call build()")]
    fn int_panics_if_search_before_build() {
        let mut ac = AhoCorasick::new();
        ac.insert("test", 1u32);
        ac.search("test", &mut |_| {});
    }

    #[test]
    #[should_panic(expected = "Cannot insert after build()")]
    fn int_panics_if_insert_after_build() {
        let mut ac: AhoCorasick<u32> = AhoCorasick::new();
        ac.build();
        ac.insert("test", 1u32);
    }

    #[test]
    fn int_empty_pattern_matches_any_text() {
        let mut ac = AhoCorasick::new();
        ac.insert("", 42u32);
        ac.build();
        assert!(search_u32(&ac, "anything").contains(&42));
    }

    #[test]
    fn int_finds_pattern_at_end() {
        let mut ac = AhoCorasick::new();
        ac.insert("sport", 1u32);
        ac.build();
        assert!(search_u32(&ac, "/category/sport").contains(&1));
    }

    #[test]
    fn int_finds_pattern_in_middle() {
        let mut ac = AhoCorasick::new();
        ac.insert("sport", 1u32);
        ac.build();
        assert!(search_u32(&ac, "/category/sport/items").contains(&1));
    }

    #[test]
    fn int_is_empty_when_new() {
        assert!(AhoCorasick::<u32>::new().is_empty());
    }

    #[test]
    fn int_is_not_empty_after_insert() {
        let mut ac = AhoCorasick::new();
        ac.insert("test", 1u32);
        assert!(!ac.is_empty());
    }

    #[test]
    fn int_is_not_empty_after_empty_pattern_insert() {
        let mut ac = AhoCorasick::new();
        ac.insert("", 1u32);
        assert!(!ac.is_empty());
    }

    #[test]
    fn int_non_ascii_pattern() {
        let mut ac = AhoCorasick::new();
        ac.insert("\u{00E9}l\u{00E8}ve", 1u32);
        ac.build();
        assert!(search_u32(&ac, "un \u{00E9}l\u{00E8}ve ici").contains(&1));
    }

    #[test]
    fn int_multiple_empty_pattern_values() {
        let mut ac = AhoCorasick::new();
        ac.insert("", 1u32);
        ac.insert("", 2u32);
        ac.insert("", 3u32);
        ac.build();
        let result = search_u32(&ac, "text");
        assert_eq!(3, result.len());
        assert!(result.contains(&1));
        assert!(result.contains(&2));
        assert!(result.contains(&3));
    }

    #[test]
    fn int_failure_link_merges_output() {
        let mut ac = AhoCorasick::new();
        ac.insert("abc", 1u32);
        ac.insert("bc", 2u32);
        ac.insert("c", 3u32);
        ac.build();

        let result = search_u32(&ac, "abc");
        assert!(result.contains(&1));
        assert!(result.contains(&2));
        assert!(result.contains(&3));
    }

    #[test]
    fn int_many_patterns_stress_test() {
        let mut ac = AhoCorasick::new();
        for i in 0..100u32 {
            ac.insert(&format!("pattern{}", i), i);
        }
        ac.build();
        let result = search_u32(&ac, "this has pattern42 and pattern7 inside");
        assert!(result.contains(&42));
        assert!(result.contains(&7));
    }

    #[test]
    fn search_bytes_finds_single_pattern() {
        let mut ac = AhoCorasick::new();
        ac.insert("he", 1u32);
        ac.build();
        let mut result = Vec::new();
        ac.search_bytes("she", &mut |v| result.push(*v));
        assert!(result.contains(&1));
    }

    #[test]
    fn search_bytes_finds_multiple_patterns() {
        let mut ac = AhoCorasick::new();
        ac.insert("he", 1u32);
        ac.insert("she", 2u32);
        ac.insert("his", 3u32);
        ac.insert("hers", 4u32);
        ac.build();

        let mut result = Vec::new();
        ac.search_bytes("shers", &mut |v| result.push(*v));
        assert!(result.contains(&1), "should find 'he'");
        assert!(result.contains(&2), "should find 'she'");
        assert!(result.contains(&4), "should find 'hers'");
        assert!(!result.contains(&3), "should not find 'his'");
    }

    #[test]
    fn search_bytes_empty_pattern() {
        let mut ac = AhoCorasick::new();
        ac.insert("", 42u32);
        ac.build();
        let mut result = Vec::new();
        ac.search_bytes("anything", &mut |v| result.push(*v));
        assert!(result.contains(&42));
    }
}
