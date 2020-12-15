package com.hyperion.jarvis;

import com.hyperion.jarvis.backend.services.JobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

@Component
public class StartupTaskRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupTaskRunner.class);

    private final ResourcePatternResolver resourcePatternResolver;
    private final JobRunner jobRunner;

    @Autowired
    public StartupTaskRunner(JobRunner jobRunner, ResourcePatternResolver resourcePatternResolver) {
        this.jobRunner = jobRunner;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            File f = new File("scripts");
            if (!f.exists()) {
                if (f.mkdirs()) {
                    unpackFiles(f);
                } else {
                    log.error("For some reason the scripts directory could not be created");
                }
            } else {
                unpackFiles(f);
            }
        } catch (IOException e) {
            log.error("Error unpacking scripts", e);
        }

        // todo is this needed if we have ping controller? It would seem safe enough to just wrap in a runAsync but is it really needed?
        CompletableFuture.runAsync(jobRunner::requeuePendingJobs);
    }

    private void unpackFiles(File f) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath:scripts/*.ps1");
        for (Resource resource : resources) {
            log.info("Unpacking {} script", resource.getFilename());
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, Paths.get(f.getName(), resource.getFilename()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("Error unpacking changePassword from classpath", e);
            }
        }
    }
}
