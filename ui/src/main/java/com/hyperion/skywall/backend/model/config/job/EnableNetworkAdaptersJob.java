package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;

public class EnableNetworkAdaptersJob extends NonCancellableJob {

    private static final Logger log = LoggerFactory.getLogger(SetDelayJob.class);

    private static final String DELAY = "delay";

    public EnableNetworkAdaptersJob() {}

    public EnableNetworkAdaptersJob(LocalDateTime jobLaunchTime, String jobDescription) {
        super(jobLaunchTime, jobDescription);
    }

    public void setDelay(Integer delay) {
        data.put(DELAY, delay);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        WinUtils winUtils = applicationContext.getBean(WinUtils.class);

        configService.withStartupLogTransaction(startupLog -> startupLog.getTimes().clear());
        try {
            winUtils.enableNetworkAdapters();
        } catch (IOException | InterruptedException e) {
            log.error("Error enabling network adapters", e);
            return false;
        }

        return true;
    }
}
