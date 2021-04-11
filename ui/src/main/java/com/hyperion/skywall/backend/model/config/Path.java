package com.hyperion.skywall.backend.model.config;

import com.hyperion.skywall.backend.model.Activatable;

import java.util.UUID;

public class Path extends Activatable {

    private String path;

    public Path() {
        super();
    }

    public Path(String path, ActivationStatus current, ActivationStatus last, UUID uuid) {
        super(current, last, uuid);
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
