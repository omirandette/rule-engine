package com.ruleengine.benchmark;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.UrlPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates synthetic rules and URLs for benchmarking the rule engine.
 *
 * <p>All data is generated deterministically from a seeded {@link Random} for reproducibility.
 */
public class DataGenerator {

    private static final String[] DOMAINS = {
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
        "signal.org", "telegram.org", "whatsapp.com", "messenger.com", "viber.com"
    };

    private static final String[] TLDS = {
        ".com", ".org", ".net", ".ca", ".co.uk", ".io", ".dev", ".us", ".tv", ".ru"
    };

    private static final String[] BRAND_KEYWORDS = {
        "google", "amazon", "apple", "microsoft", "shop", "news", "cloud",
        "dev", "tech", "game", "music", "video", "health", "finance", "travel"
    };

    private static final String[] HOST_PREFIXES = {
        "www.", "api.", "shop.", "blog.", "mail.", "m.", "dev.", "cdn.", "app.", "admin."
    };

    private static final String[] PATH_DIRS = {
        "/api", "/admin", "/blog", "/category", "/products", "/users", "/search", "/docs",
        "/news", "/sport", "/music", "/video", "/health", "/finance", "/travel",
        "/login", "/signup", "/settings", "/profile", "/dashboard",
        "/images", "/assets", "/downloads", "/help"
    };

    private static final String[] PATH_KEYWORDS = {
        "sport", "news", "tech", "finance", "health", "travel", "music", "video",
        "game", "food", "fashion", "auto", "science", "education", "weather",
        "entertainment", "politics", "business", "culture", "lifestyle"
    };

    private static final String[] FILE_EXTENSIONS = {
        ".html", ".php", ".js", ".css", ".json", ".xml", ".png", ".jpg",
        ".pdf", ".svg", ".gif", ".webp", ".woff", ".ttf", ".ico", ".txt",
        ".csv", ".zip", ".tar", ".gz", ".mp4", ".mp3", ".webm", ".wasm"
    };

    private static final String[] FILE_NAMES = {
        "index", "main", "app", "style", "script", "data", "config",
        "logo", "favicon", "manifest", "robots", "sitemap", "feed"
    };

    private static final String[] QUERY_PARAMS = {
        "lang=en", "sort=date", "page=1", "utm_source=google", "ref=home",
        "category=electronics", "type=json", "format=xml", "debug=true", "v=2",
        "q=search", "id=12345", "token=abc", "limit=100", "offset=0",
        "filter=active", "mode=dark", "theme=default", "locale=en-US", "currency=USD",
        "size=large", "color=blue", "brand=nike", "year=2025"
    };

    // === Large-scale vocabulary for 100K rule generation ===

    private static final String[] DOMAIN_WORDS_1 = {
        "tech", "cloud", "data", "web", "smart", "digital", "cyber", "global", "info", "open",
        "fast", "big", "meta", "hyper", "micro", "nano", "mega", "ultra", "super", "pro",
        "prime", "core", "edge", "one", "next", "top", "max", "first", "rapid", "blue",
        "green", "red", "deep", "true", "safe", "easy", "quick", "bright", "clear", "star",
        "sky", "sun", "sea", "fire", "iron", "rock", "wave", "pulse", "pixel", "byte",
        "code", "flex", "bold", "keen", "pure", "live", "real", "wise", "apex", "nova"
    };

    private static final String[] DOMAIN_WORDS_2 = {
        "hub", "lab", "box", "flow", "base", "stack", "zone", "link", "point", "spot",
        "works", "ware", "soft", "forge", "craft", "grid", "mesh", "vault", "dock", "port",
        "mind", "node", "gate", "path", "stream", "bridge", "leap", "shift", "scale", "reach",
        "spark", "fuse", "sync", "dash", "lens", "logic", "play", "view", "form", "trace",
        "nest", "loop", "ping", "sign", "cast", "map", "kit", "bit", "tap", "run"
    };

