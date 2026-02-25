use std::cell::RefCell;

use crate::rule::{Condition, Operator, Rule};
use crate::rule_index::{CandidateResult, RuleIndex};
use crate::url::ParsedUrl;

/// Thread-local reusable buffers for evaluate().
struct QueryContext {
    candidates: CandidateResult,
    reverse_buf: Vec<u8>,
}

thread_local! {
    static QUERY_CTX: RefCell<QueryContext> = RefCell::new(QueryContext {
        candidates: CandidateResult::new(),
        reverse_buf: Vec::new(),
    });
}

/// Bundles a rule with its precomputed index ID and negation flag.
struct SortedEntry {
    rule_index: usize,
    rule_id: u32,
    all_negated: bool,
}

/// Evaluates a parsed URL against a set of rules and returns the result
/// of the highest-priority matching rule.
///
/// Matching is accelerated by a `RuleIndex` for non-negated conditions.
/// Negated conditions are evaluated directly at match time.
pub struct RuleEngine {
    rules: Vec<Rule>,
    entries: Vec<SortedEntry>,
    index: RuleIndex,
}

impl RuleEngine {
    /// Creates an engine that evaluates the given rules.
    pub fn new(rules: Vec<Rule>) -> Self {
        let index = RuleIndex::new(&rules);

        // Build sorted entries: sort by priority (descending), stable for ties
        let mut indices: Vec<usize> = (0..rules.len()).collect();
        indices.sort_by(|&a, &b| rules[a].cmp(&rules[b]));

        let entries: Vec<SortedEntry> = indices
            .into_iter()
            .map(|i| {
                let rule_id = index.rule_id(i);
                let all_negated = rules[i].conditions.iter().all(|c| c.negated);
                SortedEntry {
                    rule_index: i,
                    rule_id,
                    all_negated,
                }
            })
            .collect();

        Self {
            rules,
            entries,
            index,
        }
    }

    /// Evaluates a parsed URL against all rules and returns the result of the
    /// highest-priority matching rule, or `None` if no rule matches.
    pub fn evaluate(&self, url: &ParsedUrl) -> Option<&str> {
        QUERY_CTX.with(|ctx| {
            let mut ctx = ctx.borrow_mut();
            let QueryContext {
                ref mut candidates,
                ref mut reverse_buf,
            } = *ctx;
            self.index.query_candidates_into(url, candidates, reverse_buf);

            let non_negated = self.index.non_negated_counts();

            for entry in &self.entries {
                if !candidates.is_candidate(entry.rule_id) && !entry.all_negated {
                    continue;
                }
                if candidates.all_satisfied(entry.rule_id, non_negated)
                    && self.no_negated_conditions_match(&self.rules[entry.rule_index], url)
                {
                    return Some(self.rules[entry.rule_index].result.as_str());
                }
            }
            None
        })
    }

    /// Returns `true` if none of the rule's negated conditions match the URL.
    fn no_negated_conditions_match(&self, rule: &Rule, url: &ParsedUrl) -> bool {
        for cond in &rule.conditions {
            if cond.negated && Self::matches_direct(cond, url) {
                return false;
            }
        }
        true
    }

    fn matches_direct(cond: &Condition, url: &ParsedUrl) -> bool {
        let value = url.part(cond.part);
        match cond.operator {
            Operator::Equals => value == cond.value,
            Operator::Contains => value.contains(&*cond.value),
            Operator::StartsWith => value.starts_with(&*cond.value),
            Operator::EndsWith => value.ends_with(&*cond.value),
        }
    }
}
