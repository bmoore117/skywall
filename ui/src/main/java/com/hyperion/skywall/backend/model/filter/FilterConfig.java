package com.hyperion.skywall.backend.model.filter;

import java.util.HashSet;
import java.util.Set;

public class FilterConfig {

    private Set<String> hosts;
    private Set<String> blockedHosts;
    private Set<String> ignoredHosts;
    private Set<String> blockedPhrases;
    private boolean lockdownActive;
    private boolean filterActive;

    public FilterConfig() {
        this.hosts = new HashSet<>();
        this.blockedHosts = new HashSet<>();
        filterActive = true;
    }

    public boolean isLockdownActive() {
        return lockdownActive;
    }

    public void setLockdownActive(boolean lockdownActive) {
        this.lockdownActive = lockdownActive;
    }

    public Set<String> getHosts() {
        if (hosts == null) {
            hosts = new HashSet<>();
        }
        return hosts;
    }

    public void setHosts(Set<String> hosts) {
        this.hosts = hosts;
    }

    public Set<String> getBlockedHosts() {
        if (blockedHosts == null) {
            blockedHosts = new HashSet<>();
        }
        return blockedHosts;
    }

    public void setBlockedHosts(Set<String> blockedHosts) {
        this.blockedHosts = blockedHosts;
    }

    public Set<String> getIgnoredHosts() {
        if (ignoredHosts == null) {
            ignoredHosts = new HashSet<>();
        }
        return ignoredHosts;
    }

    public void setIgnoredHosts(Set<String> ignoredHosts) {
        this.ignoredHosts = ignoredHosts;
    }

    public boolean isFilterActive() {
        return filterActive;
    }

    public void setFilterActive(boolean filterActive) {
        this.filterActive = filterActive;
    }

    public Set<String> getBlockedPhrases() {
        if (blockedPhrases == null) {
            blockedPhrases = new HashSet<>();
        }
        return blockedPhrases;
    }

    public void setBlockedPhrases(Set<String> blockedPhrases) {
        this.blockedPhrases = blockedPhrases;
    }
}
