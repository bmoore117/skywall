package com.hyperion.skywall.backend.model.config.job;

import java.time.LocalDateTime;

public abstract class NonCancellableJob extends Job {

    public NonCancellableJob() {}

    public NonCancellableJob(LocalDateTime jobLaunchTime, String jobDescription) {
        super(jobLaunchTime, jobDescription);
    }
}
