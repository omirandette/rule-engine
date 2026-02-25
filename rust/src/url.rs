use crate::rule::UrlPart;

/// Immutable representation of a parsed URL, decomposed into its constituent parts.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedUrl {
    pub host: String,
    pub path: String,
    pub file: String,
    pub query: String,
}

impl ParsedUrl {
    /// Creates a new ParsedUrl with the given parts.
    pub fn new(
        host: impl Into<String>,
        path: impl Into<String>,
        file: impl Into<String>,
        query: impl Into<String>,
    ) -> Self {
        Self {
            host: host.into(),
            path: path.into(),
            file: file.into(),
            query: query.into(),
        }
    }

    /// Returns the value of the specified URL part.
    pub fn part(&self, url_part: UrlPart) -> &str {
        match url_part {
            UrlPart::Host => &self.host,
            UrlPart::Path => &self.path,
            UrlPart::File => &self.file,
            UrlPart::Query => &self.query,
        }
    }
}

const SCHEME_SEPARATOR: &str = "://";

/// Parses raw URL strings into `ParsedUrl` records.
///
/// Uses fast index-based parsing instead of a full URI parser.
pub struct UrlParser;

impl UrlParser {
    /// Parses a raw URL string into its constituent parts.
    ///
    /// Returns `Err` if the input is empty, blank, or has no parseable host.
    pub fn parse(raw: &str) -> Result<ParsedUrl, String> {
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            return Err("URL must not be blank".to_string());
        }

        let host_start = Self::find_host_start(trimmed, raw)?;

        let path_start = trimmed[host_start..].find('/').map(|i| i + host_start);
        let query_start = trimmed[host_start..].find('?').map(|i| i + host_start);

        let host = Self::extract_host(trimmed, raw, host_start, path_start, query_start)?;
        let path = Self::extract_path(trimmed, path_start, query_start);
        let file = Self::extract_file(&path);
        let query = Self::extract_query(trimmed, query_start);

        Ok(ParsedUrl {
            host,
            path,
            file,
            query,
        })
    }

    fn find_host_start(to_parse: &str, raw: &str) -> Result<usize, String> {
        match to_parse.find(SCHEME_SEPARATOR) {
            Some(0) => Err(format!("Could not parse host from URL: {}", raw)),
            Some(pos) => Ok(pos + SCHEME_SEPARATOR.len()),
            None => Ok(0),
        }
    }

    fn extract_host(
        to_parse: &str,
        raw: &str,
        host_start: usize,
        path_start: Option<usize>,
        query_start: Option<usize>,
    ) -> Result<String, String> {
        let host_end = Self::first_delimiter_or_end(to_parse, path_start, query_start);
        let mut host = &to_parse[host_start..host_end];

        // Strip port
        if let Some(colon) = host.find(':') {
            host = &host[..colon];
        }

        if host.is_empty() {
            return Err(format!("Could not parse host from URL: {}", raw));
        }
        Ok(host.to_lowercase())
    }

    fn first_delimiter_or_end(
        to_parse: &str,
        path_start: Option<usize>,
        query_start: Option<usize>,
    ) -> usize {
        match (path_start, query_start) {
            (Some(p), Some(q)) => p.min(q),
            (Some(p), None) => p,
            (None, Some(q)) => q,
            (None, None) => to_parse.len(),
        }
    }

    fn extract_path(to_parse: &str, path_start: Option<usize>, query_start: Option<usize>) -> String {
        match path_start {
            Some(p) if query_start.is_none() || p < query_start.unwrap() => {
                let path_end = query_start.unwrap_or(to_parse.len());
                to_parse[p..path_end].to_string()
            }
            _ => String::new(),
        }
    }

    fn extract_query(to_parse: &str, query_start: Option<usize>) -> String {
        match query_start {
            Some(q) => to_parse[q + 1..].to_string(),
            None => String::new(),
        }
    }

    fn extract_file(path: &str) -> String {
        if path.is_empty() {
            return String::new();
        }
        match path.rfind('/') {
            Some(pos) => path[pos + 1..].to_string(),
            None => path.to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_full_url() {
        let url = UrlParser::parse("https://example.com/path?key=value").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("/path", url.path);
        assert_eq!("key=value", url.query);
    }

    #[test]
    fn auto_prepends_scheme() {
        let url = UrlParser::parse("example.com/path").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("/path", url.path);
    }

    #[test]
    fn lowercases_host() {
        let url = UrlParser::parse("https://EXAMPLE.COM/Path").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("/Path", url.path);
    }

    #[test]
    fn handles_empty_path() {
        let url = UrlParser::parse("https://example.com").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("", url.path);
        assert_eq!("", url.file);
    }

    #[test]
    fn handles_empty_query() {
        let url = UrlParser::parse("https://example.com/path").unwrap();
        assert_eq!("", url.query);
    }

    #[test]
    fn handles_complex_query() {
        let url = UrlParser::parse("https://example.com/search?q=hello&lang=en").unwrap();
        assert_eq!("q=hello&lang=en", url.query);
    }

    #[test]
    fn errors_on_blank() {
        assert!(UrlParser::parse("  ").is_err());
    }

    #[test]
    fn errors_on_empty() {
        assert!(UrlParser::parse("").is_err());
    }

    #[test]
    fn part_accessor_works() {
        let url = UrlParser::parse("https://example.com/path?q=1").unwrap();
        assert_eq!("example.com", url.part(UrlPart::Host));
        assert_eq!("/path", url.part(UrlPart::Path));
        assert_eq!("path", url.part(UrlPart::File));
        assert_eq!("q=1", url.part(UrlPart::Query));
    }

    #[test]
    fn handles_subdomain() {
        let url = UrlParser::parse("https://www.shop.example.ca/products").unwrap();
        assert_eq!("www.shop.example.ca", url.host);
        assert_eq!("/products", url.path);
    }

    #[test]
    fn extracts_file_from_path() {
        let url = UrlParser::parse("https://example.com/category/sport/items").unwrap();
        assert_eq!("items", url.file);
    }

    #[test]
    fn file_is_empty_for_trailing_slash() {
        let url = UrlParser::parse("https://example.com/path/").unwrap();
        assert_eq!("", url.file);
    }

    #[test]
    fn file_is_empty_for_root_path() {
        let url = UrlParser::parse("https://example.com/").unwrap();
        assert_eq!("", url.file);
    }

    #[test]
    fn file_from_single_segment_path() {
        let url = UrlParser::parse("https://example.com/index.html").unwrap();
        assert_eq!("index.html", url.file);
    }

    #[test]
    fn strips_port_from_host() {
        let url = UrlParser::parse("https://example.com:8080/path?q=1").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("/path", url.path);
        assert_eq!("q=1", url.query);
    }

    #[test]
    fn strips_port_with_no_path() {
        let url = UrlParser::parse("https://example.com:443").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("", url.path);
    }

    #[test]
    fn strips_port_with_no_scheme() {
        let url = UrlParser::parse("example.com:3000/api/data").unwrap();
        assert_eq!("example.com", url.host);
        assert_eq!("/api/data", url.path);
    }
}
