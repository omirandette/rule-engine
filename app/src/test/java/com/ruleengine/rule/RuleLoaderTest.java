package com.ruleengine.rule;

import com.ruleengine.url.UrlPart;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleLoaderTest {

    @Test
    void loadsRulesFromResource() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-rules.json");
        assertNotNull(is);
        List<Rule> rules = RuleLoader.load(new InputStreamReader(is));
        assertEquals(3, rules.size());
    }

    @Test
    void parsesCanadaSportRule() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-rules.json");
        List<Rule> rules = RuleLoader.load(new InputStreamReader(is));

        Rule canadaSport = rules.stream()
                .filter(r -> r.name().equals("Canada Sport"))
                .findFirst().orElseThrow();

        assertEquals(10, canadaSport.priority());
        assertEquals("Canada Sport", canadaSport.result());
        assertEquals(2, canadaSport.conditions().size());

        Condition hostCond = canadaSport.conditions().get(0);
        assertEquals(UrlPart.HOST, hostCond.part());
        assertEquals(Operator.ENDS_WITH, hostCond.operator());
        assertEquals(".ca", hostCond.value());
        assertFalse(hostCond.negated());
    }

    @Test
    void parsesNegatedCondition() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-rules.json");
        List<Rule> rules = RuleLoader.load(new InputStreamReader(is));

        Rule notAdmin = rules.stream()
                .filter(r -> r.name().equals("Not Admin"))
                .findFirst().orElseThrow();

        Condition cond = notAdmin.conditions().getFirst();
        assertTrue(cond.negated());
        assertEquals(Operator.STARTS_WITH, cond.operator());
    }

    @Test
    void caseInsensitiveEnums() {
        String json = """
                [{"name":"test","priority":1,"conditions":[
                  {"part":"HOST","operator":"EQUALS","value":"x"}
                ],"result":"ok"}]
                """;
        List<Rule> rules = RuleLoader.load(new StringReader(json));
        assertEquals(UrlPart.HOST, rules.getFirst().conditions().getFirst().part());
    }

    @Test
    void emptyJsonReturnsEmptyList() {
        List<Rule> rules = RuleLoader.load(new StringReader("[]"));
        assertTrue(rules.isEmpty());
    }

    @Test
    void rulesAreSortedByPriority() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-rules.json");
        List<Rule> rules = RuleLoader.load(new InputStreamReader(is));
        List<Rule> sorted = rules.stream().sorted().toList();
        assertEquals("Canada Sport", sorted.get(0).name());
        assertEquals("Example Home", sorted.get(1).name());
        assertEquals("Not Admin", sorted.get(2).name());
    }
}
