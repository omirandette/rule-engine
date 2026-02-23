package com.ruleengine.benchmark;

import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlParser;

import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A/B benchmark comparing the old {@link URI}-based URL parser against the new
 * {@code indexOf}-based {@link UrlParser}.
 *
 * <p>Generates ~100K realistic URLs, warms up both implementations, then measures
 * average ns/URL and URLs/sec for each, printing a side-by-side comparison.
 */
public class UrlParserBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURED_ITERATIONS = 10;
    private static final NumberFormat NUM_FMT = NumberFormat.getIntegerInstance(Locale.US);

    /** Main entry point. Optional arg: random seed (default 42). */
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;

        System.out.println("=== UrlParser A/B Benchmark ===");
        System.out.printf("Generating URLs (seed=%d)...%n", seed);

        List<String> urls = generateUrls(seed);
        System.out.printf("Generated %s URLs%n%n", NUM_FMT.format(urls.size()));

        String[] urlArray = urls.toArray(String[]::new);

        // Warmup
        System.out.printf("Warming up (%d iterations each)...%n", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runOldParser(urlArray);
            runNewParser(urlArray);
        }

        // Measure old (URI-based) parser
        System.out.printf("%nMeasuring OLD (URI-based) parser (%d iterations)...%n", MEASURED_ITERATIONS);
        long[] oldTimes = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            runOldParser(urlArray);
            oldTimes[i] = System.nanoTime() - start;
        }

        // Measure new (indexOf-based) parser
        System.out.printf("Measuring NEW (indexOf-based) parser (%d iterations)...%n", MEASURED_ITERATIONS);
        long[] newTimes = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            runNewParser(urlArray);
            newTimes[i] = System.nanoTime() - start;
        }

        // Report
        double oldAvgNs = average(oldTimes) / urlArray.length;
        double newAvgNs = average(newTimes) / urlArray.length;
        double oldUrlsPerSec = 1_000_000_000.0 / oldAvgNs;
        double newUrlsPerSec = 1_000_000_000.0 / newAvgNs;
        double speedup = oldAvgNs / newAvgNs;

        System.out.printf("""

                === Results ===
                                        OLD (URI)       NEW (indexOf)
                Avg ns/URL:             %,.1f           %,.1f
                URLs/sec:               %s              %s
                Speedup:                %.2fx
                """,
                oldAvgNs, newAvgNs,
                NUM_FMT.format((long) oldUrlsPerSec),
                NUM_FMT.format((long) newUrlsPerSec),
                speedup);
    }

    /**
     * Parses all URLs using the old URI-based implementation.
     * This is a copy of the original logic before the indexOf rewrite.
     */
    private static int runOldParser(String[] urls) {
        int sink = 0;
        for (String raw : urls) {
            sink += parseWithUri(raw).host().length();
        }
        return sink;
    }

    /** Parses all URLs using the current (new) UrlParser. */
    private static int runNewParser(String[] urls) {
        int sink = 0;
        for (String raw : urls) {
            sink += UrlParser.parse(raw).host().length();
        }
        return sink;
    }

    /**
     * Original URI-based parsing logic, preserved here for benchmark comparison.
     */
    private static ParsedUrl parseWithUri(String raw) {
        String toParse = raw.strip();
        if (!toParse.contains("://")) {
            toParse = "http://" + toParse;
        }

        URI uri = URI.create(toParse);

        String host = uri.getHost();
        if (host == null) {
            host = "";
        }
        host = host.toLowerCase();

        String path = uri.getPath();
        if (path == null) {
            path = "";
        }

        String file;
        if (path.isEmpty()) {
            file = "";
        } else {
            int lastSlash = path.lastIndexOf('/');
            file = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        }

        String query = uri.getQuery();
        if (query == null) {
            query = "";
        }

        return new ParsedUrl(host, path, file, query);
    }

    private static double average(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return (double) sum / values.length;
    }

    /** Generates ~100K realistic URLs with a mix of patterns. */
    private static List<String> generateUrls(long seed) {
        Random rng = new Random(seed);
        List<String> urls = new ArrayList<>(100_000);

        String[] domains = {
            "google.com", "facebook.com", "youtube.com", "amazon.com", "github.com",
            "netflix.com", "twitter.com", "linkedin.com", "reddit.com", "instagram.com",
            "microsoft.com", "apple.com", "stackoverflow.com", "wikipedia.org", "medium.com",
            "twitch.tv", "spotify.com", "dropbox.com", "slack.com", "zoom.us"
        };
        String[] prefixes = {"www.", "api.", "shop.", "blog.", "m.", "cdn.", "app."};
        String[] paths = {
            "/api/v1/users", "/products/electronics", "/blog/2025/post",
            "/search", "/category/sport/items", "/docs/getting-started",
            "/admin/dashboard", "/login", "/settings/profile", "/images/logo.png"
        };
        String[] queries = {
            "q=hello&lang=en", "page=1&sort=date", "utm_source=google",
            "id=12345&format=json", "category=electronics&brand=nike"
        };
        String[] files = {
            "/index.html", "/main.js", "/style.css", "/data.json",
            "/logo.png", "/favicon.ico", "/robots.txt", "/sitemap.xml"
        };

        // Full URLs with scheme (30K)
        for (int i = 0; i < 30_000; i++) {
            String scheme = rng.nextBoolean() ? "https://" : "http://";
            String domain = domains[rng.nextInt(domains.length)];
            String path = paths[rng.nextInt(paths.length)];
            String url = scheme + domain + path;
            if (rng.nextDouble() < 0.4) {
                url += "?" + queries[rng.nextInt(queries.length)];
            }
            urls.add(url);
        }

        // Scheme-less URLs (20K)
        for (int i = 0; i < 20_000; i++) {
            String domain = domains[rng.nextInt(domains.length)];
            String path = paths[rng.nextInt(paths.length)];
            urls.add(domain + path);
        }

        // With subdomains (20K)
        for (int i = 0; i < 20_000; i++) {
            String prefix = prefixes[rng.nextInt(prefixes.length)];
            String domain = domains[rng.nextInt(domains.length)];
            String path = paths[rng.nextInt(paths.length)];
            urls.add("https://" + prefix + domain + path);
        }

        // Deep paths with files (15K)
        for (int i = 0; i < 15_000; i++) {
            String domain = domains[rng.nextInt(domains.length)];
            String path = paths[rng.nextInt(paths.length)];
            String file = files[rng.nextInt(files.length)];
            urls.add("https://" + domain + path + file);
        }

        // Query-heavy URLs (10K)
        for (int i = 0; i < 10_000; i++) {
            String domain = domains[rng.nextInt(domains.length)];
            String query = queries[rng.nextInt(queries.length)]
                    + "&" + queries[rng.nextInt(queries.length)];
            urls.add("https://" + domain + "/search?" + query);
        }

        // Host-only URLs (5K)
        for (int i = 0; i < 5_000; i++) {
            String domain = domains[rng.nextInt(domains.length)];
            urls.add("https://" + domain);
        }

        Collections.shuffle(urls, rng);
        return urls;
    }
}
