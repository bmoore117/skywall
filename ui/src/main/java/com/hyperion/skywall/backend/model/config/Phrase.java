package com.hyperion.skywall.backend.model.config;

import com.hyperion.skywall.backend.model.Activatable;

import java.util.UUID;

public class Phrase extends Activatable {

    private String phrase;

    public Phrase() {
        super();
    }

    public Phrase(String phrase, ActivationStatus current, ActivationStatus last, UUID uuid) {
        super(current, last, uuid);
        this.phrase = phrase;
    }

    public String getPhrase() {
        return phrase;
    }

    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }
}