    private static final String[] LARGE_TLDS = {
        ".com", ".org", ".net", ".io", ".co", ".dev", ".app", ".ai", ".tech", ".cloud",
        ".us", ".uk", ".ca", ".au", ".de", ".fr", ".jp", ".br", ".in", ".ru"
    };

    private static final String[] LARGE_SUBDOMAINS = {
        "www", "api", "app", "m", "mobile", "shop", "store", "blog", "news", "mail",
        "cdn", "static", "media", "img", "dev", "staging", "beta", "auth", "sso", "admin",
        "docs", "help", "support", "my", "account"
    };

    private static final String[] LARGE_PATH_SEGMENTS = {
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
        "/status"
    };

    private static final String[] LARGE_PATH_KEYWORDS = {
        "sport", "news", "tech", "finance", "health", "travel", "music", "video", "game",
        "food", "fashion", "auto", "science", "education", "weather", "entertainment",
        "politics", "business", "culture", "lifestyle", "shopping", "deals", "reviews",
        "trending", "popular", "featured", "latest", "premium", "free", "trial", "mobile",
        "desktop", "social", "local", "global", "custom", "personal", "shared", "public",
        "private", "internal", "external", "secure", "fast", "new", "updated", "archived",
        "daily", "weekly", "annual"
    };

    private static final String[] LARGE_FILE_NAMES = {
        "index", "main", "app", "style", "script", "data", "config", "logo", "favicon",
        "manifest", "robots", "sitemap", "feed", "bundle", "vendor", "runtime", "polyfill",
        "worker", "analytics", "tracker", "pixel", "consent", "header", "footer", "sidebar",
        "modal", "widget", "banner", "thumbnail", "preview", "avatar", "background", "hero",
        "icon", "report", "export", "upload", "download", "attachment", "readme"
    };

    private static final String[] LARGE_FILE_EXTENSIONS = {
        ".html", ".htm", ".php", ".jsp", ".asp", ".js", ".mjs", ".css", ".json", ".xml",
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico", ".pdf", ".woff2", ".woff",
        ".ttf", ".csv", ".zip", ".mp4", ".mp3", ".webm", ".wasm", ".txt", ".md", ".yaml"
    };

    private static final String[] LARGE_QUERY_PARAMS = {
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
        "year=2024", "year=2025", "year=2026", "period=monthly", "period=yearly"
    };

    private final Random random;

