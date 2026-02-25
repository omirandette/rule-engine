use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};
use rule_engine::rule::{Condition, Operator, Rule, UrlPart};

static DOMAINS: &[&str] = &[
    "google.com", "facebook.com", "youtube.com", "amazon.com", "github.com",
    "netflix.com", "twitter.com", "linkedin.com", "reddit.com", "instagram.com",
    "microsoft.com", "apple.com", "stackoverflow.com", "wikipedia.org", "medium.com",
    "twitch.tv", "spotify.com", "dropbox.com", "slack.com", "zoom.us",
    "adobe.com", "salesforce.com", "shopify.com", "stripe.com", "paypal.com",
    "ebay.com", "walmart.com", "target.com", "bestbuy.com", "homedepot.com",
    "nytimes.com", "bbc.co.uk", "cnn.com", "reuters.com", "theguardian.com",
    "espn.com", "nba.com", "nfl.com", "mlb.com", "fifa.com",
    "booking.com", "airbnb.com", "expedia.com", "tripadvisor.com", "kayak.com",
    "uber.com", "lyft.com", "doordash.com", "grubhub.com", "instacart.com",
    "docker.com", "kubernetes.io", "terraform.io", "ansible.com", "jenkins.io",
    "mongodb.com", "postgresql.org", "mysql.com", "redis.io", "elasticsearch.co",
    "cloudflare.com", "fastly.com", "akamai.com", "digitalocean.com", "heroku.com",
    "vercel.com", "netlify.com", "gatsby.com", "nextjs.org", "svelte.dev",
    "rust-lang.org", "python.org", "golang.org", "nodejs.org", "ruby-lang.org",
    "spring.io", "django-project.com", "flask.palletsprojects.com", "express.com", "rails.org",
    "nvidia.com", "intel.com", "amd.com", "qualcomm.com", "samsung.com",
    "sony.com", "nintendo.com", "valve.com", "epic.com", "roblox.com",
    "pinterest.com", "tumblr.com", "snapchat.com", "tiktok.com", "discord.com",
    "signal.org", "telegram.org", "whatsapp.com", "messenger.com", "viber.com",
];

static TLDS: &[&str] = &[
    ".com", ".org", ".net", ".ca", ".co.uk", ".io", ".dev", ".us", ".tv", ".ru",
];

static BRAND_KEYWORDS: &[&str] = &[
    "google", "amazon", "apple", "microsoft", "shop", "news", "cloud",
    "dev", "tech", "game", "music", "video", "health", "finance", "travel",
];

static HOST_PREFIXES: &[&str] = &[
    "www.", "api.", "shop.", "blog.", "mail.", "m.", "dev.", "cdn.", "app.", "admin.",
];

static PATH_DIRS: &[&str] = &[
    "/api", "/admin", "/blog", "/category", "/products", "/users", "/search", "/docs",
    "/news", "/sport", "/music", "/video", "/health", "/finance", "/travel",
    "/login", "/signup", "/settings", "/profile", "/dashboard",
    "/images", "/assets", "/downloads", "/help",
];

static PATH_KEYWORDS: &[&str] = &[
    "sport", "news", "tech", "finance", "health", "travel", "music", "video",
    "game", "food", "fashion", "auto", "science", "education", "weather",
    "entertainment", "politics", "business", "culture", "lifestyle",
];

static FILE_EXTENSIONS: &[&str] = &[
    ".html", ".php", ".js", ".css", ".json", ".xml", ".png", ".jpg",
    ".pdf", ".svg", ".gif", ".webp", ".woff", ".ttf", ".ico", ".txt",
    ".csv", ".zip", ".tar", ".gz", ".mp4", ".mp3", ".webm", ".wasm",
];

static FILE_NAMES: &[&str] = &[
    "index", "main", "app", "style", "script", "data", "config",
    "logo", "favicon", "manifest", "robots", "sitemap", "feed",
];

static QUERY_PARAMS: &[&str] = &[
    "lang=en", "sort=date", "page=1", "utm_source=google", "ref=home",
    "category=electronics", "type=json", "format=xml", "debug=true", "v=2",
    "q=search", "id=12345", "token=abc", "limit=100", "offset=0",
    "filter=active", "mode=dark", "theme=default", "locale=en-US", "currency=USD",
    "size=large", "color=blue", "brand=nike", "year=2025",
];

// Large-scale vocabulary for 100K rule generation
static DOMAIN_WORDS_1: &[&str] = &[
    "tech", "cloud", "data", "web", "smart", "digital", "cyber", "global", "info", "open",
    "fast", "big", "meta", "hyper", "micro", "nano", "mega", "ultra", "super", "pro",
    "prime", "core", "edge", "one", "next", "top", "max", "first", "rapid", "blue",
    "green", "red", "deep", "true", "safe", "easy", "quick", "bright", "clear", "star",
    "sky", "sun", "sea", "fire", "iron", "rock", "wave", "pulse", "pixel", "byte",
    "code", "flex", "bold", "keen", "pure", "live", "real", "wise", "apex", "nova",
];

