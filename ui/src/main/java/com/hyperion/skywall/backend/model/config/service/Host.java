package com.hyperion.skywall.backend.model.config.service;

import com.hyperion.skywall.backend.model.nlp.enumentity.FilterMode;

public class Host {

    private String host;
    private FilterMode filterMode;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }
}
