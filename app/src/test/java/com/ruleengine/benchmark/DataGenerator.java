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
}
