use std::collections::HashMap;

use crate::aho_corasick::AhoCorasick;
use crate::rule::{Operator, Rule, UrlPart, URL_PART_COUNT};
use crate::trie::Trie;
use crate::url::ParsedUrl;

/// Dense array-based container tracking how many non-negated conditions
/// are satisfied per rule, with sparse tracking of touched rule IDs.
pub struct CandidateResult {
    satisfied_counts: Vec<u32>,
    touched: Vec<u32>,
}

impl CandidateResult {
    /// Creates a new empty candidate result.
    pub fn new() -> Self {
        Self {
            satisfied_counts: Vec::new(),
            touched: Vec::new(),
        }
    }

    /// Ensures the internal buffer is at least `n` elements, growing if needed.
    /// Resets only previously-touched entries instead of the full array.
    pub fn ensure_capacity_and_reset(&mut self, n: usize) {
        // Zero previously-touched entries first (cleanup from last query)
        for &id in &self.touched {
            self.satisfied_counts[id as usize] = 0;
        }
        self.touched.clear();
        if self.satisfied_counts.len() < n {
            // Growth: new elements are filled with 0 by resize
            self.satisfied_counts.resize(n, 0);
        }
    }

    fn increment(&mut self, rule_id: u32) {
        let slot = &mut self.satisfied_counts[rule_id as usize];
        if *slot == 0 {
            self.touched.push(rule_id);
        }
        *slot += 1;
    }

    /// Returns `true` if all non-negated conditions for the given rule have been satisfied.
    pub fn all_satisfied(&self, rule_id: u32, non_negated_counts: &[u32]) -> bool {
        self.satisfied_counts[rule_id as usize] == non_negated_counts[rule_id as usize]
    }

    /// Returns `true` if the rule has at least one satisfied condition.
    pub fn is_candidate(&self, rule_id: u32) -> bool {
        self.satisfied_counts[rule_id as usize] > 0
    }
}

/// Indexes non-negated rule conditions by (UrlPart, Operator) for fast lookup.
pub struct RuleIndex {
    equals_indexes: [HashMap<String, Box<[u32]>>; URL_PART_COUNT],
    starts_with_indexes: [Trie<u32>; URL_PART_COUNT],
    ends_with_indexes: [Trie<u32>; URL_PART_COUNT],
    contains_ac_indexes: [AhoCorasick<u32>; URL_PART_COUNT],

    rule_ids: HashMap<usize, u32>, // rule index in original list -> dense ID
    rule_count: usize,
    non_negated_counts: Vec<u32>,
    has_equals: [bool; URL_PART_COUNT],
    has_starts_with: [bool; URL_PART_COUNT],
    has_ends_with: [bool; URL_PART_COUNT],
    has_contains: [bool; URL_PART_COUNT],
}

impl RuleIndex {
    /// Builds the index from a list of rules.
    ///
    /// Rules are identified by their position in the input list.
    pub fn new(rules: &[Rule]) -> Self {
        let rule_count = rules.len();
        let mut non_negated_counts = vec![0u32; rule_count];

        let mut equals_indexes: [HashMap<String, Vec<u32>>; URL_PART_COUNT] =
            std::array::from_fn(|_| HashMap::new());
        let mut starts_with_indexes: [Trie<u32>; URL_PART_COUNT] =
            std::array::from_fn(|_| Trie::new());
        let mut ends_with_indexes: [Trie<u32>; URL_PART_COUNT] =
            std::array::from_fn(|_| Trie::new());
        let mut contains_ac_indexes: [AhoCorasick<u32>; URL_PART_COUNT] =
            std::array::from_fn(|_| AhoCorasick::new());

        let mut rule_ids = HashMap::with_capacity(rule_count * 2);

        for (i, rule) in rules.iter().enumerate() {
            let id = i as u32;
            rule_ids.insert(i, id);

            for cond in &rule.conditions {
                if !cond.negated {
                    non_negated_counts[i] += 1;
                    let p = cond.part.ordinal();
                    match cond.operator {
                        Operator::Equals => {
                            equals_indexes[p]
                                .entry(cond.value.clone())
                                .or_default()
                                .push(id);
                        }
                        Operator::StartsWith => {
                            starts_with_indexes[p].insert(&cond.value, id);
                        }
                        Operator::EndsWith => {
                            let reversed: String = cond.value.chars().rev().collect();
                            ends_with_indexes[p].insert(&reversed, id);
                        }
                        Operator::Contains => {
                            contains_ac_indexes[p].insert(&cond.value, id);
                        }
                    }
                }
            }
        }

        for ac in &mut contains_ac_indexes {
            ac.build();
        }

        let has_equals = std::array::from_fn(|p| !equals_indexes[p].is_empty());
        let has_starts_with = std::array::from_fn(|p| !starts_with_indexes[p].is_empty());
        let has_ends_with = std::array::from_fn(|p| !ends_with_indexes[p].is_empty());
        let has_contains = std::array::from_fn(|p| !contains_ac_indexes[p].is_empty());

        // Freeze equals indexes: Vec<u32> → Box<[u32]>
        let equals_indexes: [HashMap<String, Box<[u32]>>; URL_PART_COUNT] =
            std::array::from_fn(|p| {
                std::mem::take(&mut equals_indexes[p])
                    .into_iter()
                    .map(|(k, v)| (k, v.into_boxed_slice()))
                    .collect()
            });

        Self {
            equals_indexes,
            starts_with_indexes,
            ends_with_indexes,
            contains_ac_indexes,
            rule_ids,
            rule_count,
            non_negated_counts,
            has_equals,
            has_starts_with,
            has_ends_with,
            has_contains,
        }
    }

