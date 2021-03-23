package com.hyperion.skywall.backend.model.filter;

import java.util.HashSet;
import java.util.Set;

public class ProcessConfig {

    private Set<String> processes;

    public Set<String> getProcesses() {
        if (processes == null) {
            processes = new HashSet<>();
        }
        return processes;
    }

    public void setProcesses(Set<String> processes) {
        this.processes = processes;
    }
}
