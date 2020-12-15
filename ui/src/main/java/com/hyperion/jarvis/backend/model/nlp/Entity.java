package com.hyperion.jarvis.backend.model.nlp;

import java.io.Serializable;

public class Entity implements Serializable {

    protected String taskId;
    protected String entityType;
    protected String entityTypeFriendly;
    protected String entityName;

    public Entity(String taskId, String entityType, String entityTypeFriendly, String entityName) {
        this.taskId = taskId;
        this.entityType = entityType;
        this.entityTypeFriendly = entityTypeFriendly;
        this.entityName = entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityTypeFriendly() {
        return entityTypeFriendly;
    }

    public void setEntityTypeFriendly(String entityTypeFriendly) {
        this.entityTypeFriendly = entityTypeFriendly;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