    /**
     * Creates a data generator with the given random seed.
     *
     * @param seed the random seed for reproducible generation
     */
    public DataGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates approximately 2000 benchmark rules across various categories.
     *
     * @return an unmodifiable list of generated rules
     */
    public List<Rule> generateRules() {
        List<Rule> rules = new ArrayList<>();
        int id = 0;

        // Domain exact match (100)
        for (String domain : DOMAINS) {
            rules.add(rule("domain-eq-" + id++, UrlPart.HOST, Operator.EQUALS, domain));
        }

        // Subdomain combos (200): prefix + domain
        for (int i = 0; i < 200; i++) {
            String prefix = pick(HOST_PREFIXES);
            String domain = pick(DOMAINS);
            rules.add(rule("subdomain-eq-" + id++, UrlPart.HOST, Operator.EQUALS, prefix + domain));
        }

        // TLD match (10)
        for (String tld : TLDS) {
            rules.add(rule("tld-ew-" + id++, UrlPart.HOST, Operator.ENDS_WITH, tld));
        }

        // Brand substring (200): keywords + domain variations
        for (int i = 0; i < 200; i++) {
            String keyword = pick(BRAND_KEYWORDS);
            if (random.nextBoolean()) {
                keyword = keyword + pick(TLDS).substring(1); // e.g. "googlecom"
            }
            rules.add(rule("brand-ct-" + id++, UrlPart.HOST, Operator.CONTAINS, keyword));
        }

        // Host prefix (200)
        for (int i = 0; i < 200; i++) {
            rules.add(rule("host-sw-" + id++, UrlPart.HOST, Operator.STARTS_WITH, pick(HOST_PREFIXES)));
        }

        // Domain suffix (200): .domain
        for (int i = 0; i < 200; i++) {
            String domain = pick(DOMAINS);
            rules.add(rule("host-ew-" + id++, UrlPart.HOST, Operator.ENDS_WITH, "." + domain));
        }

        // File extension (24)
        for (String ext : FILE_EXTENSIONS) {
            rules.add(rule("file-ext-" + id++, UrlPart.FILE, Operator.ENDS_WITH, ext));
        }

        // File exact (40): name + ext combos
        for (int i = 0; i < 40; i++) {
            String name = pick(FILE_NAMES) + pick(FILE_EXTENSIONS);
            rules.add(rule("file-eq-" + id++, UrlPart.FILE, Operator.EQUALS, name));
        }

        // File prefix (13)
        for (String name : FILE_NAMES) {
            rules.add(rule("file-sw-" + id++, UrlPart.FILE, Operator.STARTS_WITH, name));
        }

        // File contains (13)
        for (String name : FILE_NAMES) {
            rules.add(rule("file-ct-" + id++, UrlPart.FILE, Operator.CONTAINS, name));
        }

        // Query contains (24)
        for (String param : QUERY_PARAMS) {
            rules.add(rule("query-ct-" + id++, UrlPart.QUERY, Operator.CONTAINS, param));
        }

        // Query starts_with (24)
        for (String param : QUERY_PARAMS) {
            rules.add(rule("query-sw-" + id++, UrlPart.QUERY, Operator.STARTS_WITH, param));
        }

        // Query ends_with (24)
        for (String param : QUERY_PARAMS) {
            rules.add(rule("query-ew-" + id++, UrlPart.QUERY, Operator.ENDS_WITH, param));
        }

        // Query equals (24)
        for (String param : QUERY_PARAMS) {
            rules.add(rule("query-eq-" + id++, UrlPart.QUERY, Operator.EQUALS, param));
        }

        // Path prefix (24)
        for (String dir : PATH_DIRS) {
            rules.add(rule("path-sw-" + id++, UrlPart.PATH, Operator.STARTS_WITH, dir));
        }

        // Path contains (20)
        for (int i = 0; i < 20; i++) {
            rules.add(rule("path-ct-" + id++, UrlPart.PATH, Operator.CONTAINS, pick(PATH_KEYWORDS)));
        }

        // Path exact (75)
        for (int i = 0; i < 75; i++) {
            String path = pick(PATH_DIRS) + "/" + pick(PATH_KEYWORDS);
            rules.add(rule("path-eq-" + id++, UrlPart.PATH, Operator.EQUALS, path));
        }

        // Path ends_with (75)
        for (int i = 0; i < 75; i++) {
            String suffix = "/" + pick(PATH_KEYWORDS) + pick(FILE_EXTENSIONS);
            rules.add(rule("path-ew-" + id++, UrlPart.PATH, Operator.ENDS_WITH, suffix));
        }

        // Compound rules with 2-3 conditions (200)
        for (int i = 0; i < 200; i++) {
            int condCount = random.nextInt(2) + 2; // 2 or 3
            List<Condition> conditions = new ArrayList<>();
            for (int c = 0; c < condCount; c++) {
                conditions.add(randomCondition());
            }
            rules.add(new Rule("compound-" + id++, randomPriority(), conditions, "compound-match"));
        }

        // Negated variants (200)
        for (int i = 0; i < 200; i++) {
            Condition cond = randomCondition();
            Condition negated = new Condition(cond.part(), cond.operator(), cond.value(), true);
            rules.add(new Rule("negated-" + id++, randomPriority(), List.of(negated), "negated-match"));
        }

        return Collections.unmodifiableList(rules);
    }

