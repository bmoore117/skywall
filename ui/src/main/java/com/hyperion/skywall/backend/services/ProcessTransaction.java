package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.filter.ProcessConfig;

public interface ProcessTransaction {

    void updateConfig(ProcessConfig config);

}
