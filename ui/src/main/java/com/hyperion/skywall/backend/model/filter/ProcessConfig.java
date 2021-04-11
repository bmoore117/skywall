package com.hyperion.skywall.backend.model.filter;

import java.util.HashSet;
import java.util.Set;

public class ProcessConfig {

    private Set<String> whitelistedPaths;
    private Set<String> blacklistedPaths;

    public Set<String> getWhitelistedPaths() {
        if (whitelistedPaths == null) {
            whitelistedPaths = new HashSet<>();
        }
        return whitelistedPaths;
    }

    public void setWhitelistedPaths(Set<String> whitelistedPaths) {
        this.whitelistedPaths = whitelistedPaths;
    }

    public Set<String> getBlacklistedPaths() {
        if (blacklistedPaths == null) {
            blacklistedPaths = new HashSet<>();
        }
        return blacklistedPaths;
    }

    public void setBlacklistedPaths(Set<String> blacklistedPaths) {
        this.blacklistedPaths = blacklistedPaths;
    }
}
