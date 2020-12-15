package com.hyperion.jarvis.backend.model.config.job;

import com.hyperion.jarvis.backend.model.config.ActivationStatus;
import com.hyperion.jarvis.backend.model.config.BlockedHost;
import com.hyperion.jarvis.backend.model.config.JobConstants;
import com.hyperion.jarvis.backend.services.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

public class DeactivateHostJob extends Job implements ActivatableJob {
    private static final Logger log = LoggerFactory.getLogger(DeactivateHostJob.class);

    public DeactivateHostJob() {}

    public DeactivateHostJob(LocalDateTime jobLaunchTime, String jobDescription, BlockedHost host) {
        super(jobLaunchTime, jobDescription);
        data.put(JobConstants.ACTIVATABLE_ID, host.getId());
        data.put(JobConstants.ACTIVATABLE_OBJECT_VAL, host.getHost());
    }

    @Override
    public UUID getActivatableId() {
        Object val = data.get(JobConstants.ACTIVATABLE_ID);
        if (val instanceof String) {
            return UUID.fromString((String) val);
        }
        return (UUID) data.get(JobConstants.ACTIVATABLE_ID);
    }

    public String getHostName() {
        return (String) data.get(JobConstants.ACTIVATABLE_OBJECT_VAL);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        UUID activatableId = getActivatableId();
        String hostName = getHostName();
        ConfigService configService = applicationContext.getBean(ConfigService.class);

        configService.withFilterTransaction(config -> config.getBlockedHosts().remove(hostName));

        configService.withTransaction(config -> config.getBlockedHosts().stream()
                .filter(host -> activatableId.equals(host.getId()))
                .findFirst()
                .ifPresent(host -> host.updateCurrentActivationStatus(ActivationStatus.DISABLED)));

        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return BlockedHost.class;
    }
}
