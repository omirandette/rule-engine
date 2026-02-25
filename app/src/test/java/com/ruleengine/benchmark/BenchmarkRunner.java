package com.ruleengine.benchmark;

import com.ruleengine.engine.RuleEngine;
import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlParser;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Macro-benchmark for measuring rule engine throughput with a realistic workload.
 *
 * <p>Generates rules and ~200K URLs, pre-parses URLs, then runs self-calibrating
 * timed iterations to measure evaluation throughput. Standard mode uses ~2000 rules;
 * pass "large" as the third argument for ~100K rules.
 */
public class BenchmarkRunner {

    private static final long TARGET_BENCHMARK_MS = 60_000;
    private static final NumberFormat NUM_FMT = NumberFormat.getIntegerInstance(Locale.US);

    /** Main entry point. Optional args: seed (default 42), threads (default 1), "large" for 100K rules. */
    public static void main(String[] args) throws IOException {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        boolean large = args.length > 2 && "large".equalsIgnoreCase(args[2]);
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1, got " + threads);
        }

        System.out.println("=== Rule Engine Benchmark ===");
        System.out.printf("Generating data (seed=%d, mode=%s)...%n",
                seed, large ? "large" : "standard");

        DataGenerator generator = new DataGenerator(seed);
        List<Rule> rules = large ? generator.generateLargeRuleSet() : generator.generateRules();
        List<String> urls = large ? generator.generateLargeUrlSet() : generator.generateUrls();
        System.out.printf("Generated %s rules, %s URLs%n",
                NUM_FMT.format(rules.size()), NUM_FMT.format(urls.size()));

        Path outputDir = Path.of("build/benchmark");
        Files.createDirectories(outputDir);
        writeRulesJson(rules, outputDir.resolve("rules.json"));
        writeUrlsTxt(urls, outputDir.resolve("urls.txt"));
        System.out.printf("Written to %s%n", outputDir.toAbsolutePath());

        System.out.println("\nPre-parsing URLs...");
        ParsedUrl[] parsedUrls = urls.stream()
                .map(UrlParser::parse)
                .toArray(ParsedUrl[]::new);

        System.out.println("Building engine...");
        long buildStart = System.nanoTime();
        RuleEngine engine = new RuleEngine(rules);
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
        System.out.printf("  Index build time: %s ms%n", NUM_FMT.format(buildMs));

        System.out.printf("Threads: %d%n", threads);

        System.out.println("\nWarmup pass...");
        runPass(engine, parsedUrls);

        System.out.println("Calibrating...");
        long calibrationStart = System.nanoTime();
        runPass(engine, parsedUrls);
        long singlePassMs = (System.nanoTime() - calibrationStart) / 1_000_000;
        int iterations = Math.max(1, (int) (TARGET_BENCHMARK_MS / Math.max(singlePassMs, 1)));
        System.out.printf("  Single pass: %s ms, running %d iterations (~%d s target)%n",
                NUM_FMT.format(singlePassMs), iterations, TARGET_BENCHMARK_MS / 1000);

        System.out.println("\nRunning benchmark...");
        long gcCountBefore = totalGcCount();
        long gcTimeBefore = totalGcTimeMs();
        long allocBefore = threadAllocatedBytes();

