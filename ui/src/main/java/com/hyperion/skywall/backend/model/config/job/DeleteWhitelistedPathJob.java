package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.Path;
import com.hyperion.skywall.backend.services.ConfigService;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeleteWhitelistedPathJob extends Job implements ActivatableJob {

    public DeleteWhitelistedPathJob() {}

    public DeleteWhitelistedPathJob(LocalDateTime jobLaunchTime, String jobDescription, Path path) {
        super(jobLaunchTime, jobDescription);
        data.put(JobConstants.ACTIVATABLE_ID, path.getId());
        data.put(JobConstants.ACTIVATABLE_OBJECT_VAL, path.getPath());
    }

    @Override
    public Class<?> getActivatableClass() {
        return Path.class;
    }

    @Override
    public UUID getActivatableId() {
        Object val = data.get(JobConstants.ACTIVATABLE_ID);
        if (val instanceof String) {
            return UUID.fromString((String) val);
        }
        return (UUID) val;
    }

    public String getProcessName() {
        return (String) data.get(JobConstants.ACTIVATABLE_OBJECT_VAL);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);

        configService.withTransaction(config -> {
            config.setWhitelistedPaths(config.getWhitelistedPaths().stream().filter(path -> !path.getPath().equals(getProcessName())).collect(Collectors.toList()));
            configService.withProcessTransaction(processes -> processes.getWhitelistedPaths().remove(getProcessName()));
        });

        return true;
    }
}
