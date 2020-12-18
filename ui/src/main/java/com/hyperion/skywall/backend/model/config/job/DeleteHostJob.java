package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.BlockedHost;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.services.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeleteHostJob extends Job implements ActivatableJob {

    private static final Logger log = LoggerFactory.getLogger(DeleteHostJob.class);

    public DeleteHostJob() {}

    public DeleteHostJob(LocalDateTime jobLaunchTime, String jobDescription, BlockedHost host) {
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
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        String hostName = getHostName();
        UUID activatableId = getActivatableId();

        configService.withFilterTransaction(config -> config.getBlockedHosts().remove(hostName));

        configService.withTransaction(config -> config.setBlockedHosts(config.getBlockedHosts().stream()
                .filter(host -> !activatableId.equals(host.getId())).collect(Collectors.toList())));

        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return BlockedHost.class;
    }
}