    /// Returns the dense integer ID assigned to the rule at the given index.
    pub fn rule_id(&self, rule_index: usize) -> u32 {
        self.rule_ids[&rule_index]
    }

    /// Returns the number of rules in the index.
    pub fn rule_count(&self) -> usize {
        self.rule_count
    }

    /// Returns the non-negated condition counts per rule.
    pub fn non_negated_counts(&self) -> &[u32] {
        &self.non_negated_counts
    }

    /// Queries the index for all non-negated conditions that match the URL.
    ///
    /// Returns a `CandidateResult` that must be used before the next call.
    pub fn query_candidates(&self, url: &ParsedUrl) -> CandidateResult {
        let mut candidates = CandidateResult::new();
        candidates.ensure_capacity_and_reset(self.rule_count);
        let mut reverse_buf = Vec::new();
        self.query_candidates_into(url, &mut candidates, &mut reverse_buf);
        candidates
    }

    /// Queries into an existing CandidateResult and reverse buffer (for reuse).
    pub fn query_candidates_into(
        &self,
        url: &ParsedUrl,
        candidates: &mut CandidateResult,
        reverse_buf: &mut Vec<u8>,
    ) {
        candidates.ensure_capacity_and_reset(self.rule_count);

        for part in UrlPart::ALL {
            let p = part.ordinal();
            let value = url.part(part);

            if self.has_equals[p] {
                if let Some(ids) = self.equals_indexes[p].get(value) {
                    for &id in &**ids {
                        candidates.increment(id);
                    }
                }
            }

            if self.has_starts_with[p] {
                self.starts_with_indexes[p]
                    .find_prefixes_of_bytes(value.as_bytes(), &mut |&id| {
                        candidates.increment(id);
                    });
            }

            if self.has_ends_with[p] {
                // Reuse reverse_buf instead of allocating Vec<char> each call
                reverse_buf.clear();
                reverse_buf.extend(value.bytes().rev());
                self.ends_with_indexes[p]
                    .find_prefixes_of_bytes(reverse_buf, &mut |&id| {
                        candidates.increment(id);
                    });
            }

            if self.has_contains[p] {
                self.contains_ac_indexes[p].search_bytes(value, &mut |&id| {
                    candidates.increment(id);
                });
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::Condition;

    fn rule(name: &str, conditions: Vec<Condition>) -> Rule {
        Rule::new(name, 1, conditions, name)
    }

    fn cond(part: UrlPart, op: Operator, value: &str) -> Condition {
        Condition::new(part, op, value, false)
    }

    fn neg_cond(part: UrlPart, op: Operator, value: &str) -> Condition {
        Condition::new(part, op, value, true)
    }

    #[test]
    fn equals_match() {
        let r = rule("eq", vec![cond(UrlPart::Host, Operator::Equals, "example.com")]);
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates =
            index.query_candidates(&ParsedUrl::new("example.com", "/", "", ""));
        assert!(candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn equals_no_match() {
        let r = rule("eq", vec![cond(UrlPart::Host, Operator::Equals, "example.com")]);
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates =
            index.query_candidates(&ParsedUrl::new("other.com", "/", "", ""));
        assert!(!candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn starts_with_match() {
        let r = rule("sw", vec![cond(UrlPart::Path, Operator::StartsWith, "/api")]);
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates =
            index.query_candidates(&ParsedUrl::new("x.com", "/api/users", "users", ""));
        assert!(candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn ends_with_match() {
        let r = rule("ew", vec![cond(UrlPart::Host, Operator::EndsWith, ".ca")]);
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates = index
            .query_candidates(&ParsedUrl::new("shop.example.ca", "/", "", ""));
        assert!(candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn contains_match() {
        let r = rule("ct", vec![cond(UrlPart::Path, Operator::Contains, "sport")]);
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates = index.query_candidates(&ParsedUrl::new(
            "x.com",
            "/category/sport/items",
            "items",
            "",
        ));
        assert!(candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn negated_conditions_not_indexed() {
        let r = rule(
            "neg",
            vec![neg_cond(UrlPart::Path, Operator::StartsWith, "/admin")],
        );
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates = index
            .query_candidates(&ParsedUrl::new("x.com", "/admin/panel", "panel", ""));
        assert!(!candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn multiple_rules_multiple_operators() {
        let r1 = rule("r1", vec![cond(UrlPart::Host, Operator::Equals, "example.com")]);
        let r2 = rule("r2", vec![cond(UrlPart::Path, Operator::Contains, "sport")]);
        let r3 = rule("r3", vec![cond(UrlPart::Host, Operator::EndsWith, ".com")]);
        let rules = vec![r1, r2, r3];
        let index = RuleIndex::new(&rules);

        let candidates =
            index.query_candidates(&ParsedUrl::new("example.com", "/sport", "sport", ""));

        assert!(candidates.is_candidate(index.rule_id(0)));
        assert!(candidates.is_candidate(index.rule_id(1)));
        assert!(candidates.is_candidate(index.rule_id(2)));
    }

    #[test]
    fn query_on_query_param() {
        let r = rule(
            "qp",
            vec![cond(UrlPart::Query, Operator::Contains, "lang=en")],
        );
        let rules = vec![r];
        let index = RuleIndex::new(&rules);

        let candidates = index
            .query_candidates(&ParsedUrl::new("x.com", "/", "", "q=hello&lang=en"));
        assert!(candidates.is_candidate(index.rule_id(0)));
    }

    #[test]
    fn concurrent_queries_return_correct_results() {
        use std::sync::Arc;
        use std::thread;

        let r1 = rule(
            "host-eq",
            vec![cond(UrlPart::Host, Operator::Equals, "match.com")],
        );
        let r2 = rule(
            "path-sw",
            vec![cond(UrlPart::Path, Operator::StartsWith, "/api")],
        );
        let r3 = rule(
            "host-ew",
            vec![cond(UrlPart::Host, Operator::EndsWith, ".org")],
        );
        let rules = vec![r1, r2, r3];
        let index = Arc::new(RuleIndex::new(&rules));

        let match_r1 = ParsedUrl::new("match.com", "/home", "home", "");
        let match_r2 = ParsedUrl::new("other.com", "/api/users", "users", "");
        let match_r3 = ParsedUrl::new("example.org", "/page", "page", "");
        let no_match = ParsedUrl::new("none.net", "/nothing", "nothing", "");

        let urls = Arc::new(vec![match_r1, match_r2, match_r3, no_match]);

        let thread_count = 8;
        let iterations_per_thread = 10_000;

        let mut handles = Vec::new();
        for t in 0..thread_count {
            let index = Arc::clone(&index);
            let urls = Arc::clone(&urls);
            handles.push(thread::spawn(move || {
                for i in 0..iterations_per_thread {
                    let url = &urls[(t + i) % urls.len()];
                    let result = index.query_candidates(url);

                    match (t + i) % urls.len() {
                        0 => {
                            // match_r1
                            assert!(result.is_candidate(0));
                            assert!(!result.is_candidate(1));
                            assert!(!result.is_candidate(2));
                        }
                        1 => {
                            // match_r2
                            assert!(!result.is_candidate(0));
                            assert!(result.is_candidate(1));
                            assert!(!result.is_candidate(2));
                        }
                        2 => {
                            // match_r3
                            assert!(!result.is_candidate(0));
                            assert!(!result.is_candidate(1));
                            assert!(result.is_candidate(2));
                        }
                        3 => {
                            // no_match
                            assert!(!result.is_candidate(0));
                            assert!(!result.is_candidate(1));
                            assert!(!result.is_candidate(2));
                        }
                        _ => unreachable!(),
                    }
                }
            }));
        }

        for handle in handles {
            handle.join().unwrap();
        }
    }
}
