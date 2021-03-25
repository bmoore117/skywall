package com.hyperion.skywall.backend.model.config;

import com.hyperion.skywall.backend.model.Activatable;

import java.util.UUID;

public class Process extends Activatable {

    private String process;

    public Process() {
        super();
    }

    public Process(String process, ActivationStatus current, ActivationStatus last, UUID uuid) {
        super(current, last, uuid);
        this.process = process;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }
}
