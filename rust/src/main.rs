use std::env;
use std::path::Path;
use std::process;

use rule_engine::batch::BatchProcessor;
use rule_engine::engine::RuleEngine;
use rule_engine::rule::RuleLoader;

/// CLI entry point for the rule engine.
///
/// Usage: `rule-engine <rules.json> <urls.txt>`
fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 3 {
        eprintln!("Usage: rule-engine <rules.json> <urls.txt>");
        process::exit(1);
    }

    let rules_path = Path::new(&args[1]);
    let urls_path = Path::new(&args[2]);

    let rules = match RuleLoader::load_from_file(rules_path) {
        Ok(r) => r,
        Err(e) => {
            eprintln!("Error: {}", e);
            process::exit(1);
        }
    };

    let engine = RuleEngine::new(rules);
    let processor = BatchProcessor::new(&engine);

    let results = match processor.process_file(urls_path) {
        Ok(r) => r,
        Err(e) => {
            eprintln!("Error: {}", e);
            process::exit(1);
        }
    };

    for result in &results {
        println!("{} -> {}", result.url, result.result);
    }
}
