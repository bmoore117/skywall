package com.hyperion.jarvis.backend.model.config.job;

import com.hyperion.jarvis.backend.services.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;

public class DisableLockdownJob extends Job {

    private static final Logger log = LoggerFactory.getLogger(DisableLockdownJob.class);

    public DisableLockdownJob() {}

    public DisableLockdownJob(LocalDateTime jobLaunchTime, String jobDescription) {
        super(jobLaunchTime, jobDescription);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        configService.withFilterTransaction(filterConfig -> filterConfig.setLockdownActive(false));
        return true;
    }
}
