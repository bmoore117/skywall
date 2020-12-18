package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.ActivationStatus;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.service.Host;
import com.hyperion.skywall.backend.model.config.service.Service;
import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.ConfigUtils;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hyperion.skywall.backend.model.nlp.enumentity.FilterMode.PASSTHROUGH;
import static com.hyperion.skywall.backend.model.nlp.enumentity.FilterMode.STANDARD;
import static com.hyperion.skywall.backend.services.WinUtils.FILTER_SERVICE_NAME;

public class ActivateServiceJob extends Job implements ActivatableJob {
    private static final Logger log = LoggerFactory.getLogger(ActivateServiceJob.class);

    public ActivateServiceJob() {}

    public ActivateServiceJob(LocalDateTime jobLaunchTime, String jobDescription, Service service) {
        super(jobLaunchTime, jobDescription);
        data.put(JobConstants.ACTIVATABLE_ID, service.getId());
        data.put(JobConstants.ACTIVATABLE_OBJECT_VAL, service.getName());
    }

    @Override
    public UUID getActivatableId() {
        Object val = data.get(JobConstants.ACTIVATABLE_ID);
        if (val instanceof String) {
            return UUID.fromString((String) val);
        }
        return (UUID) data.get(JobConstants.ACTIVATABLE_ID);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        UUID activatableId = getActivatableId();
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        WinUtils winUtils = applicationContext.getBean(WinUtils.class);

        configService.withTransaction(config -> ConfigUtils.findServiceById(config, activatableId).ifPresent(service -> {
            List<Host> standardHosts = service.getHosts().stream().filter(host -> host.getFilterMode() == STANDARD).collect(Collectors.toList());
            List<Host> bypassHosts = service.getHosts().stream().filter(host -> host.getFilterMode() == PASSTHROUGH).collect(Collectors.toList());

            if (!bypassHosts.isEmpty()) {
                try {
                    winUtils.stopService(FILTER_SERVICE_NAME);
                } catch (IOException | InterruptedException e) {
                    log.error("Error stopping service", e);
                    return;
                }
            }

            configService.withFilterTransaction(filterConfig -> {
                for (Host host : standardHosts) {
                    filterConfig.getHosts().add(host.getHost());
                }

                for (Host host : bypassHosts) {
                    filterConfig.getIgnoredHosts().add(host.getHost());
                }
            });

            service.updateCurrentActivationStatus(ActivationStatus.ACTIVE);

            if (!bypassHosts.isEmpty()) {
                try {
                    winUtils.startService(FILTER_SERVICE_NAME);
                } catch (IOException | InterruptedException e) {
                    log.error("Error starting service", e);
                }
            }
        }));
        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return Service.class;
    }
}
