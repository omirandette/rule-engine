use rule_engine::batch::BatchProcessor;
use rule_engine::engine::RuleEngine;
use rule_engine::rule::{Condition, Operator, Rule, RuleLoader, UrlPart};
use rule_engine::url::{ParsedUrl, UrlParser};

// --- Helpers ---

fn rule(name: &str, priority: i32, result: &str, conditions: Vec<Condition>) -> Rule {
    Rule::new(name, priority, conditions, result)
}

fn cond(part: UrlPart, op: Operator, value: &str) -> Condition {
    Condition::new(part, op, value, false)
}

fn neg_cond(part: UrlPart, op: Operator, value: &str) -> Condition {
    Condition::new(part, op, value, true)
}

/// Shorthand to build a ParsedUrl with an auto-derived file component.
fn url(host: &str, path: &str, query: &str) -> ParsedUrl {
    let file = if !path.is_empty() {
        match path.rfind('/') {
            Some(pos) => &path[pos + 1..],
            None => path,
        }
    } else {
        ""
    };
    ParsedUrl::new(host, path, file, query)
}

// ====================================================================
// RuleEngineTest (18 tests)
// ====================================================================

#[test]
fn equals_operator() {
    let r = rule(
        "eq",
        1,
        "matched",
        vec![cond(UrlPart::Host, Operator::Equals, "example.com")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(Some("matched"), engine.evaluate(&url("example.com", "/", "")));
    assert_eq!(None, engine.evaluate(&url("other.com", "/", "")));
}

#[test]
fn contains_operator() {
    let r = rule(
        "ct",
        1,
        "matched",
        vec![cond(UrlPart::Path, Operator::Contains, "sport")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("matched"),
        engine.evaluate(&url("x.com", "/category/sport/items", ""))
    );
    assert_eq!(None, engine.evaluate(&url("x.com", "/category/news", "")));
}

#[test]
fn starts_with_operator() {
    let r = rule(
        "sw",
        1,
        "matched",
        vec![cond(UrlPart::Path, Operator::StartsWith, "/api")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("matched"),
        engine.evaluate(&url("x.com", "/api/users", ""))
    );
    assert_eq!(None, engine.evaluate(&url("x.com", "/web/api", "")));
}

#[test]
fn ends_with_operator() {
    let r = rule(
        "ew",
        1,
        "matched",
        vec![cond(UrlPart::Host, Operator::EndsWith, ".ca")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("matched"),
        engine.evaluate(&url("shop.example.ca", "/", ""))
    );
    assert_eq!(
        None,
        engine.evaluate(&url("shop.example.com", "/", ""))
    );
}

#[test]
fn negated_equals() {
    let r = rule(
        "neq",
        1,
        "not-example",
        vec![neg_cond(UrlPart::Host, Operator::Equals, "example.com")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("not-example"),
        engine.evaluate(&url("other.com", "/", ""))
    );
    assert_eq!(None, engine.evaluate(&url("example.com", "/", "")));
}

#[test]
fn negated_contains() {
    let r = rule(
        "nct",
        1,
        "no-sport",
        vec![neg_cond(UrlPart::Path, Operator::Contains, "sport")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("no-sport"),
        engine.evaluate(&url("x.com", "/news", ""))
    );
    assert_eq!(None, engine.evaluate(&url("x.com", "/sport/live", "")));
}

#[test]
fn negated_starts_with() {
    let r = rule(
        "nsw",
        1,
        "not-admin",
        vec![neg_cond(UrlPart::Path, Operator::StartsWith, "/admin")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("not-admin"),
        engine.evaluate(&url("x.com", "/user", ""))
    );
    assert_eq!(None, engine.evaluate(&url("x.com", "/admin/panel", "")));
}

#[test]
fn negated_ends_with() {
    let r = rule(
        "new",
        1,
        "not-ca",
        vec![neg_cond(UrlPart::Host, Operator::EndsWith, ".ca")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("not-ca"),
        engine.evaluate(&url("example.com", "/", ""))
    );
    assert_eq!(None, engine.evaluate(&url("example.ca", "/", "")));
}

#[test]
fn canada_sport_compound_rule() {
    let r = rule(
        "cs",
        1,
        "Canada Sport",
        vec![
            cond(UrlPart::Host, Operator::EndsWith, ".ca"),
            cond(UrlPart::Path, Operator::Contains, "sport"),
        ],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("Canada Sport"),
        engine.evaluate(&url("shop.example.ca", "/category/sport/items", ""))
    );
    assert_eq!(
        None,
        engine.evaluate(&url("shop.example.ca", "/category/news", ""))
    );
    assert_eq!(
        None,
        engine.evaluate(&url("shop.example.com", "/category/sport", ""))
    );
}

#[test]
fn compound_with_negation() {
    let r = rule(
        "mix",
        1,
        "result",
        vec![
            cond(UrlPart::Host, Operator::Equals, "example.com"),
            neg_cond(UrlPart::Path, Operator::StartsWith, "/admin"),
        ],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("result"),
        engine.evaluate(&url("example.com", "/user", ""))
    );
    assert_eq!(
        None,
        engine.evaluate(&url("example.com", "/admin/panel", ""))
    );
    assert_eq!(None, engine.evaluate(&url("other.com", "/user", "")));
}

#[test]
fn higher_priority_wins() {
    let low = rule(
        "low",
        1,
        "low-result",
        vec![cond(UrlPart::Host, Operator::EndsWith, ".com")],
    );
    let high = rule(
        "high",
        10,
        "high-result",
        vec![cond(UrlPart::Host, Operator::Equals, "example.com")],
    );
    let engine = RuleEngine::new(vec![low, high]);

    assert_eq!(
        Some("high-result"),
        engine.evaluate(&url("example.com", "/", ""))
    );
}

#[test]
fn same_priority_uses_definition_order() {
    let first = rule(
        "first",
        5,
        "first-result",
        vec![cond(UrlPart::Host, Operator::EndsWith, ".com")],
    );
    let second = rule(
        "second",
        5,
        "second-result",
        vec![cond(UrlPart::Host, Operator::EndsWith, ".com")],
    );
    let engine = RuleEngine::new(vec![first, second]);

    let result = engine.evaluate(&url("example.com", "/", ""));
    assert_eq!(Some("first-result"), result);
}

#[test]
fn lower_priority_matches_when_higher_does_not() {
    let high = rule(
        "high",
        10,
        "high-result",
        vec![cond(UrlPart::Host, Operator::Equals, "special.com")],
    );
    let low = rule(
        "low",
        1,
        "low-result",
        vec![cond(UrlPart::Host, Operator::EndsWith, ".com")],
    );
    let engine = RuleEngine::new(vec![high, low]);

    assert_eq!(
        Some("low-result"),
        engine.evaluate(&url("example.com", "/", ""))
    );
}

#[test]
fn no_rules_returns_none() {
    let engine = RuleEngine::new(vec![]);
    assert_eq!(None, engine.evaluate(&url("x.com", "/", "")));
}

#[test]
fn no_match_returns_none() {
    let r = rule(
        "r",
        1,
        "result",
        vec![cond(UrlPart::Host, Operator::Equals, "specific.com")],
    );
    let engine = RuleEngine::new(vec![r]);
    assert_eq!(None, engine.evaluate(&url("other.com", "/", "")));
}

#[test]
fn query_part_matching() {
    let r = rule(
        "qr",
        1,
        "query-match",
        vec![cond(UrlPart::Query, Operator::Contains, "lang=en")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("query-match"),
        engine.evaluate(&url("x.com", "/", "q=test&lang=en"))
    );
    assert_eq!(
        None,
        engine.evaluate(&url("x.com", "/", "q=test&lang=fr"))
    );
}

#[test]
fn empty_path_and_query() {
    let r = rule(
        "empty",
        1,
        "matched",
        vec![cond(UrlPart::Host, Operator::Equals, "example.com")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("matched"),
        engine.evaluate(&url("example.com", "", ""))
    );
}

#[test]
fn file_part_matching() {
    let r = rule(
        "html",
        1,
        "html-file",
        vec![cond(UrlPart::File, Operator::EndsWith, ".html")],
    );
    let engine = RuleEngine::new(vec![r]);

    assert_eq!(
        Some("html-file"),
        engine.evaluate(&url("x.com", "/page/index.html", ""))
    );
    assert_eq!(
        None,
        engine.evaluate(&url("x.com", "/page/data.json", ""))
    );
}

// ====================================================================
// BatchProcessorTest (5 tests)
// ====================================================================

#[test]
fn processes_multiple_urls() {
    let r1 = rule(
        "ca-sport",
        10,
        "Canada Sport",
        vec![
            cond(UrlPart::Host, Operator::EndsWith, ".ca"),
            cond(UrlPart::Path, Operator::Contains, "sport"),
        ],
    );
    let r2 = rule(
        "example-home",
        5,
        "Example Home",
        vec![
            cond(UrlPart::Host, Operator::Equals, "example.com"),
            cond(UrlPart::Path, Operator::Equals, "/"),
        ],
    );

    let engine = RuleEngine::new(vec![r1, r2]);
    let processor = BatchProcessor::new(&engine);

    let lines: Vec<String> = vec![
        "https://shop.example.ca/category/sport/items".to_string(),
        "https://example.com/".to_string(),
        "https://other.org/page".to_string(),
    ];

    let results = processor.process_lines(&lines);

    assert_eq!(3, results.len());
    assert_eq!("Canada Sport", results[0].result);
    assert_eq!("Example Home", results[1].result);
    assert_eq!("NO_MATCH", results[2].result);
}

#[test]
fn skips_blank_lines() {
    let r = rule(
        "r",
        1,
        "ok",
        vec![cond(UrlPart::Host, Operator::Equals, "x.com")],
    );
    let engine = RuleEngine::new(vec![r]);
    let processor = BatchProcessor::new(&engine);

    let lines: Vec<String> = vec![
        "https://x.com/".to_string(),
        "".to_string(),
        "  ".to_string(),
        "https://x.com/page".to_string(),
    ];
    let results = processor.process_lines(&lines);
    assert_eq!(2, results.len());
}

#[test]
fn handles_invalid_urls() {
    let r = rule(
        "r",
        1,
        "ok",
        vec![cond(UrlPart::Host, Operator::Equals, "x.com")],
    );
    let engine = RuleEngine::new(vec![r]);
    let processor = BatchProcessor::new(&engine);

    let lines: Vec<String> = vec!["://bad-url".to_string()];
    let results = processor.process_lines(&lines);
    assert_eq!(1, results.len());
    assert_eq!("INVALID_URL", results[0].result);
}

#[test]
fn empty_input_returns_empty_results() {
    let engine = RuleEngine::new(vec![]);
    let processor = BatchProcessor::new(&engine);
    let results = processor.process_lines(&[]);
    assert!(results.is_empty());
}

#[test]
fn parallel_processing_preserves_order() {
    let r = rule(
        "host-match",
        1,
        "matched",
        vec![cond(UrlPart::Host, Operator::Equals, "example.com")],
    );
    let engine = RuleEngine::new(vec![r]);
    let processor = BatchProcessor::new(&engine);

    let urls: Vec<String> = (0..10_000)
        .map(|i| format!("https://example.com/page/{}", i))
        .collect();

    let results = processor.process_lines(&urls);

    assert_eq!(urls.len(), results.len());
    for (i, result) in results.iter().enumerate() {
        assert_eq!(
            format!("https://example.com/page/{}", i),
            result.url,
            "Result at index {} has wrong URL",
            i
        );
        assert_eq!("matched", result.result);
    }
}

// ====================================================================
// AppTest (integration with test-rules.json)
// ====================================================================

const TEST_RULES_JSON: &str = include_str!("data/test-rules.json");

#[test]
fn integration_test_with_resource_files() {
    let rules = RuleLoader::load_from_str(TEST_RULES_JSON).unwrap();
    let engine = RuleEngine::new(rules);
    let processor = BatchProcessor::new(&engine);

    let lines: Vec<String> = vec![
        "https://shop.example.ca/category/sport/items".to_string(),
        "https://example.com/".to_string(),
        "https://example.com/admin/panel".to_string(),
        "https://example.com/user/profile".to_string(),
        "https://news.example.ca/sport/hockey".to_string(),
    ];

    let results = processor.process_lines(&lines);

    assert_eq!(5, results.len());
    assert_eq!("Canada Sport", results[0].result);
    assert_eq!("Example Home", results[1].result);
    // /admin/panel: Example Home requires path=/, so doesn't match.
    // Not Admin is negated starts_with /admin → fails.
    assert_eq!("NO_MATCH", results[2].result);
    assert_eq!("Not Admin", results[3].result);
    assert_eq!("Canada Sport", results[4].result);
}

// ====================================================================
// RuleEngineIntegrationTest (from integration-rules.json)
// ====================================================================

const INTEGRATION_RULES_JSON: &str = include_str!("data/integration-rules.json");
const CANONICAL_URL: &str = "https://shop.example.ca/api/sport/index.html?lang=en&sort=date";

fn all_single_condition_rule_names() -> Vec<String> {
    let parts = ["host", "path", "file", "query"];
    let operators = ["equals", "contains", "starts_with", "ends_with"];
    let mut names = Vec::new();
    for part in &parts {
        for op in &operators {
            names.push(format!("{}-{}", part, op));
            names.push(format!("{}-{}-neg", part, op));
        }
    }
    names
}

#[test]
fn batch_pipeline_produces_expected_results() {
    let rules = RuleLoader::load_from_str(INTEGRATION_RULES_JSON).unwrap();
    let engine = RuleEngine::new(rules.clone());
    let processor = BatchProcessor::new(&engine);

    let integration_urls: Vec<String> = include_str!("data/integration-urls.txt")
        .lines()
        .filter(|line| !line.trim().is_empty() && !line.starts_with('#'))
        .map(|s| s.to_string())
        .collect();

    let results = processor.process_lines(&integration_urls);

    assert_eq!(3, results.len(), "expected one result per URL");
    assert_eq!(
        "compound-positive", results[0].result,
        "canonical URL should match compound-positive (priority 10)"
    );
    assert_eq!(
        "compound-all-neg", results[1].result,
        "second URL should match compound-all-neg (priority 10)"
    );
    assert_eq!(
        "compound-all-neg", results[2].result,
        "third URL should match compound-all-neg (priority 10)"
    );

    let canonical_batch: Vec<String> = vec![CANONICAL_URL.to_string()];
    let single_rule_names = all_single_condition_rule_names();

    for rule in &rules {
        if !single_rule_names.contains(&rule.name) {
            continue;
        }
        let single_engine = RuleEngine::new(vec![rule.clone()]);
        let single_processor = BatchProcessor::new(&single_engine);
        let single_result = single_processor.process_lines(&canonical_batch);
        assert_eq!(1, single_result.len());
        assert_eq!(
            rule.name, single_result[0].result,
            "Rule {} should match canonical URL via batch pipeline",
            rule.name
        );
    }
}

#[test]
fn each_condition_type_matches_canonical_url() {
    let all_rules = RuleLoader::load_from_str(INTEGRATION_RULES_JSON).unwrap();
    let parsed = UrlParser::parse(CANONICAL_URL).unwrap();

    for rule_name in all_single_condition_rule_names() {
        let target = all_rules
            .iter()
            .find(|r| r.name == rule_name)
            .unwrap_or_else(|| panic!("Rule not found: {}", rule_name));

        let engine = RuleEngine::new(vec![target.clone()]);
        let result = engine.evaluate(&parsed);
        assert!(
            result.is_some(),
            "Rule {} should match canonical URL",
            rule_name
        );
        assert_eq!(rule_name, result.unwrap());
    }
}
