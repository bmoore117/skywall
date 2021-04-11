package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.config.BlockedHost;
import com.hyperion.skywall.backend.model.config.Config;
import com.hyperion.skywall.backend.model.config.Phrase;
import com.hyperion.skywall.backend.model.config.Path;
import com.hyperion.skywall.backend.model.config.service.Service;

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

    public static Optional<Path> findWhitelistedPathById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getWhitelistedPaths().stream().filter(path -> uuid.equals(path.getId())).findFirst();
    }

    public static Optional<Path> findBlacklistedPathById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getBlacklistedPaths().stream().filter(path -> uuid.equals(path.getId())).findFirst();
    }

    public static Optional<BlockedHost> findHostById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getBlockedHosts().stream().filter(host -> uuid.equals(host.getId())).findFirst();
    }

    public static Optional<Path> findProcessById(Config config, UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return config.getWhitelistedPaths().stream().filter(process -> uuid.equals(process.getId())).findFirst();
    }
}