        int matchCount;
        long benchNanos;
        long parallelAlloc = 0;
        if (threads == 1) {
            matchCount = 0;
            long benchStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                matchCount = runPass(engine, parsedUrls);
            }
            benchNanos = System.nanoTime() - benchStart;
        } else {
            long benchStart = System.nanoTime();
            BenchmarkResult result = runParallel(engine, parsedUrls, iterations, threads);
            benchNanos = System.nanoTime() - benchStart;
            matchCount = result.matchCount;
            parallelAlloc = result.bytesAllocated;
        }

        long gcCountAfter = totalGcCount();
        long gcTimeAfter = totalGcTimeMs();
        long allocAfter = threadAllocatedBytes();

        double benchSeconds = benchNanos / 1_000_000_000.0;
        long totalEvaluated = (long) iterations * parsedUrls.length;
        long throughput = (long) (totalEvaluated / benchSeconds);
        double matchRate = 100.0 * matchCount / parsedUrls.length;

        System.out.printf("""
                  Iterations:           %s
                  Total URLs evaluated: %s
                  Wall time:            %.3f s
                  Throughput:           %s URLs/sec
                  Match rate:           %.1f%% (%s/%s)
                """,
                NUM_FMT.format(iterations),
                NUM_FMT.format(totalEvaluated),
                benchSeconds,
                NUM_FMT.format(throughput),
                matchRate,
                NUM_FMT.format(matchCount),
                NUM_FMT.format(parsedUrls.length));

        long gcCollections = gcCountAfter - gcCountBefore;
        long gcPauseMs = gcTimeAfter - gcTimeBefore;
        double gcPausePercent = 100.0 * gcPauseMs / (benchSeconds * 1000);
        long bytesAllocated = threads == 1 ? allocAfter - allocBefore : parallelAlloc;
        double allocRateMBs = bytesAllocated / (benchSeconds * 1024 * 1024);
        long bytesPerUrl = totalEvaluated > 0 ? bytesAllocated / totalEvaluated : 0;

        System.out.printf("""

                === GC & Memory ===
                  GC collections:      %s
                  GC pause time:       %.2f s (%.1f%% of wall time)
                  Bytes allocated:     %s
                  Allocation rate:     %.0f MB/s
                  Bytes per URL:       %d B
                """,
                NUM_FMT.format(gcCollections),
                gcPauseMs / 1000.0, gcPausePercent,
                formatBytes(bytesAllocated),
                allocRateMBs,
                bytesPerUrl);
    }

    private static int runPass(RuleEngine engine, ParsedUrl[] urls) {
        int matches = 0;
        for (ParsedUrl url : urls) {
            if (engine.evaluate(url) != null) {
                matches++;
            }
        }
        return matches;
    }

    private record BenchmarkResult(int matchCount, long bytesAllocated) {}

    /**
     * Runs the benchmark with multiple threads, each performing its share of iterations.
     * All threads share the same {@code RuleEngine} instance to exercise thread-safety.
     * Each thread tracks its own allocation via {@code ThreadMXBean} and the totals are summed.
     */
    private static BenchmarkResult runParallel(RuleEngine engine, ParsedUrl[] urls,
            int iterations, int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<long[]>> futures = new ArrayList<>(threads);

        int baseIters = iterations / threads;
        int remainder = iterations % threads;

        for (int t = 0; t < threads; t++) {
            int iters = baseIters + (t < remainder ? 1 : 0);
            futures.add(executor.submit(() -> {
                long allocBefore = threadAllocatedBytes();
                int lastMatches = 0;
                for (int i = 0; i < iters; i++) {
                    lastMatches = runPass(engine, urls);
                }
                long allocAfter = threadAllocatedBytes();
                long alloc = (allocBefore >= 0 && allocAfter >= 0)
                        ? allocAfter - allocBefore : -1;
                return new long[]{lastMatches, alloc};
            }));
        }

        int matchCount = 0;
        long totalAlloc = 0;
        boolean allocSupported = true;
        for (Future<long[]> f : futures) {
            try {
                long[] result = f.get();
                matchCount = (int) result[0];
                if (result[1] >= 0) {
                    totalAlloc += result[1];
                } else {
                    allocSupported = false;
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Benchmark thread failed", e);
            }
        }
        executor.shutdown();
        return new BenchmarkResult(matchCount, allocSupported ? totalAlloc : -1);
    }

    private static void writeRulesJson(List<Rule> rules, Path path) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
            writer.println("[");
            for (int i = 0; i < rules.size(); i++) {
                Rule rule = rules.get(i);
                writer.println("  {");
                writer.printf("    \"name\": %s,%n", jsonString(rule.name()));
                writer.printf("    \"priority\": %d,%n", rule.priority());
                writer.println("    \"conditions\": [");
                List<Condition> conditions = rule.conditions();
                for (int c = 0; c < conditions.size(); c++) {
                    Condition cond = conditions.get(c);
                    writer.println("      {");
                    writer.printf("        \"part\": \"%s\",%n", cond.part().name().toLowerCase(Locale.ROOT));
                    writer.printf("        \"operator\": \"%s\",%n", cond.operator().name().toLowerCase(Locale.ROOT));
                    writer.printf("        \"value\": %s", jsonString(cond.value()));
                    if (cond.negated()) {
                        writer.printf(",%n        \"negated\": true%n");
                    } else {
                        writer.println();
                    }
                    writer.print("      }");
                    if (c < conditions.size() - 1) {
                        writer.println(",");
                    } else {
                        writer.println();
                    }
                }
                writer.println("    ],");
                writer.printf("    \"result\": %s%n", jsonString(rule.result()));
                writer.print("  }");
                if (i < rules.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            writer.println("]");
        }
    }

    private static void writeUrlsTxt(List<String> urls, Path path) throws IOException {
        Files.write(path, urls);
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static long totalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0) {
                total += count;
            }
        }
        return total;
    }

    private static long totalGcTimeMs() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = gc.getCollectionTime();
            if (time >= 0) {
                total += time;
            }
        }
        return total;
    }

    /**
     * Returns the number of bytes allocated by the current thread, or -1 if unsupported.
     *
     * <p>Uses the {@code com.sun.management.ThreadMXBean} extension available on HotSpot JVMs.
     */
    private static long threadAllocatedBytes() {
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean threadBean) {
            return threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        return -1;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "N/A (unsupported)";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
