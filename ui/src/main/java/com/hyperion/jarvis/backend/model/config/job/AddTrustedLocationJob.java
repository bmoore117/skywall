package com.hyperion.jarvis.backend.model.config.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperion.jarvis.backend.model.config.Location;
import com.hyperion.jarvis.backend.services.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

public class AddTrustedLocationJob extends Job {
    private static final Logger log = LoggerFactory.getLogger(AddTrustedLocationJob.class);

    private static final String LOCATION = "location";
    private static final ObjectMapper mapper = new ObjectMapper();

    public AddTrustedLocationJob() {}

    public AddTrustedLocationJob(LocalDateTime jobLaunchTime, String jobDescription, Location location) {
        super(jobLaunchTime, jobDescription);
        data.put(LOCATION, location);
    }

    public Location getLocation() {
        Object o = data.get(LOCATION);
        if (o instanceof Map) {
            return mapper.convertValue(o, Location.class);
        }
        return (Location) o;
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        configService.withTransaction(config -> {
            config.getKnownLocations().add(getLocation());
        });
        return true;
    }
}
