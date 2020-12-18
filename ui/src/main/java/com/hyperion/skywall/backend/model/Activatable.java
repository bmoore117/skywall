package com.hyperion.skywall.backend.model;

import com.hyperion.skywall.backend.model.config.ActivationStatus;

import java.util.Objects;
import java.util.UUID;

public abstract class Activatable {

    private ActivationStatus currentStatus;
    private ActivationStatus lastActivationStatus;
    private UUID id;

    public Activatable() {
        id = UUID.randomUUID();
    }

    public Activatable(ActivationStatus currentStatus, ActivationStatus lastActivationStatus, UUID id) {
        this.currentStatus = currentStatus;
        this.lastActivationStatus = lastActivationStatus;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Activatable that = (Activatable) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public ActivationStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(ActivationStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public ActivationStatus getLastActivationStatus() {
        return lastActivationStatus;
    }

    public void setLastActivationStatus(ActivationStatus lastActivationStatus) {
        this.lastActivationStatus = lastActivationStatus;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void updateCurrentActivationStatus(ActivationStatus status) {
        if (this.currentStatus != ActivationStatus.PENDING_DEACTIVATION
                && this.currentStatus != ActivationStatus.PENDING_DELETE
                && this.currentStatus != ActivationStatus.PENDING_ACTIVATION) {
            this.lastActivationStatus = this.currentStatus;
        }
        this.currentStatus = status;
    }
}
