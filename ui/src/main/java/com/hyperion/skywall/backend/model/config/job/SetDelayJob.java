package com.hyperion.skywall.backend.model.config.job;

import com.hyperion.skywall.backend.model.config.Delay;
import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class SetDelayJob extends Job {

    private static final Logger log = LoggerFactory.getLogger(SetDelayJob.class);

    private static final String DELAY = "delay";

    public SetDelayJob() {
    }

    public SetDelayJob(LocalDateTime jobLaunchTime, String jobDescription, Delay delay) {
        super(jobLaunchTime, jobDescription);
        data.put("delay", delay);
    }

    public Delay getDelay() {
        Object o = data.get(DELAY);
        if (o instanceof String) {
            return Delay.valueOf((String) o);
        }
        return (Delay) o;
    }

    public void setDelay(Integer delay) {
        data.put(DELAY, delay);
    }

    @Override
    public Boolean call(ApplicationContext applicationContext) {
        ConfigService configService = applicationContext.getBean(ConfigService.class);
        WinUtils winUtils = applicationContext.getBean(WinUtils.class);
        Delay delay = getDelay();
        AtomicBoolean needsRestart = new AtomicBoolean(false);
        configService.withTransaction(config -> {
            if (delay == Delay.ZERO) {
                // case where we are setting delay to 0
                if (winUtils.changeLocalAdminPassword(ConfigService.STOCK_PASSWORD) == 0) {
                    config.setDelay(delay);
                } else {
                    log.error("Changing admin password unsuccessful");
                }
            } else if (configService.getCurrentDelay() == Delay.ZERO) {
                // case where we are increasing delay from 0
                String newPassword = winUtils.generatePassword();
                if (winUtils.changeLocalAdminPassword(newPassword) == 0) {
                    config.setDelay(delay);
                    configService.withFilterTransaction(filterConfig -> filterConfig.setFilterActive(true));
                    needsRestart.set(true);
                } else {
                    log.error("Changing admin password unsuccessful");
                }
            } else {
                // case where we have positive delay, and are increasing it
                config.setDelay(delay);
            }
        });

        if (needsRestart.get() && configService.getConfig().isStrictModeEnabled()) {
            // this is technically a race condition with job termination & queue removal, but all that
            // should finish before the restart happens given the baked-in delay before the actual restart
            CompletableFuture.runAsync(() -> {
                try {
                    winUtils.restartComputer();
                } catch (IOException | InterruptedException e) {
                    log.error("Unable to restart computer", e);
                }
            });
        }

        return true;
    }
}