    /**
     * Generates approximately 200,000 benchmark URLs with a mix of matching and non-matching patterns.
     *
     * @return an unmodifiable list of generated URL strings
     */
    public List<String> generateUrls() {
        List<String> urls = new ArrayList<>(200_000);

        // ~80K: known domains with random paths/files/queries
        for (int i = 0; i < 80_000; i++) {
            String domain = pick(DOMAINS);
            urls.add("https://" + domain + randomPath() + randomFile() + randomQuery());
        }

        // ~40K: prefixed hosts (www.google.com, api.amazon.com, etc.)
        for (int i = 0; i < 40_000; i++) {
            String host = pick(HOST_PREFIXES) + pick(DOMAINS);
            urls.add("https://" + host + randomPath() + randomFile() + randomQuery());
        }

        // ~40K: random non-matching hosts
        for (int i = 0; i < 40_000; i++) {
            String host = "random" + i + ".example.test";
            urls.add("https://" + host + randomPath() + randomFile());
        }

        // ~20K: designed for query matching
        for (int i = 0; i < 20_000; i++) {
            String domain = pick(DOMAINS);
            String param = pick(QUERY_PARAMS);
            String extraParam = pick(QUERY_PARAMS);
            urls.add("https://" + domain + randomPath() + randomFile() + "?" + param + "&" + extraParam);
        }

        // ~20K: designed for path matching
        for (int i = 0; i < 20_000; i++) {
            String domain = pick(DOMAINS);
            String dir = pick(PATH_DIRS);
            String keyword = pick(PATH_KEYWORDS);
            urls.add("https://" + domain + dir + "/" + keyword + randomFile());
        }

        Collections.shuffle(urls, random);
        return Collections.unmodifiableList(urls);
    }

    private Rule rule(String name, UrlPart part, Operator operator, String value) {
        Condition condition = new Condition(part, operator, value, false);
        return new Rule(name, randomPriority(), List.of(condition), name + "-result");
    }

    private int randomPriority() {
        double r = random.nextDouble();
        if (r < 0.60) {
            return random.nextInt(3) + 1;   // 1-3 (60%)
        } else if (r < 0.90) {
            return random.nextInt(4) + 4;   // 4-7 (30%)
        } else {
            return random.nextInt(3) + 8;   // 8-10 (10%)
        }
    }

    private Condition randomCondition() {
        UrlPart part = pickEnum(UrlPart.values());
        Operator operator = pickEnum(Operator.values());
        String value = switch (part) {
            case HOST -> randomHostValue(operator);
            case PATH -> randomPathValue(operator);
            case FILE -> randomFileValue(operator);
            case QUERY -> randomQueryValue(operator);
        };
        return new Condition(part, operator, value, false);
    }

    private String randomHostValue(Operator op) {
        return switch (op) {
            case EQUALS -> pick(HOST_PREFIXES) + pick(DOMAINS);
            case CONTAINS -> pick(BRAND_KEYWORDS);
            case STARTS_WITH -> pick(HOST_PREFIXES);
            case ENDS_WITH -> pick(TLDS);
        };
    }

    private String randomPathValue(Operator op) {
        return switch (op) {
            case EQUALS -> pick(PATH_DIRS) + "/" + pick(PATH_KEYWORDS);
            case CONTAINS -> pick(PATH_KEYWORDS);
            case STARTS_WITH -> pick(PATH_DIRS);
            case ENDS_WITH -> "/" + pick(PATH_KEYWORDS);
        };
    }

    private String randomFileValue(Operator op) {
        return switch (op) {
            case EQUALS -> pick(FILE_NAMES) + pick(FILE_EXTENSIONS);
            case CONTAINS -> pick(FILE_NAMES);
            case STARTS_WITH -> pick(FILE_NAMES);
            case ENDS_WITH -> pick(FILE_EXTENSIONS);
        };
    }

    private String randomQueryValue(Operator op) {
        return switch (op) {
            case EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH -> pick(QUERY_PARAMS);
        };
    }

