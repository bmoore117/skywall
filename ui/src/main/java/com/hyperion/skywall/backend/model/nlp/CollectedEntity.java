package com.hyperion.skywall.backend.model.nlp;

import java.io.Serializable;

public class CollectedEntity<T> extends Entity implements Serializable {

    private T entityValue;

    public CollectedEntity(String taskId, String entityType, String entityTypeFriendly, String entityName, T entityValue) {
        super(taskId, entityType, entityTypeFriendly, entityName);
        this.entityValue = entityValue;
    }

    public T getEntityValue() {
        return entityValue;
    }

    public void setEntityValue(T entityValue) {
        this.entityValue = entityValue;
    }

    @Override
    public String toString() {
        return "CollectedEntity{" +
                "entityValue=" + entityValue +
                ", taskId='" + taskId + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityTypeFriendly='" + entityTypeFriendly + '\'' +
                ", entityName='" + entityName + '\'' +
                '}';
    }
}
