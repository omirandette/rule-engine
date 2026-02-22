package com.ruleengine.rule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.ruleengine.url.UrlPart;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RuleLoader {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    private static final Type RULE_LIST_TYPE = new TypeToken<List<Rule>>() {}.getType();

    private RuleLoader() {}

    public static List<Rule> loadFromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    public static List<Rule> load(Reader reader) {
        List<Rule> rules = GSON.fromJson(reader, RULE_LIST_TYPE);
        if (rules == null) {
            return List.of();
        }
        return List.copyOf(rules);
    }

    private static final class ConditionDeserializer implements JsonDeserializer<Condition> {
        @Override
        public Condition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            JsonObject obj = json.getAsJsonObject();
            UrlPart part = UrlPart.valueOf(obj.get("part").getAsString().toUpperCase());
            Operator operator = Operator.valueOf(obj.get("operator").getAsString().toUpperCase());
            String value = obj.get("value").getAsString();
            boolean negated = obj.has("negated") && obj.get("negated").getAsBoolean();
            return new Condition(part, operator, value, negated);
        }
    }
}
