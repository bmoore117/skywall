package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.ActivationStatus;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.Phrase;
import com.hyperion.skywall.backend.services.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public class DeactivatePhraseJob extends Job implements ActivatableJob {
    private static final Logger log = LoggerFactory.getLogger(DeactivateServiceJob.class);

    public DeactivatePhraseJob() {}

    public DeactivatePhraseJob(LocalDateTime jobLaunchTime, String jobDescription, Phrase phrase) {
        super(jobLaunchTime, jobDescription);
        data.put(JobConstants.ACTIVATABLE_ID, phrase.getId());
        data.put(JobConstants.ACTIVATABLE_OBJECT_VAL, phrase.getPhrase());
    }

    @Override
    public UUID getActivatableId() {
        Object val = data.get(JobConstants.ACTIVATABLE_ID);
        if (val instanceof String) {
            return UUID.fromString((String) val);
        }
        return (UUID) data.get(JobConstants.ACTIVATABLE_ID);
    }

    public String getPhrase() {
        return (String) data.get(JobConstants.ACTIVATABLE_OBJECT_VAL);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        UUID activatableId = getActivatableId();
        String phraseName = getPhrase();
        ConfigService configService = applicationContext.getBean(ConfigService.class);

        configService.withFilterTransaction(config -> config.getBlockedPhrases().remove(phraseName));

        configService.withTransaction(config -> config.getDefinedPhrases().stream()
                .filter(phrase -> activatableId.equals(phrase.getId()))
                .findFirst()
                .ifPresent(phrase -> phrase.updateCurrentActivationStatus(ActivationStatus.DISABLED)));

        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return Phrase.class;
    }
}
