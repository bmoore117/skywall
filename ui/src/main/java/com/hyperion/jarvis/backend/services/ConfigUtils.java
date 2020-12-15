package com.hyperion.jarvis.backend.services;

import com.hyperion.jarvis.backend.model.config.BlockedHost;
import com.hyperion.jarvis.backend.model.config.Config;
import com.hyperion.jarvis.backend.model.config.Phrase;
import com.hyperion.jarvis.backend.model.config.service.Service;

import java.util.Optional;
import java.util.UUID;

public class ConfigUtils {

    public static Optional<Service> findServiceById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getDefinedServices().stream().filter(service -> uuid.equals(service.getId())).findFirst();
    }

    public static Optional<Phrase> findPhraseById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getDefinedPhrases().stream().filter(phrase -> uuid.equals(phrase.getId())).findFirst();
    }

    public static Optional<BlockedHost> findHostById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getBlockedHosts().stream().filter(host -> uuid.equals(host.getId())).findFirst();
    }

    public static void resetActivatableStatus(Config config, UUID uuid, Class<?> activatableClass) {
    }
}
