package com.hyperion.skywall.backend.model.config;

import com.hyperion.skywall.backend.model.config.bedtime.Bedtimes;
import com.hyperion.skywall.backend.model.config.job.Job;
import com.hyperion.skywall.backend.model.config.service.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Config {

    private Delay delay;
    private Set<Credentials> credentials;
    private Boolean hallPassUsed;
    private List<Job> pendingJobs;
    private List<Job> retryJobs;
    private Bedtimes bedtimes;
    private List<Location> knownLocations;
    private List<Service> definedServices;
    private List<Phrase> definedPhrases;
    private List<BlockedHost> blockedHosts;

    public Config() {
        delay = Delay.ZERO;
        hallPassUsed = false;
        credentials = new HashSet<>();
        pendingJobs = new ArrayList<>();
        retryJobs = new ArrayList<>();
        knownLocations = new ArrayList<>();
        definedServices = new ArrayList<>();
        definedPhrases = new ArrayList<>();
        blockedHosts = new ArrayList<>();
    }

    public Boolean isHallPassUsed() {
        if (hallPassUsed == null) {
            return false;
        }

        return hallPassUsed;
    }

    public void setHallPassUsed(Boolean hallPassUsed) {
        this.hallPassUsed = hallPassUsed;
    }

    public Delay getDelay() {
        if (delay == null) {
            delay = Delay.ZERO;
        }
        return delay;
    }

    public void setDelay(Delay delay) {
        this.delay = delay;
    }

    public Set<Credentials> getCredentials() {
        if (credentials == null) {
            credentials = new HashSet<>();
        }
        return credentials;
    }

    public void setCredentials(Set<Credentials> credentials) {
        this.credentials = credentials;
    }

    public List<Job> getPendingJobs() {
        if (pendingJobs == null) {
            pendingJobs = new ArrayList<>();
        }
        return pendingJobs;
    }

    public void setPendingJobs(List<Job> pendingJobs) {
        this.pendingJobs = pendingJobs;
    }

    public List<Job> getRetryJobs() {
        if (retryJobs == null) {
            retryJobs = new ArrayList<>();
        }
        return retryJobs;
    }

    public void setRetryJobs(List<Job> retryJobs) {
        this.retryJobs = retryJobs;
    }

    public Bedtimes getBedtimes() {
        return bedtimes;
    }

    public void setBedtimes(Bedtimes bedtimes) {
        this.bedtimes = bedtimes;
    }

    public List<Location> getKnownLocations() {
        if (this.knownLocations == null) {
            this.knownLocations = new ArrayList<>();
        }
        return knownLocations;
    }

    public void setKnownLocations(List<Location> knownLocations) {
        this.knownLocations = knownLocations;
    }

    public List<Service> getDefinedServices() {
        if (this.definedServices == null) {
            this.definedServices = new ArrayList<>();
        }
        return definedServices;
    }

    public void setDefinedServices(List<Service> definedServices) {
        this.definedServices = definedServices;
    }

    public List<Phrase> getDefinedPhrases() {
        if (this.definedPhrases == null) {
            this.definedPhrases = new ArrayList<>();
        }
        return definedPhrases;
    }

    public void setDefinedPhrases(List<Phrase> definedPhrases) {
        this.definedPhrases = definedPhrases;
    }

    public List<BlockedHost> getBlockedHosts() {
        if (this.blockedHosts == null) {
            this.blockedHosts = new ArrayList<>();
        }
        return blockedHosts;
    }

    public void setBlockedHosts(List<BlockedHost> blockedHosts) {
        this.blockedHosts = blockedHosts;
    }
}
