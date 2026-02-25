use crate::engine::RuleEngine;
use crate::url::UrlParser;
use rayon::prelude::*;
use std::fs;
use std::io;
use std::path::Path;

/// The result of evaluating a single URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UrlResult {
    pub url: String,
    pub result: String,
}

/// Processes batches of URLs against a RuleEngine.
pub struct BatchProcessor<'a> {
    engine: &'a RuleEngine,
}

impl<'a> BatchProcessor<'a> {
    /// Creates a batch processor backed by the given engine.
    pub fn new(engine: &'a RuleEngine) -> Self {
        Self { engine }
    }

    /// Reads URLs from a file and evaluates each against the engine.
    pub fn process_file(&self, url_file: &Path) -> io::Result<Vec<UrlResult>> {
        let content = fs::read_to_string(url_file)?;
        let lines: Vec<String> = content.lines().map(|s| s.to_string()).collect();
        Ok(self.process_lines(&lines))
    }

    /// Evaluates a list of URL strings against the engine in parallel.
    ///
    /// Uses rayon parallel iterator for distribution across available cores.
    /// Encounter order is preserved.
    pub fn process_lines(&self, lines: &[String]) -> Vec<UrlResult> {
        lines
            .par_iter()
            .filter(|line| !line.trim().is_empty())
            .map(|line| self.evaluate_line(line))
            .collect()
    }

    fn evaluate_line(&self, line: &str) -> UrlResult {
        let stripped = line.trim();
        match UrlParser::parse(stripped) {
            Ok(parsed) => {
                let result = match self.engine.evaluate(&parsed) {
                    Some(r) => r.to_string(),
                    None => "NO_MATCH".to_string(),
                };
                UrlResult {
                    url: stripped.to_string(),
                    result,
                }
            }
            Err(_) => UrlResult {
                url: stripped.to_string(),
                result: "INVALID_URL".to_string(),
            },
        }
    }
}
