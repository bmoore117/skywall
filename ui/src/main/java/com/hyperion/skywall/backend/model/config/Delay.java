package com.hyperion.skywall.backend.model.config;

import java.util.HashMap;
import java.util.Map;

public enum Delay {
    ZERO("0"),
    ONE_MINUTE("1 minute"),
    FIVE_MINUTES("5 minutes"),
    TEN_MINUTES("10 minutes"),
    THIRTY_MINUTES("30 minutes"),
    ONE_HOUR("1 hour"),
    TWO_HOURS("2 hours"),
    THREE_HOURS("3 hours"),
    FOUR_HOURS("4 hours"),
    FIVE_HOURS("5 hours"),
    TEN_HOURS("10 hours");

    public final String label;

    Delay(String label) {
        this.label = label;
    }

    private static final Map<String, Delay> BY_LABEL = new HashMap<>();

    static {
        for (Delay e: values()) {
            BY_LABEL.put(e.label, e);
        }
    }

    public static Delay valueOfLabel(String label) {
        return BY_LABEL.get(label);
    }

    public static int valueInSeconds(Delay delay) {
        String[] parts = delay.label.split(" ");
        if ("0".equals(parts[0])) {
            return 0;
        } else if (parts[1].contains("minute")) {
            return Integer.parseInt(parts[0])*60;
        } else {
            return Integer.parseInt(parts[0])*60*60;
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