static DOMAIN_WORDS_2: &[&str] = &[
    "hub", "lab", "box", "flow", "base", "stack", "zone", "link", "point", "spot",
    "works", "ware", "soft", "forge", "craft", "grid", "mesh", "vault", "dock", "port",
    "mind", "node", "gate", "path", "stream", "bridge", "leap", "shift", "scale", "reach",
    "spark", "fuse", "sync", "dash", "lens", "logic", "play", "view", "form", "trace",
    "nest", "loop", "ping", "sign", "cast", "map", "kit", "bit", "tap", "run",
];

static LARGE_TLDS: &[&str] = &[
    ".com", ".org", ".net", ".io", ".co", ".dev", ".app", ".ai", ".tech", ".cloud",
    ".us", ".uk", ".ca", ".au", ".de", ".fr", ".jp", ".br", ".in", ".ru",
];

static LARGE_SUBDOMAINS: &[&str] = &[
    "www", "api", "app", "m", "mobile", "shop", "store", "blog", "news", "mail",
    "cdn", "static", "media", "img", "dev", "staging", "beta", "auth", "sso", "admin",
    "docs", "help", "support", "my", "account",
];

static LARGE_PATH_SEGMENTS: &[&str] = &[
    "/api", "/v1", "/v2", "/v3", "/admin", "/blog", "/category", "/products", "/users",
    "/search", "/docs", "/news", "/sport", "/music", "/video", "/health", "/finance",
    "/travel", "/login", "/signup", "/settings", "/profile", "/dashboard", "/images",
    "/assets", "/downloads", "/help", "/support", "/faq", "/about", "/contact", "/terms",
    "/privacy", "/checkout", "/cart", "/orders", "/inventory", "/catalog", "/reviews",
    "/wishlist", "/account", "/billing", "/subscription", "/notifications", "/messages",
    "/feed", "/analytics", "/reports", "/events", "/webhooks", "/teams", "/projects",
    "/tasks", "/wiki", "/forums", "/community", "/marketplace", "/enterprise", "/pricing",
    "/features", "/solutions", "/partners", "/careers", "/developers", "/resources",
    "/tutorials", "/guides", "/templates", "/embed", "/share", "/trending", "/popular",
    "/recent", "/archive", "/tags", "/collections", "/bundles", "/offers", "/campaigns",
    "/status",
];

static LARGE_PATH_KEYWORDS: &[&str] = &[
    "sport", "news", "tech", "finance", "health", "travel", "music", "video", "game",
    "food", "fashion", "auto", "science", "education", "weather", "entertainment",
    "politics", "business", "culture", "lifestyle", "shopping", "deals", "reviews",
    "trending", "popular", "featured", "latest", "premium", "free", "trial", "mobile",
    "desktop", "social", "local", "global", "custom", "personal", "shared", "public",
    "private", "internal", "external", "secure", "fast", "new", "updated", "archived",
    "daily", "weekly", "annual",
];

static LARGE_FILE_NAMES: &[&str] = &[
    "index", "main", "app", "style", "script", "data", "config", "logo", "favicon",
    "manifest", "robots", "sitemap", "feed", "bundle", "vendor", "runtime", "polyfill",
    "worker", "analytics", "tracker", "pixel", "consent", "header", "footer", "sidebar",
    "modal", "widget", "banner", "thumbnail", "preview", "avatar", "background", "hero",
    "icon", "report", "export", "upload", "download", "attachment", "readme",
];

static LARGE_FILE_EXTENSIONS: &[&str] = &[
    ".html", ".htm", ".php", ".jsp", ".asp", ".js", ".mjs", ".css", ".json", ".xml",
    ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico", ".pdf", ".woff2", ".woff",
    ".ttf", ".csv", ".zip", ".mp4", ".mp3", ".webm", ".wasm", ".txt", ".md", ".yaml",
];

