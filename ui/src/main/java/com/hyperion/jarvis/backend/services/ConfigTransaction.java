package com.hyperion.jarvis.backend.services;

import com.hyperion.jarvis.backend.model.config.Config;

public interface ConfigTransaction {

    void updateConfig(Config config);
}
