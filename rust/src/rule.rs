use serde::Deserialize;
use std::cmp::Ordering;
use std::fs;
use std::io::{self, Read};
use std::path::Path;

/// String-matching operators supported by rule conditions.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Operator {
    Equals,
    Contains,
    StartsWith,
    EndsWith,
}

/// Represents the decomposed parts of a URL that conditions can target.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum UrlPart {
    Host,
    Path,
    File,
    Query,
}

/// Number of URL parts (used for flat array indexing).
pub const URL_PART_COUNT: usize = 4;

impl UrlPart {
    /// Returns the ordinal index of this URL part (0-3).
    pub fn ordinal(self) -> usize {
        self as usize
    }

    /// All URL part variants in ordinal order.
    pub const ALL: [UrlPart; URL_PART_COUNT] = [
        UrlPart::Host,
        UrlPart::Path,
        UrlPart::File,
        UrlPart::Query,
    ];
}

/// A single condition within a rule, targeting one URL part with one operator.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Deserialize)]
pub struct Condition {
    pub part: UrlPart,
    pub operator: Operator,
    pub value: String,
    #[serde(default)]
    pub negated: bool,
}

impl Condition {
    /// Creates a new condition.
    pub fn new(part: UrlPart, operator: Operator, value: impl Into<String>, negated: bool) -> Self {
        Self {
            part,
            operator,
            value: value.into(),
            negated,
        }
    }
}

/// A named rule consisting of one or more conditions and a result string.
///
/// Rules are compared by priority in descending order (highest first).
#[derive(Debug, Clone, PartialEq, Eq, Hash, Deserialize)]
pub struct Rule {
    pub name: String,
    pub priority: i32,
    pub conditions: Vec<Condition>,
    pub result: String,
}

impl Rule {
    /// Creates a new rule.
    pub fn new(
        name: impl Into<String>,
        priority: i32,
        conditions: Vec<Condition>,
        result: impl Into<String>,
    ) -> Self {
        Self {
            name: name.into(),
            priority,
            conditions,
            result: result.into(),
        }
    }
}

impl Ord for Rule {
    fn cmp(&self, other: &Self) -> Ordering {
        // Descending priority (higher = first)
        other.priority.cmp(&self.priority)
    }
}

impl PartialOrd for Rule {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

/// Loads rules from JSON.
pub struct RuleLoader;

impl RuleLoader {
    /// Loads rules from a JSON file.
    pub fn load_from_file(path: &Path) -> io::Result<Vec<Rule>> {
        let content = fs::read_to_string(path)?;
        Self::load_from_str(&content)
    }

    /// Loads rules from a reader providing JSON content.
    pub fn load_from_reader(reader: &mut dyn Read) -> io::Result<Vec<Rule>> {
        let mut content = String::new();
        reader.read_to_string(&mut content)?;
        Self::load_from_str(&content)
    }

    /// Loads rules from a JSON string.
    pub fn load_from_str(json: &str) -> io::Result<Vec<Rule>> {
        let rules: Vec<Rule> =
            serde_json::from_str(json).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))?;
        Ok(rules)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TEST_RULES_JSON: &str = include_str!("../tests/data/test-rules.json");

    #[test]
    fn loads_rules_from_json() {
        let rules = RuleLoader::load_from_str(TEST_RULES_JSON).unwrap();
        assert_eq!(3, rules.len());
    }

    #[test]
    fn parses_canada_sport_rule() {
        let rules = RuleLoader::load_from_str(TEST_RULES_JSON).unwrap();
        let canada_sport = rules.iter().find(|r| r.name == "Canada Sport").unwrap();

        assert_eq!(10, canada_sport.priority);
        assert_eq!("Canada Sport", canada_sport.result);
        assert_eq!(2, canada_sport.conditions.len());

        let host_cond = &canada_sport.conditions[0];
        assert_eq!(UrlPart::Host, host_cond.part);
        assert_eq!(Operator::EndsWith, host_cond.operator);
        assert_eq!(".ca", host_cond.value);
        assert!(!host_cond.negated);
    }

    #[test]
    fn parses_negated_condition() {
        let rules = RuleLoader::load_from_str(TEST_RULES_JSON).unwrap();
        let not_admin = rules.iter().find(|r| r.name == "Not Admin").unwrap();

        let cond = &not_admin.conditions[0];
        assert!(cond.negated);
        assert_eq!(Operator::StartsWith, cond.operator);
    }

    #[test]
    fn case_insensitive_enums() {
        let json = r#"[{"name":"test","priority":1,"conditions":[
          {"part":"host","operator":"equals","value":"x"}
        ],"result":"ok"}]"#;
        let rules = RuleLoader::load_from_str(json).unwrap();
        assert_eq!(UrlPart::Host, rules[0].conditions[0].part);
    }

    #[test]
    fn empty_json_returns_empty_list() {
        let rules = RuleLoader::load_from_str("[]").unwrap();
        assert!(rules.is_empty());
    }

    #[test]
    fn rules_are_sorted_by_priority() {
        let rules = RuleLoader::load_from_str(TEST_RULES_JSON).unwrap();
        let mut sorted = rules.clone();
        sorted.sort();
        assert_eq!("Canada Sport", sorted[0].name);
        assert_eq!("Example Home", sorted[1].name);
        assert_eq!("Not Admin", sorted[2].name);
    }
}