static LARGE_QUERY_PARAMS: &[&str] = &[
    "lang=en", "lang=fr", "lang=de", "lang=es", "lang=ja", "lang=zh", "lang=ko",
    "lang=pt", "sort=date", "sort=price", "sort=name", "sort=rating", "sort=relevance",
    "sort=popular", "page=1", "page=2", "page=3", "page=5", "page=10", "page=20",
    "utm_source=google", "utm_source=facebook", "utm_source=twitter", "utm_source=email",
    "utm_source=linkedin", "utm_source=instagram", "utm_source=tiktok", "utm_source=reddit",
    "utm_medium=cpc", "utm_medium=organic", "utm_medium=social", "utm_medium=referral",
    "utm_medium=display", "utm_medium=email", "utm_medium=affiliate", "utm_medium=video",
    "utm_campaign=summer", "utm_campaign=winter", "utm_campaign=spring", "utm_campaign=launch",
    "utm_campaign=sale", "utm_campaign=brand", "utm_campaign=retarget", "utm_campaign=promo",
    "ref=home", "ref=search", "ref=social", "ref=email", "ref=ads", "ref=partner",
    "category=electronics", "category=clothing", "category=books", "category=food",
    "category=sports", "category=home", "category=beauty", "category=toys",
    "type=json", "type=xml", "type=csv", "type=html", "type=pdf", "type=rss",
    "format=json", "format=xml", "format=csv", "format=pdf", "format=html", "format=atom",
    "debug=true", "debug=false", "verbose=true", "test=true", "preview=true", "draft=true",
    "v=1", "v=2", "v=3", "v=4", "v=5",
    "q=search+terms", "q=buy+online", "q=how+to", "q=best+deals", "q=reviews",
    "id=12345", "id=67890", "id=11111", "id=99999", "id=55555",
    "token=abc123", "token=xyz789", "apikey=test", "session=active", "auth=bearer",
    "limit=10", "limit=25", "limit=50", "limit=100", "limit=500",
    "offset=0", "offset=10", "offset=20", "offset=50", "offset=100",
    "filter=active", "filter=pending", "filter=archived", "filter=all", "filter=new",
    "status=published", "status=draft", "status=review", "status=deleted",
    "mode=dark", "mode=light", "mode=auto", "mode=compact", "mode=full",
    "theme=default", "theme=modern", "theme=classic", "theme=minimal", "theme=dark",
    "view=grid", "view=list", "view=table", "view=card", "view=map",
    "locale=en-US", "locale=en-GB", "locale=fr-FR", "locale=de-DE", "locale=ja-JP",
    "currency=USD", "currency=EUR", "currency=GBP", "currency=JPY", "currency=CAD",
    "size=xs", "size=s", "size=m", "size=l", "size=xl",
    "color=red", "color=blue", "color=green", "color=black", "color=white",
    "brand=nike", "brand=adidas", "brand=puma", "brand=reebok", "brand=under-armour",
    "year=2024", "year=2025", "year=2026", "period=monthly", "period=yearly",
];

pub struct DataGenerator {
    rng: StdRng,
}

impl DataGenerator {
    pub fn new(seed: u64) -> Self {
        Self {
            rng: StdRng::seed_from_u64(seed),
        }
    }

