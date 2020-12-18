package com.hyperion.skywall.backend.model.config;

import com.hyperion.skywall.backend.model.Activatable;

public class BlockedHost extends Activatable {

    private String host;

    public BlockedHost() {}

    public BlockedHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
