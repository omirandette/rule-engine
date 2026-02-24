package com.ruleengine.index;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class RuleIndexTest {

    private Rule rule(String name, Condition... conditions) {
        return new Rule(name, 1, List.of(conditions), name);
    }

    private Condition cond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, false);
    }

    private Condition negCond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, true);
    }

    @Test
    void equalsMatch() {
        Rule r = rule("eq", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(new ParsedUrl("example.com", "/", "", ""));
        assertTrue(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void equalsNoMatch() {
        Rule r = rule("eq", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(new ParsedUrl("other.com", "/", "", ""));
        assertFalse(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void startsWithMatch() {
        Rule r = rule("sw", cond(UrlPart.PATH, Operator.STARTS_WITH, "/api"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(new ParsedUrl("x.com", "/api/users", "users", ""));
        assertTrue(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void endsWithMatch() {
        Rule r = rule("ew", cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(new ParsedUrl("shop.example.ca", "/", "", ""));
        assertTrue(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void containsMatch() {
        Rule r = rule("ct", cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(new ParsedUrl("x.com", "/category/sport/items", "items", ""));
        assertTrue(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void negatedConditionsNotIndexed() {
        Rule r = rule("neg", negCond(UrlPart.PATH, Operator.STARTS_WITH, "/admin"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(new ParsedUrl("x.com", "/admin/panel", "panel", ""));
        assertFalse(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void multipleRulesMultipleOperators() {
        Rule r1 = rule("r1", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        Rule r2 = rule("r2", cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        Rule r3 = rule("r3", cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        RuleIndex index = new RuleIndex(List.of(r1, r2, r3));

        CandidateResult candidates = index.queryCandidates(
                new ParsedUrl("example.com", "/sport", "sport", ""));

        assertTrue(candidates.isCandidate(index.ruleId(r1)));
        assertTrue(candidates.isCandidate(index.ruleId(r2)));
        assertTrue(candidates.isCandidate(index.ruleId(r3)));
    }

    @Test
    void queryOnQueryParam() {
        Rule r = rule("qp", cond(UrlPart.QUERY, Operator.CONTAINS, "lang=en"));
        RuleIndex index = new RuleIndex(List.of(r));

        CandidateResult candidates = index.queryCandidates(
                new ParsedUrl("x.com", "/", "", "q=hello&lang=en"));
        assertTrue(candidates.isCandidate(index.ruleId(r)));
    }

    @Test
    void concurrentQueriesReturnCorrectResults() throws Exception {
        Rule r1 = rule("host-eq", cond(UrlPart.HOST, Operator.EQUALS, "match.com"));
        Rule r2 = rule("path-sw", cond(UrlPart.PATH, Operator.STARTS_WITH, "/api"));
        Rule r3 = rule("host-ew", cond(UrlPart.HOST, Operator.ENDS_WITH, ".org"));
        RuleIndex index = new RuleIndex(List.of(r1, r2, r3));

        int threadCount = 8;
        int iterationsPerThread = 10_000;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        ParsedUrl matchR1 = new ParsedUrl("match.com", "/home", "home", "");
        ParsedUrl matchR2 = new ParsedUrl("other.com", "/api/users", "users", "");
        ParsedUrl matchR3 = new ParsedUrl("example.org", "/page", "page", "");
        ParsedUrl noMatch = new ParsedUrl("none.net", "/nothing", "nothing", "");

        ParsedUrl[] urls = {matchR1, matchR2, matchR3, noMatch};

        List<Future<String>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                    ParsedUrl url = urls[(threadId + i) % urls.length];
                    CandidateResult result = index.queryCandidates(url);

                    if (url == matchR1) {
                        assertTrue(result.isCandidate(index.ruleId(r1)));
                        assertFalse(result.isCandidate(index.ruleId(r2)));
                        assertFalse(result.isCandidate(index.ruleId(r3)));
                    } else if (url == matchR2) {
                        assertFalse(result.isCandidate(index.ruleId(r1)));
                        assertTrue(result.isCandidate(index.ruleId(r2)));
                        assertFalse(result.isCandidate(index.ruleId(r3)));
                    } else if (url == matchR3) {
                        assertFalse(result.isCandidate(index.ruleId(r1)));
                        assertFalse(result.isCandidate(index.ruleId(r2)));
                        assertTrue(result.isCandidate(index.ruleId(r3)));
                    } else {
                        assertFalse(result.isCandidate(index.ruleId(r1)));
                        assertFalse(result.isCandidate(index.ruleId(r2)));
                        assertFalse(result.isCandidate(index.ruleId(r3)));
                    }
                }
                return "ok";
            }));
        }

        startLatch.countDown();
        for (Future<String> f : futures) {
            assertEquals("ok", f.get());
        }
        executor.shutdown();
    }
}
