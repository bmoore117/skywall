package com.hyperion.skywall.backend.controller;

import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.JobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest")
public class PingController {

    public static final Logger log = LoggerFactory.getLogger(PingController.class);

    private final JobRunner jobRunner;

    @Autowired
    public PingController(JobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @PostMapping(path = "/ping", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void checkStatus(@RequestBody Ping ping) {
        log.info("Received ping");
        jobRunner.onWake();
        jobRunner.resetHallPassForTheWeekIfEligible();
    }

    @PostMapping(path = "/scheduleUnlock")
    public void scheduleUnlock() {
        log.info("Scheduling internet unlock job");
        jobRunner.scheduleUnlock();
    }

    @GetMapping(path = "/getStockPassword")
    public String getStockPassword() {
        return ConfigService.STOCK_PASSWORD;
    }

    @GetMapping(path = "/startupCheck")
    public String startupCheck() {
        return "started";
    }
}
