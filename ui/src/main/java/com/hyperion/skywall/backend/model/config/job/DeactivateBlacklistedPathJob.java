package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.ActivationStatus;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.Path;
import com.hyperion.skywall.backend.services.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public class DeactivateBlacklistedPathJob extends Job implements ActivatableJob {
    private static final Logger log = LoggerFactory.getLogger(DeactivateBlacklistedPathJob.class);

    public DeactivateBlacklistedPathJob() {}

    public DeactivateBlacklistedPathJob(LocalDateTime jobLaunchTime, String jobDescription, Path blacklistedPath) {
        super(jobLaunchTime, jobDescription);
        data.put(JobConstants.ACTIVATABLE_ID, blacklistedPath.getId());
        data.put(JobConstants.ACTIVATABLE_OBJECT_VAL, blacklistedPath.getPath());
    }

    @Override
    public UUID getActivatableId() {
        Object val = data.get(JobConstants.ACTIVATABLE_ID);
        if (val instanceof String) {
            return UUID.fromString((String) val);
        }
        return (UUID) data.get(JobConstants.ACTIVATABLE_ID);
    }

    public String getPath() {
        return (String) data.get(JobConstants.ACTIVATABLE_OBJECT_VAL);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        UUID activatableId = getActivatableId();
        String pathName = getPath();
        ConfigService configService = applicationContext.getBean(ConfigService.class);

        configService.withProcessTransaction(config -> config.getBlacklistedPaths().remove(pathName));

        configService.withTransaction(config -> config.getBlacklistedPaths().stream()
                .filter(path -> activatableId.equals(path.getId()))
                .findFirst()
                .ifPresent(path -> path.updateCurrentActivationStatus(ActivationStatus.DISABLED)));

        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return Path.class;
    }
}
