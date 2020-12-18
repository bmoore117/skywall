package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.config.Config;

public interface ConfigTransaction {

    void updateConfig(Config config);
}
