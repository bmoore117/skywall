package com.hyperion.jarvis.backend.model.config.job;

import java.util.UUID;

public interface ActivatableJob {

    Class<?> getActivatableClass();

    UUID getActivatableId();
}
