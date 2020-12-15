package com.hyperion.jarvis.backend.model.config.job;

import com.hyperion.jarvis.backend.model.config.JobConstants;
import com.hyperion.jarvis.backend.model.config.Phrase;
import com.hyperion.jarvis.backend.services.ConfigService;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeletePhraseJob extends Job implements ActivatableJob {

    public DeletePhraseJob() {}

    public DeletePhraseJob(LocalDateTime jobLaunchTime, String jobDescription, Phrase phrase) {
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

        configService.withFilterTransaction(config -> config.getBlockedHosts().remove(phraseName));

        configService.withTransaction(config -> config.setDefinedPhrases(config.getDefinedPhrases().stream()
                .filter(phrase -> !activatableId.equals(phrase.getId())).collect(Collectors.toList())));

        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return Phrase.class;
    }
}