    fn pick<'a>(&mut self, array: &[&'a str]) -> &'a str {
        let idx = self.rng.gen_range(0..array.len());
        array[idx]
    }

    fn random_priority(&mut self) -> i32 {
        let r: f64 = self.rng.r#gen();
        if r < 0.60 {
            self.rng.gen_range(1..=3)
        } else if r < 0.90 {
            self.rng.gen_range(4..=7)
        } else {
            self.rng.gen_range(8..=10)
        }
    }

    fn make_rule(&mut self, name: &str, part: UrlPart, operator: Operator, value: &str) -> Rule {
        let priority = self.random_priority();
        Rule::new(
            name,
            priority,
            vec![Condition::new(part, operator, value, false)],
            format!("{}-result", name),
        )
    }

    fn random_condition(&mut self) -> Condition {
        let parts = [UrlPart::Host, UrlPart::Path, UrlPart::File, UrlPart::Query];
        let ops = [Operator::Equals, Operator::Contains, Operator::StartsWith, Operator::EndsWith];
        let part = parts[self.rng.gen_range(0..parts.len())];
        let operator = ops[self.rng.gen_range(0..ops.len())];
        let value = match part {
            UrlPart::Host => self.random_host_value(operator),
            UrlPart::Path => self.random_path_value(operator),
            UrlPart::File => self.random_file_value(operator),
            UrlPart::Query => self.random_query_value(),
        };
        Condition::new(part, operator, &value, false)
    }

    fn random_host_value(&mut self, op: Operator) -> String {
        match op {
            Operator::Equals => format!("{}{}", self.pick(HOST_PREFIXES), self.pick(DOMAINS)),
            Operator::Contains => self.pick(BRAND_KEYWORDS).to_string(),
            Operator::StartsWith => self.pick(HOST_PREFIXES).to_string(),
            Operator::EndsWith => self.pick(TLDS).to_string(),
        }
    }

    fn random_path_value(&mut self, op: Operator) -> String {
        match op {
            Operator::Equals => format!("{}/{}", self.pick(PATH_DIRS), self.pick(PATH_KEYWORDS)),
            Operator::Contains => self.pick(PATH_KEYWORDS).to_string(),
            Operator::StartsWith => self.pick(PATH_DIRS).to_string(),
            Operator::EndsWith => format!("/{}", self.pick(PATH_KEYWORDS)),
        }
    }

    fn random_file_value(&mut self, op: Operator) -> String {
        match op {
            Operator::Equals => format!("{}{}", self.pick(FILE_NAMES), self.pick(FILE_EXTENSIONS)),
            Operator::Contains | Operator::StartsWith => self.pick(FILE_NAMES).to_string(),
            Operator::EndsWith => self.pick(FILE_EXTENSIONS).to_string(),
        }
    }

    fn random_query_value(&mut self) -> String {
        self.pick(QUERY_PARAMS).to_string()
    }

    fn random_path(&mut self) -> String {
        let depth = self.rng.gen_range(1..=3);
        let mut s = String::new();
        for _ in 0..depth {
            s.push_str(self.pick(PATH_DIRS));
        }
        s
    }

    fn random_file(&mut self) -> String {
        let r: f64 = self.rng.r#gen();
        if r < 0.7 {
            format!("/{}{}", self.pick(FILE_NAMES), self.pick(FILE_EXTENSIONS))
        } else {
            String::new()
        }
    }

    fn random_query(&mut self) -> String {
        let r: f64 = self.rng.r#gen();
        if r < 0.3 {
            format!("?{}", self.pick(QUERY_PARAMS))
        } else {
            String::new()
        }
    }

    /// Generates approximately 2000 benchmark rules.
    pub fn generate_rules(&mut self) -> Vec<Rule> {
        let mut rules = Vec::new();
        let mut id = 0;

        // Domain exact match (100)
        for domain in DOMAINS {
            rules.push(self.make_rule(&format!("domain-eq-{}", id), UrlPart::Host, Operator::Equals, domain));
            id += 1;
        }

        // Subdomain combos (200)
        for _ in 0..200 {
            let value = format!("{}{}", self.pick(HOST_PREFIXES), self.pick(DOMAINS));
            rules.push(self.make_rule(&format!("subdomain-eq-{}", id), UrlPart::Host, Operator::Equals, &value));
            id += 1;
        }

        // TLD match (10)
        for tld in TLDS {
            rules.push(self.make_rule(&format!("tld-ew-{}", id), UrlPart::Host, Operator::EndsWith, tld));
            id += 1;
        }

        // Brand substring (200)
        for _ in 0..200 {
            let mut keyword = self.pick(BRAND_KEYWORDS).to_string();
            let coin: bool = self.rng.r#gen();
            if coin {
                keyword.push_str(&self.pick(TLDS)[1..]);
            }
            rules.push(self.make_rule(&format!("brand-ct-{}", id), UrlPart::Host, Operator::Contains, &keyword));
            id += 1;
        }

        // Host prefix (200)
        for _ in 0..200 {
            let prefix = self.pick(HOST_PREFIXES);
            rules.push(self.make_rule(&format!("host-sw-{}", id), UrlPart::Host, Operator::StartsWith, prefix));
            id += 1;
        }

        // Domain suffix (200)
        for _ in 0..200 {
            let domain = self.pick(DOMAINS);
            let value = format!(".{}", domain);
            rules.push(self.make_rule(&format!("host-ew-{}", id), UrlPart::Host, Operator::EndsWith, &value));
            id += 1;
        }

        // File extension (24)
        for ext in FILE_EXTENSIONS {
            rules.push(self.make_rule(&format!("file-ext-{}", id), UrlPart::File, Operator::EndsWith, ext));
            id += 1;
        }

        // File exact (40)
        for _ in 0..40 {
            let name = format!("{}{}", self.pick(FILE_NAMES), self.pick(FILE_EXTENSIONS));
            rules.push(self.make_rule(&format!("file-eq-{}", id), UrlPart::File, Operator::Equals, &name));
            id += 1;
        }

        // File prefix (13)
        for name in FILE_NAMES {
            rules.push(self.make_rule(&format!("file-sw-{}", id), UrlPart::File, Operator::StartsWith, name));
            id += 1;
        }

        // File contains (13)
        for name in FILE_NAMES {
            rules.push(self.make_rule(&format!("file-ct-{}", id), UrlPart::File, Operator::Contains, name));
            id += 1;
        }

        // Query contains (24)
        for param in QUERY_PARAMS {
            rules.push(self.make_rule(&format!("query-ct-{}", id), UrlPart::Query, Operator::Contains, param));
            id += 1;
        }

        // Query starts_with (24)
        for param in QUERY_PARAMS {
            rules.push(self.make_rule(&format!("query-sw-{}", id), UrlPart::Query, Operator::StartsWith, param));
            id += 1;
        }

        // Query ends_with (24)
        for param in QUERY_PARAMS {
            rules.push(self.make_rule(&format!("query-ew-{}", id), UrlPart::Query, Operator::EndsWith, param));
            id += 1;
        }

        // Query equals (24)
        for param in QUERY_PARAMS {
            rules.push(self.make_rule(&format!("query-eq-{}", id), UrlPart::Query, Operator::Equals, param));
            id += 1;
        }

        // Path prefix (24)
        for dir in PATH_DIRS {
            rules.push(self.make_rule(&format!("path-sw-{}", id), UrlPart::Path, Operator::StartsWith, dir));
            id += 1;
        }

        // Path contains (20)
        for _ in 0..20 {
            let kw = self.pick(PATH_KEYWORDS);
            rules.push(self.make_rule(&format!("path-ct-{}", id), UrlPart::Path, Operator::Contains, kw));
            id += 1;
        }

        // Path exact (75)
        for _ in 0..75 {
            let path = format!("{}/{}", self.pick(PATH_DIRS), self.pick(PATH_KEYWORDS));
            rules.push(self.make_rule(&format!("path-eq-{}", id), UrlPart::Path, Operator::Equals, &path));
            id += 1;
        }

        // Path ends_with (75)
        for _ in 0..75 {
            let suffix = format!("/{}{}", self.pick(PATH_KEYWORDS), self.pick(FILE_EXTENSIONS));
            rules.push(self.make_rule(&format!("path-ew-{}", id), UrlPart::Path, Operator::EndsWith, &suffix));
            id += 1;
        }

        // Compound rules (200)
        for _ in 0..200 {
            let cond_count = self.rng.gen_range(2..=3);
            let conditions: Vec<Condition> = (0..cond_count).map(|_| self.random_condition()).collect();
            let priority = self.random_priority();
            rules.push(Rule::new(format!("compound-{}", id), priority, conditions, "compound-match"));
            id += 1;
        }

        // Negated variants (200)
        for _ in 0..200 {
            let cond = self.random_condition();
            let negated = Condition::new(cond.part, cond.operator, &cond.value, true);
            let priority = self.random_priority();
            rules.push(Rule::new(format!("negated-{}", id), priority, vec![negated], "negated-match"));
            id += 1;
        }

        let _ = id;
        rules
    }

    /// Generates approximately 200,000 benchmark URLs.
    pub fn generate_urls(&mut self) -> Vec<String> {
        let mut urls = Vec::with_capacity(200_000);

        // ~80K: known domains with random paths/files/queries
        for _ in 0..80_000 {
            let domain = self.pick(DOMAINS);
            let path = self.random_path();
            let file = self.random_file();
            let query = self.random_query();
            urls.push(format!("https://{}{}{}{}", domain, path, file, query));
        }

        // ~40K: prefixed hosts
        for _ in 0..40_000 {
            let host = format!("{}{}", self.pick(HOST_PREFIXES), self.pick(DOMAINS));
            let path = self.random_path();
            let file = self.random_file();
            let query = self.random_query();
            urls.push(format!("https://{}{}{}{}", host, path, file, query));
        }

        // ~40K: random non-matching hosts
        for i in 0..40_000 {
            let path = self.random_path();
            let file = self.random_file();
            urls.push(format!("https://random{}.example.test{}{}", i, path, file));
        }

        // ~20K: designed for query matching
        for _ in 0..20_000 {
            let domain = self.pick(DOMAINS);
            let param = self.pick(QUERY_PARAMS);
            let extra_param = self.pick(QUERY_PARAMS);
            let path = self.random_path();
            let file = self.random_file();
            urls.push(format!("https://{}{}{}?{}&{}", domain, path, file, param, extra_param));
        }

        // ~20K: designed for path matching
        for _ in 0..20_000 {
            let domain = self.pick(DOMAINS);
            let dir = self.pick(PATH_DIRS);
            let keyword = self.pick(PATH_KEYWORDS);
            let file = self.random_file();
            urls.push(format!("https://{}{}/{}{}", domain, dir, keyword, file));
        }

        // Shuffle
        use rand::seq::SliceRandom;
        urls.shuffle(&mut self.rng);
        urls
    }

    // === Large-scale generation (100K rules) ===

    fn generate_domain(&self, index: usize) -> String {
        let tld_idx = index % LARGE_TLDS.len();
        let remaining = index / LARGE_TLDS.len();
        let w2_idx = remaining % DOMAIN_WORDS_2.len();
        let w1_idx = (remaining / DOMAIN_WORDS_2.len()) % DOMAIN_WORDS_1.len();
        format!("{}{}{}", DOMAIN_WORDS_1[w1_idx], DOMAIN_WORDS_2[w2_idx], LARGE_TLDS[tld_idx])
    }

    fn large_random_condition(&mut self) -> Condition {
        let parts = [UrlPart::Host, UrlPart::Path, UrlPart::File, UrlPart::Query];
        let ops = [Operator::Equals, Operator::Contains, Operator::StartsWith, Operator::EndsWith];
        let part = parts[self.rng.gen_range(0..parts.len())];
        let operator = ops[self.rng.gen_range(0..ops.len())];
        let value = match part {
            UrlPart::Host => self.large_random_host_value(operator),
            UrlPart::Path => self.large_random_path_value(operator),
            UrlPart::File => self.large_random_file_value(operator),
            UrlPart::Query => self.pick(LARGE_QUERY_PARAMS).to_string(),
        };
        Condition::new(part, operator, &value, false)
    }

    fn large_random_host_value(&mut self, op: Operator) -> String {
        match op {
            Operator::Equals => {
                let idx = self.rng.gen_range(0..20_000);
                let domain = self.generate_domain(idx);
                let coin: bool = self.rng.r#gen();
                if coin {
                    format!("{}.{}", self.pick(LARGE_SUBDOMAINS), domain)
                } else {
                    domain
                }
            }
            Operator::Contains => self.pick(BRAND_KEYWORDS).to_string(),
            Operator::StartsWith => format!("{}.", self.pick(LARGE_SUBDOMAINS)),
            Operator::EndsWith => {
                let coin: bool = self.rng.r#gen();
                if coin {
                    self.pick(LARGE_TLDS).to_string()
                } else {
                    let idx = self.rng.gen_range(0..20_000);
                    format!(".{}", self.generate_domain(idx))
                }
            }
        }
    }

    fn large_random_path_value(&mut self, op: Operator) -> String {
        match op {
            Operator::Equals => format!("{}/{}", self.pick(LARGE_PATH_SEGMENTS), self.pick(LARGE_PATH_KEYWORDS)),
            Operator::Contains => self.pick(LARGE_PATH_KEYWORDS).to_string(),
            Operator::StartsWith => self.pick(LARGE_PATH_SEGMENTS).to_string(),
            Operator::EndsWith => format!("/{}", self.pick(LARGE_PATH_KEYWORDS)),
        }
    }

    fn large_random_file_value(&mut self, op: Operator) -> String {
        match op {
            Operator::Equals => format!("{}{}", self.pick(LARGE_FILE_NAMES), self.pick(LARGE_FILE_EXTENSIONS)),
            Operator::Contains | Operator::StartsWith => self.pick(LARGE_FILE_NAMES).to_string(),
            Operator::EndsWith => self.pick(LARGE_FILE_EXTENSIONS).to_string(),
        }
    }

    fn large_random_path(&mut self) -> String {
        let depth = self.rng.gen_range(1..=4);
        let mut s = String::new();
        for _ in 0..depth {
            s.push_str(self.pick(LARGE_PATH_SEGMENTS));
        }
        s
    }

    fn large_random_file(&mut self) -> String {
        let r: f64 = self.rng.r#gen();
        if r < 0.7 {
            format!("/{}{}", self.pick(LARGE_FILE_NAMES), self.pick(LARGE_FILE_EXTENSIONS))
        } else {
            String::new()
        }
    }

    fn large_random_query(&mut self) -> String {
        let r: f64 = self.rng.r#gen();
        if r < 0.3 {
            let params = self.rng.gen_range(1..=3);
            let mut s = String::from("?");
            for i in 0..params {
                if i > 0 { s.push('&'); }
                s.push_str(self.pick(LARGE_QUERY_PARAMS));
            }
            s
        } else {
            String::new()
        }
    }

    /// Generates approximately 100,000 benchmark rules.
    pub fn generate_large_rule_set(&mut self) -> Vec<Rule> {
        let mut rules = Vec::with_capacity(100_000);
        let mut id = 0usize;

        // Host-based rules (50,000)
        // Exact domain matches (20,000)
        for i in 0..20_000 {
            let domain = self.generate_domain(i);
            rules.push(self.make_rule(&format!("pub-domain-{}", id), UrlPart::Host, Operator::Equals, &domain));
            id += 1;
        }

        // Subdomain exact matches (10,000)
        for _ in 0..10_000 {
            let idx = self.rng.gen_range(0..20_000);
            let sub = format!("{}.{}", self.pick(LARGE_SUBDOMAINS), self.generate_domain(idx));
            rules.push(self.make_rule(&format!("sub-domain-{}", id), UrlPart::Host, Operator::Equals, &sub));
            id += 1;
        }

        // Host ends_with domain suffix (5,000)
        for i in 0..5_000 {
            let domain = if i < DOMAINS.len() {
                DOMAINS[i].to_string()
            } else {
                let idx = self.rng.gen_range(0..20_000);
                self.generate_domain(idx)
            };
            let value = format!(".{}", domain);
            rules.push(self.make_rule(&format!("host-suffix-{}", id), UrlPart::Host, Operator::EndsWith, &value));
            id += 1;
        }

        // Host contains keyword (5,000)
        for _ in 0..5_000 {
            let coin: bool = self.rng.r#gen();
            let mut keyword = if coin {
                self.pick(BRAND_KEYWORDS).to_string()
            } else {
                self.pick(LARGE_PATH_KEYWORDS).to_string()
            };
            if self.rng.gen_range(0..3) == 0 {
                keyword.push_str(&self.pick(LARGE_TLDS)[1..]);
            }
            rules.push(self.make_rule(&format!("brand-kw-{}", id), UrlPart::Host, Operator::Contains, &keyword));
            id += 1;
        }

        // Host starts_with (5,000)
        for _ in 0..5_000 {
            let value = format!("{}.", self.pick(LARGE_SUBDOMAINS));
            rules.push(self.make_rule(&format!("host-pre-{}", id), UrlPart::Host, Operator::StartsWith, &value));
            id += 1;
        }

        // Host ends_with TLD (5,000)
        for _ in 0..5_000 {
            let tld = self.pick(LARGE_TLDS);
            rules.push(self.make_rule(&format!("tld-geo-{}", id), UrlPart::Host, Operator::EndsWith, tld));
            id += 1;
        }

        // Path-based rules (15,000)
        for _ in 0..5_000 {
            let mut path = self.pick(LARGE_PATH_SEGMENTS).to_string();
            let coin: bool = self.rng.r#gen();
            if coin { path.push_str(self.pick(LARGE_PATH_SEGMENTS)); }
            rules.push(self.make_rule(&format!("path-route-{}", id), UrlPart::Path, Operator::StartsWith, &path));
            id += 1;
        }
        for _ in 0..4_000 {
            let kw = self.pick(LARGE_PATH_KEYWORDS);
            rules.push(self.make_rule(&format!("path-class-{}", id), UrlPart::Path, Operator::Contains, kw));
            id += 1;
        }
        for _ in 0..3_000 {
            let mut path = format!("{}/{}", self.pick(LARGE_PATH_SEGMENTS), self.pick(LARGE_PATH_KEYWORDS));
            if self.rng.gen_range(0..3) == 0 {
                path = format!("{}/{}", path, self.pick(LARGE_PATH_KEYWORDS));
            }
            rules.push(self.make_rule(&format!("path-page-{}", id), UrlPart::Path, Operator::Equals, &path));
            id += 1;
        }
        for _ in 0..3_000 {
            let mut suffix = format!("/{}", self.pick(LARGE_PATH_KEYWORDS));
            let coin: bool = self.rng.r#gen();
            if coin { suffix.push_str(self.pick(LARGE_FILE_EXTENSIONS)); }
            rules.push(self.make_rule(&format!("path-suf-{}", id), UrlPart::Path, Operator::EndsWith, &suffix));
            id += 1;
        }

        // File-based rules (8,000)
        for _ in 0..3_000 {
            let ext = self.pick(LARGE_FILE_EXTENSIONS);
            rules.push(self.make_rule(&format!("file-ext-{}", id), UrlPart::File, Operator::EndsWith, ext));
            id += 1;
        }
        for _ in 0..2_000 {
            let name = format!("{}{}", self.pick(LARGE_FILE_NAMES), self.pick(LARGE_FILE_EXTENSIONS));
            rules.push(self.make_rule(&format!("file-name-{}", id), UrlPart::File, Operator::Equals, &name));
            id += 1;
        }
        for _ in 0..1_500 {
            let name = self.pick(LARGE_FILE_NAMES);
            rules.push(self.make_rule(&format!("file-pre-{}", id), UrlPart::File, Operator::StartsWith, name));
            id += 1;
        }
        for _ in 0..1_500 {
            let name = self.pick(LARGE_FILE_NAMES);
            rules.push(self.make_rule(&format!("file-kw-{}", id), UrlPart::File, Operator::Contains, name));
            id += 1;
        }

        // Query-based rules (7,000)
        for _ in 0..2_000 {
            let param = self.pick(LARGE_QUERY_PARAMS);
            rules.push(self.make_rule(&format!("query-detect-{}", id), UrlPart::Query, Operator::Contains, param));
            id += 1;
        }
        for _ in 0..2_000 {
            let param = self.pick(LARGE_QUERY_PARAMS);
            rules.push(self.make_rule(&format!("query-pre-{}", id), UrlPart::Query, Operator::StartsWith, param));
            id += 1;
        }
        for _ in 0..1_500 {
            let param = self.pick(LARGE_QUERY_PARAMS);
            rules.push(self.make_rule(&format!("query-suf-{}", id), UrlPart::Query, Operator::EndsWith, param));
            id += 1;
        }
        for _ in 0..1_500 {
            let param = self.pick(LARGE_QUERY_PARAMS);
            rules.push(self.make_rule(&format!("query-exact-{}", id), UrlPart::Query, Operator::Equals, param));
            id += 1;
        }

        // Compound rules (15,000)
        for _ in 0..8_000 {
            let conditions = vec![self.large_random_condition(), self.large_random_condition()];
            let priority = self.random_priority();
            rules.push(Rule::new(format!("compound2-{}", id), priority, conditions, "compound-match"));
            id += 1;
        }
        for _ in 0..5_000 {
            let conditions = vec![
                self.large_random_condition(), self.large_random_condition(), self.large_random_condition(),
            ];
            let priority = self.random_priority();
            rules.push(Rule::new(format!("compound3-{}", id), priority, conditions, "compound-match"));
            id += 1;
        }
        for _ in 0..2_000 {
            let conditions = vec![
                self.large_random_condition(), self.large_random_condition(),
                self.large_random_condition(), self.large_random_condition(),
            ];
            let priority = self.random_priority();
            rules.push(Rule::new(format!("compound4-{}", id), priority, conditions, "compound-match"));
            id += 1;
        }

        // Negated rules (5,000)
        for _ in 0..3_000 {
            let cond = self.large_random_condition();
            let negated = Condition::new(cond.part, cond.operator, &cond.value, true);
            let priority = self.random_priority();
            rules.push(Rule::new(format!("negated-{}", id), priority, vec![negated], "negated-match"));
            id += 1;
        }
        for _ in 0..2_000 {
            let positive = self.large_random_condition();
            let neg = self.large_random_condition();
            let negated = Condition::new(neg.part, neg.operator, &neg.value, true);
            let priority = self.random_priority();
            rules.push(Rule::new(format!("mixed-neg-{}", id), priority, vec![positive, negated], "mixed-neg-match"));
            id += 1;
        }

        let _ = id;
        rules
    }

    /// Generates approximately 200,000 URLs for large rule set benchmarking.
    pub fn generate_large_url_set(&mut self) -> Vec<String> {
        let mut urls = Vec::with_capacity(200_000);

        // ~60K: known static domains
        for _ in 0..60_000 {
            let domain = self.pick(DOMAINS);
            let path = self.large_random_path();
            let file = self.large_random_file();
            let query = self.large_random_query();
            urls.push(format!("https://{}{}{}{}", domain, path, file, query));
        }

        // ~30K: prefixed known domains
        for _ in 0..30_000 {
            let host = format!("{}.{}", self.pick(LARGE_SUBDOMAINS), self.pick(DOMAINS));
            let path = self.large_random_path();
            let file = self.large_random_file();
            let query = self.large_random_query();
            urls.push(format!("https://{}{}{}{}", host, path, file, query));
        }

        // ~30K: generated domains
        for _ in 0..30_000 {
            let idx = self.rng.gen_range(0..20_000);
            let domain = self.generate_domain(idx);
            let path = self.large_random_path();
            let file = self.large_random_file();
            let query = self.large_random_query();
            urls.push(format!("https://{}{}{}{}", domain, path, file, query));
        }

        // ~20K: subdomained generated domains
        for _ in 0..20_000 {
            let idx = self.rng.gen_range(0..20_000);
            let host = format!("{}.{}", self.pick(LARGE_SUBDOMAINS), self.generate_domain(idx));
            let path = self.large_random_path();
            let file = self.large_random_file();
            let query = self.large_random_query();
            urls.push(format!("https://{}{}{}{}", host, path, file, query));
        }

        // ~20K: random non-matching hosts
        for i in 0..20_000 {
            let path = self.large_random_path();
            let file = self.large_random_file();
            urls.push(format!("https://random{}.example.test{}{}", i, path, file));
        }

        // ~20K: query-focused
        for _ in 0..20_000 {
            let domain = self.pick(DOMAINS);
            let p1 = self.pick(LARGE_QUERY_PARAMS);
            let p2 = self.pick(LARGE_QUERY_PARAMS);
            let path = self.large_random_path();
            let file = self.large_random_file();
            urls.push(format!("https://{}{}{}?{}&{}", domain, path, file, p1, p2));
        }

        // ~20K: path-focused
        for _ in 0..20_000 {
            let domain = self.pick(DOMAINS);
            let dir = self.pick(LARGE_PATH_SEGMENTS);
            let keyword = self.pick(LARGE_PATH_KEYWORDS);
            let file = self.large_random_file();
            urls.push(format!("https://{}{}/{}{}", domain, dir, keyword, file));
        }

        use rand::seq::SliceRandom;
        urls.shuffle(&mut self.rng);
        urls
    }
}
