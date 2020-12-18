package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.filter.FilterConfig;

public interface FilterTransaction {

    void updateConfig(FilterConfig config);
}
