package com.hyperion.jarvis.backend.model.config.job;

import com.hyperion.jarvis.backend.model.config.ActivationStatus;
import com.hyperion.jarvis.backend.model.config.JobConstants;
import com.hyperion.jarvis.backend.model.config.service.Host;
import com.hyperion.jarvis.backend.model.config.service.Service;
import com.hyperion.jarvis.backend.services.ConfigService;
import com.hyperion.jarvis.backend.services.ConfigUtils;
import com.hyperion.jarvis.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hyperion.jarvis.backend.model.nlp.enumentity.FilterMode.PASSTHROUGH;
import static com.hyperion.jarvis.backend.services.WinUtils.FILTER_SERVICE_NAME;

public class DeactivateServiceJob extends Job implements ActivatableJob {
    private static final Logger log = LoggerFactory.getLogger(DeactivateServiceJob.class);


    public DeactivateServiceJob() {}

    public DeactivateServiceJob(LocalDateTime jobLaunchTime, String jobDescription, Service service) {
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

    public String getServiceName() {
        return (String) data.get(JobConstants.ACTIVATABLE_OBJECT_VAL);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        String serviceName = getServiceName();
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        WinUtils winUtils = applicationContext.getBean(WinUtils.class);

        AtomicBoolean needsRestart = new AtomicBoolean(false);
        configService.withTransaction(config -> configService.withFilterTransaction(filterConfig -> {
            Set<String> oldIgnoredHosts = new HashSet<>(filterConfig.getIgnoredHosts());
            filterConfig.getIgnoredHosts().clear();
            filterConfig.getHosts().clear();
            for (Service service : configService.getConfig().getDefinedServices()) {
                if (!serviceName.equals(service.getName())) {
                    for (Host host : service.getHosts()) {
                        if (host.getFilterMode() == PASSTHROUGH) {
                            filterConfig.getIgnoredHosts().add(host.getHost());
                        } else {
                            filterConfig.getHosts().add(host.getHost());
                        }
                    }
                }
            }

            if (!filterConfig.getIgnoredHosts().containsAll(oldIgnoredHosts) ||
                    filterConfig.getIgnoredHosts().size() != oldIgnoredHosts.size()) {
                needsRestart.set(true);
            }

            ConfigUtils.findServiceById(config, getActivatableId()).ifPresent(service -> service.updateCurrentActivationStatus(ActivationStatus.DISABLED));
        }));

        if (needsRestart.get()) {
            try {
                winUtils.restartService(FILTER_SERVICE_NAME);
            } catch (IOException | InterruptedException e) {
                log.error("");
            }
        }

        return true;
    }

    @Override
    public Class<?> getActivatableClass() {
        return Service.class;
    }
}
