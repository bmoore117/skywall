package com.hyperion.jarvis.backend.model.config.service;

import com.hyperion.jarvis.backend.model.Activatable;

import java.util.ArrayList;
import java.util.List;

public class Service extends Activatable {

    private String name;
    private List<Host> hosts;

    public Service() {
        hosts = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Host> getHosts() {
        return hosts;
    }

    public void setHosts(List<Host> hosts) {
        this.hosts = hosts;
    }
}