    private String randomPath() {
        int depth = random.nextInt(3) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append(pick(PATH_DIRS));
        }
        return sb.toString();
    }

    private String randomFile() {
        if (random.nextDouble() < 0.7) {
            return "/" + pick(FILE_NAMES) + pick(FILE_EXTENSIONS);
        }
        return "";
    }

    private String randomQuery() {
        if (random.nextDouble() < 0.3) {
            return "?" + pick(QUERY_PARAMS);
        }
        return "";
    }

    private String pick(String[] array) {
        return array[random.nextInt(array.length)];
    }

    private <T> T pickEnum(T[] values) {
        return values[random.nextInt(values.length)];
    }

    // === Large-scale generation (100K rules) ===

    /**
     * Generates approximately 100,000 benchmark rules modeling realistic use cases.
     *
     * <p>Distribution models real-world patterns from ad-tech, content classification,
     * security filtering, and analytics:
     * <ul>
     *   <li>50,000 host-based rules (domain targeting, brand safety, geo-targeting)
     *   <li>15,000 path-based rules (content classification, routing, access control)
     *   <li>8,000 file-based rules (asset/resource classification)
     *   <li>7,000 query-based rules (tracking parameters, targeting)
     *   <li>15,000 compound rules (multi-condition business logic)
     *   <li>5,000 negated rules (exclusion patterns)
     * </ul>
     *
     * @return an unmodifiable list of generated rules
     */
    public List<Rule> generateLargeRuleSet() {
        List<Rule> rules = new ArrayList<>(100_000);
        int id = 0;

        // === Host-based rules (50,000) ===

        // Exact domain matches — ad-tech publisher allowlists/blocklists (20,000)
        for (int i = 0; i < 20_000; i++) {
            String domain = generateDomain(i);
            rules.add(rule("pub-domain-" + id++, UrlPart.HOST, Operator.EQUALS, domain));
        }

        // Subdomain exact matches — platform-specific targeting (10,000)
        for (int i = 0; i < 10_000; i++) {
            String sub = pick(LARGE_SUBDOMAINS) + "." + generateDomain(random.nextInt(20_000));
            rules.add(rule("sub-domain-" + id++, UrlPart.HOST, Operator.EQUALS, sub));
        }

        // Host ends_with domain suffix — *.google.com wildcard patterns (5,000)
        for (int i = 0; i < 5_000; i++) {
            String domain = i < DOMAINS.length
                    ? DOMAINS[i]
                    : generateDomain(random.nextInt(20_000));
            rules.add(rule("host-suffix-" + id++, UrlPart.HOST, Operator.ENDS_WITH,
                    "." + domain));
        }

        // Host contains keyword — brand safety rules (5,000)
        for (int i = 0; i < 5_000; i++) {
            String keyword = random.nextBoolean()
                    ? pick(BRAND_KEYWORDS)
                    : pick(LARGE_PATH_KEYWORDS);
            if (random.nextInt(3) == 0) {
                keyword = keyword + pick(LARGE_TLDS).substring(1);
            }
            rules.add(rule("brand-kw-" + id++, UrlPart.HOST, Operator.CONTAINS, keyword));
        }

        // Host starts_with — subdomain pattern targeting (5,000)
        for (int i = 0; i < 5_000; i++) {
            rules.add(rule("host-pre-" + id++, UrlPart.HOST, Operator.STARTS_WITH,
                    pick(LARGE_SUBDOMAINS) + "."));
        }

        // Host ends_with TLD — geo-targeting by country TLD (5,000)
        for (int i = 0; i < 5_000; i++) {
            rules.add(rule("tld-geo-" + id++, UrlPart.HOST, Operator.ENDS_WITH,
                    pick(LARGE_TLDS)));
        }

        // === Path-based rules (15,000) ===

        // Path starts_with — directory-based routing and access control (5,000)
        for (int i = 0; i < 5_000; i++) {
            String path = pick(LARGE_PATH_SEGMENTS);
            if (random.nextBoolean()) {
                path += pick(LARGE_PATH_SEGMENTS);
            }
            rules.add(rule("path-route-" + id++, UrlPart.PATH, Operator.STARTS_WITH, path));
        }

        // Path contains — content classification by keyword (4,000)
        for (int i = 0; i < 4_000; i++) {
            rules.add(rule("path-class-" + id++, UrlPart.PATH, Operator.CONTAINS,
                    pick(LARGE_PATH_KEYWORDS)));
        }

        // Path equals — specific page targeting (3,000)
        for (int i = 0; i < 3_000; i++) {
            String path = pick(LARGE_PATH_SEGMENTS) + "/" + pick(LARGE_PATH_KEYWORDS);
            if (random.nextInt(3) == 0) {
                path += "/" + pick(LARGE_PATH_KEYWORDS);
            }
            rules.add(rule("path-page-" + id++, UrlPart.PATH, Operator.EQUALS, path));
        }

        // Path ends_with — URL suffix patterns (3,000)
        for (int i = 0; i < 3_000; i++) {
            String suffix = "/" + pick(LARGE_PATH_KEYWORDS);
            if (random.nextBoolean()) {
                suffix += pick(LARGE_FILE_EXTENSIONS);
            }
            rules.add(rule("path-suf-" + id++, UrlPart.PATH, Operator.ENDS_WITH, suffix));
        }

        // === File-based rules (8,000) ===

        // File ends_with — extension-based asset classification (3,000)
        for (int i = 0; i < 3_000; i++) {
            rules.add(rule("file-ext-" + id++, UrlPart.FILE, Operator.ENDS_WITH,
                    pick(LARGE_FILE_EXTENSIONS)));
        }

        // File equals — specific filename targeting (2,000)
        for (int i = 0; i < 2_000; i++) {
            rules.add(rule("file-name-" + id++, UrlPart.FILE, Operator.EQUALS,
                    pick(LARGE_FILE_NAMES) + pick(LARGE_FILE_EXTENSIONS)));
        }

        // File starts_with — filename prefix patterns (1,500)
        for (int i = 0; i < 1_500; i++) {
            rules.add(rule("file-pre-" + id++, UrlPart.FILE, Operator.STARTS_WITH,
                    pick(LARGE_FILE_NAMES)));
        }

        // File contains — filename keyword detection (1,500)
        for (int i = 0; i < 1_500; i++) {
            rules.add(rule("file-kw-" + id++, UrlPart.FILE, Operator.CONTAINS,
                    pick(LARGE_FILE_NAMES)));
        }

        // === Query-based rules (7,000) ===

        // Query contains — parameter detection for analytics (2,000)
        for (int i = 0; i < 2_000; i++) {
            rules.add(rule("query-detect-" + id++, UrlPart.QUERY, Operator.CONTAINS,
                    pick(LARGE_QUERY_PARAMS)));
        }

        // Query starts_with — first-parameter targeting (2,000)
        for (int i = 0; i < 2_000; i++) {
            rules.add(rule("query-pre-" + id++, UrlPart.QUERY, Operator.STARTS_WITH,
                    pick(LARGE_QUERY_PARAMS)));
        }

        // Query ends_with — trailing parameter patterns (1,500)
        for (int i = 0; i < 1_500; i++) {
            rules.add(rule("query-suf-" + id++, UrlPart.QUERY, Operator.ENDS_WITH,
                    pick(LARGE_QUERY_PARAMS)));
        }

        // Query equals — exact parameter match (1,500)
        for (int i = 0; i < 1_500; i++) {
            rules.add(rule("query-exact-" + id++, UrlPart.QUERY, Operator.EQUALS,
                    pick(LARGE_QUERY_PARAMS)));
        }

        // === Compound rules (12,000) ===

        // 2-condition compound rules — cross-part targeting (8,000)
        for (int i = 0; i < 8_000; i++) {
            List<Condition> conditions = List.of(
                    largeRandomCondition(), largeRandomCondition());
            rules.add(new Rule("compound2-" + id++, randomPriority(),
                    conditions, "compound-match"));
        }

        // 3-condition compound rules — precise targeting (5,000)
        for (int i = 0; i < 5_000; i++) {
            List<Condition> conditions = List.of(
                    largeRandomCondition(), largeRandomCondition(), largeRandomCondition());
            rules.add(new Rule("compound3-" + id++, randomPriority(),
                    conditions, "compound-match"));
        }

        // 4-condition compound rules — highly specific business logic (2,000)
        for (int i = 0; i < 2_000; i++) {
            List<Condition> conditions = List.of(
                    largeRandomCondition(), largeRandomCondition(),
                    largeRandomCondition(), largeRandomCondition());
            rules.add(new Rule("compound4-" + id++, randomPriority(),
                    conditions, "compound-match"));
        }

        // === Negated rules (8,000) ===

        // Single negated condition — exclusion lists (3,000)
        for (int i = 0; i < 3_000; i++) {
            Condition cond = largeRandomCondition();
            Condition negated = new Condition(cond.part(), cond.operator(), cond.value(), true);
            rules.add(new Rule("negated-" + id++, randomPriority(),
                    List.of(negated), "negated-match"));
        }

        // Mixed positive + negated — "match X but not Y" patterns (2,000)
        for (int i = 0; i < 2_000; i++) {
            Condition positive = largeRandomCondition();
            Condition neg = largeRandomCondition();
            Condition negated = new Condition(neg.part(), neg.operator(), neg.value(), true);
            rules.add(new Rule("mixed-neg-" + id++, randomPriority(),
                    List.of(positive, negated), "mixed-neg-match"));
        }

        return Collections.unmodifiableList(rules);
    }

    /**
     * Generates approximately 200,000 URLs designed to exercise a large rule set.
     *
     * <p>Includes URLs with generated domains to ensure domain-heavy rules get exercised,
     * alongside the standard domain pool for path/file/query rule coverage.
     *
     * @return an unmodifiable list of generated URL strings
     */
    public List<String> generateLargeUrlSet() {
        List<String> urls = new ArrayList<>(200_000);

        // ~60K: known static domains — exercises path/file/query rules
        for (int i = 0; i < 60_000; i++) {
            urls.add("https://" + pick(DOMAINS)
                    + largeRandomPath() + largeRandomFile() + largeRandomQuery());
        }

        // ~30K: prefixed known domains — exercises subdomain rules
        for (int i = 0; i < 30_000; i++) {
            String host = pick(LARGE_SUBDOMAINS) + "." + pick(DOMAINS);
            urls.add("https://" + host
                    + largeRandomPath() + largeRandomFile() + largeRandomQuery());
        }

        // ~30K: generated domains — exercises domain exact-match rules
        for (int i = 0; i < 30_000; i++) {
            String domain = generateDomain(random.nextInt(20_000));
            urls.add("https://" + domain
                    + largeRandomPath() + largeRandomFile() + largeRandomQuery());
        }

        // ~20K: subdomained generated domains
        for (int i = 0; i < 20_000; i++) {
            String host = pick(LARGE_SUBDOMAINS) + "." + generateDomain(random.nextInt(20_000));
            urls.add("https://" + host
                    + largeRandomPath() + largeRandomFile() + largeRandomQuery());
        }

        // ~20K: random non-matching hosts
        for (int i = 0; i < 20_000; i++) {
            urls.add("https://random" + i + ".example.test"
                    + largeRandomPath() + largeRandomFile());
        }

        // ~20K: query-focused — exercises query parameter rules
        for (int i = 0; i < 20_000; i++) {
            String domain = pick(DOMAINS);
            String p1 = pick(LARGE_QUERY_PARAMS);
            String p2 = pick(LARGE_QUERY_PARAMS);
            urls.add("https://" + domain + largeRandomPath() + largeRandomFile()
                    + "?" + p1 + "&" + p2);
        }

        // ~20K: path-focused — exercises path pattern rules
        for (int i = 0; i < 20_000; i++) {
            String domain = pick(DOMAINS);
            String dir = pick(LARGE_PATH_SEGMENTS);
            String keyword = pick(LARGE_PATH_KEYWORDS);
            urls.add("https://" + domain + dir + "/" + keyword + largeRandomFile());
        }

        Collections.shuffle(urls, random);
        return Collections.unmodifiableList(urls);
    }

    /**
     * Generates a deterministic unique domain name from an index.
     *
     * <p>Combines words from two vocabularies with a TLD to produce up to
     * 60,000 unique domains (60 × 50 × 20).
     */
    private String generateDomain(int index) {
        int tldIdx = index % LARGE_TLDS.length;
        int remaining = index / LARGE_TLDS.length;
        int w2Idx = remaining % DOMAIN_WORDS_2.length;
        int w1Idx = (remaining / DOMAIN_WORDS_2.length) % DOMAIN_WORDS_1.length;
        return DOMAIN_WORDS_1[w1Idx] + DOMAIN_WORDS_2[w2Idx] + LARGE_TLDS[tldIdx];
    }

    private Condition largeRandomCondition() {
        UrlPart part = pickEnum(UrlPart.values());
        Operator operator = pickEnum(Operator.values());
        String value = switch (part) {
            case HOST -> largeRandomHostValue(operator);
            case PATH -> largeRandomPathValue(operator);
            case FILE -> largeRandomFileValue(operator);
            case QUERY -> pick(LARGE_QUERY_PARAMS);
        };
        return new Condition(part, operator, value, false);
    }

    private String largeRandomHostValue(Operator op) {
        return switch (op) {
            case EQUALS -> {
                String domain = generateDomain(random.nextInt(20_000));
                yield random.nextBoolean()
                        ? pick(LARGE_SUBDOMAINS) + "." + domain : domain;
            }
            case CONTAINS -> pick(BRAND_KEYWORDS);
            case STARTS_WITH -> pick(LARGE_SUBDOMAINS) + ".";
            case ENDS_WITH -> random.nextBoolean()
                    ? pick(LARGE_TLDS)
                    : "." + generateDomain(random.nextInt(20_000));
        };
    }

    private String largeRandomPathValue(Operator op) {
        return switch (op) {
            case EQUALS -> pick(LARGE_PATH_SEGMENTS) + "/" + pick(LARGE_PATH_KEYWORDS);
            case CONTAINS -> pick(LARGE_PATH_KEYWORDS);
            case STARTS_WITH -> pick(LARGE_PATH_SEGMENTS);
            case ENDS_WITH -> "/" + pick(LARGE_PATH_KEYWORDS);
        };
    }

    private String largeRandomFileValue(Operator op) {
        return switch (op) {
            case EQUALS -> pick(LARGE_FILE_NAMES) + pick(LARGE_FILE_EXTENSIONS);
            case CONTAINS, STARTS_WITH -> pick(LARGE_FILE_NAMES);
            case ENDS_WITH -> pick(LARGE_FILE_EXTENSIONS);
        };
    }

    private String largeRandomPath() {
        int depth = random.nextInt(4) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append(pick(LARGE_PATH_SEGMENTS));
        }
        return sb.toString();
    }

    private String largeRandomFile() {
        if (random.nextDouble() < 0.7) {
            return "/" + pick(LARGE_FILE_NAMES) + pick(LARGE_FILE_EXTENSIONS);
        }
        return "";
    }

    private String largeRandomQuery() {
        if (random.nextDouble() < 0.3) {
            int params = random.nextInt(3) + 1;
            StringBuilder sb = new StringBuilder("?");
            for (int i = 0; i < params; i++) {
                if (i > 0) {
                    sb.append("&");
                }
                sb.append(pick(LARGE_QUERY_PARAMS));
            }
            return sb.toString();
        }
        return "";
    }
}
