package com.hyperion.jarvis.backend.model.config.job;

import com.hyperion.jarvis.backend.model.config.bedtime.Bedtimes;
import com.hyperion.jarvis.backend.services.ConfigService;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;

public class SaveBedtimesJob extends Job {

    private static final String BEDTIMES = "bedtimes";

    public SaveBedtimesJob() {}

    public SaveBedtimesJob(LocalDateTime jobLaunchTime, String description, Bedtimes bedtimes) {
        super(jobLaunchTime, description);
        data.put(BEDTIMES, bedtimes);
    }

    public Bedtimes getBedtimes() {
        return get(BEDTIMES, Bedtimes.class);
    }

    public void setBedtimes(Bedtimes bedtimes) {
        data.put(BEDTIMES, bedtimes);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        configService.withTransaction(config -> config.setBedtimes(getBedtimes()));
        return true;
    }
}
