package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.ActivationStatus;
import com.hyperion.skywall.backend.model.config.JobConstants;
import com.hyperion.skywall.backend.model.config.service.Host;
import com.hyperion.skywall.backend.model.config.service.Service;
import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.hyperion.skywall.backend.model.nlp.enumentity.FilterMode.PASSTHROUGH;
import static com.hyperion.skywall.backend.model.nlp.enumentity.FilterMode.STANDARD;
import static com.hyperion.skywall.backend.services.WinUtils.FILTER_SERVICE_NAME;

public class DeleteServiceJob extends Job implements ActivatableJob {

    private static final Logger log = LoggerFactory.getLogger(DeleteServiceJob.class);

    public DeleteServiceJob() {}

    public DeleteServiceJob(LocalDateTime jobLaunchTime, String jobDescription, Service service) {
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
        UUID activatableId = getActivatableId();
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        WinUtils winUtils = applicationContext.getBean(WinUtils.class);

        AtomicBoolean needsRestart = new AtomicBoolean(false);
        configService.withTransaction(config -> configService.withFilterTransaction(filterConfig -> {

            // the general strategy here is zero out the filter config hosts of both types, and rebuild by what's active
            // we have to be careful not to accidentally add any pending items in as active, so we work out what those
            // are first.

            List<String> pendingIgnoredHosts = config.getDefinedServices().stream().map(service -> {
                HashSet<String> remainingHosts = service.getHosts().stream().filter(host -> PASSTHROUGH == host.getFilterMode())
                        .map(Host::getHost).collect(Collectors.toCollection(HashSet::new));
                remainingHosts.removeAll(filterConfig.getIgnoredHosts());
                return Arrays.asList(remainingHosts.toArray(new String[0]));
            }).filter(list -> !list.isEmpty()).flatMap(Collection::stream).collect(Collectors.toList());

            List<String> pendingHosts = config.getDefinedServices().stream().map(service -> {
                HashSet<String> remainingHosts = service.getHosts().stream().filter(host -> STANDARD == host.getFilterMode())
                        .map(Host::getHost).collect(Collectors.toCollection(HashSet::new));
                remainingHosts.removeAll(filterConfig.getHosts());
                return Arrays.asList(remainingHosts.toArray(new String[0]));
            }).filter(list -> !list.isEmpty()).flatMap(Collection::stream).collect(Collectors.toList());

            Set<String> oldIgnoredHosts = new HashSet<>(filterConfig.getIgnoredHosts());
            filterConfig.getIgnoredHosts().clear();
            filterConfig.getHosts().clear();
            for (Service service : configService.getConfig().getDefinedServices()) {
                if (!serviceName.equals(service.getName()) && service.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    for (Host host : service.getHosts()) {
                        if (host.getFilterMode() == PASSTHROUGH) {
                            filterConfig.getIgnoredHosts().add(host.getHost());
                        } else {
                            filterConfig.getHosts().add(host.getHost());
                        }
                    }
                }
            }

            filterConfig.getIgnoredHosts().removeAll(pendingIgnoredHosts);
            filterConfig.getHosts().removeAll(pendingHosts);

            if (!filterConfig.getIgnoredHosts().containsAll(oldIgnoredHosts) ||
                    filterConfig.getIgnoredHosts().size() != oldIgnoredHosts.size()) {
                needsRestart.set(true);
            }

            config.setDefinedServices(config.getDefinedServices().stream()
                    .filter(service -> !activatableId.equals(service.getId())).collect(Collectors.toList()));
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
