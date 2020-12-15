package com.hyperion.jarvis.backend.model.nlp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IntentAndEntities {

    private String originalQuery;
    private String intent;
    private Map<String, CollectedEntity<?>> collectedEntities;

    public IntentAndEntities() {
        collectedEntities = new HashMap<>();
    }

    public IntentAndEntities(String originalQuery, String intent) {
        this();
        this.originalQuery = originalQuery;
        this.intent = intent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntentAndEntities that = (IntentAndEntities) o;
        return Objects.equals(intent, that.intent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intent);
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Map<String, CollectedEntity<?>> getCollectedEntities() {
        return collectedEntities;
    }

    public void setCollectedEntities(Map<String, CollectedEntity<?>> collectedEntities) {
        this.collectedEntities = collectedEntities;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    @Override
    public String toString() {
        return "IntentAndEntities{" +
                "originalQuery='" + originalQuery + '\'' +
                ", intent='" + intent + '\'' +
                ", collectedEntities=" + collectedEntities +
                '}';
    }
}
