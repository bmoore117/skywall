package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.config.StartupLog;

public interface StartupLogTransaction {

    void update(StartupLog log);

}
