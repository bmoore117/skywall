package com.hyperion.skywall;

import com.hyperion.skywall.backend.services.JobRunner;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

@Component
public class StartupTaskRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupTaskRunner.class);

    private final ResourcePatternResolver resourcePatternResolver;
    private final JobRunner jobRunner;
    private final WinUtils winUtils;

    @Autowired
    public StartupTaskRunner(JobRunner jobRunner, WinUtils winUtils,
                             ResourcePatternResolver resourcePatternResolver) {
        this.jobRunner = jobRunner;
        this.winUtils = winUtils;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path f = PathUtil.getWindowsPath(Paths.get("scripts"));
            if (!Files.exists(f)) {
                Files.createDirectories(f);
            }
            unpackFiles(f);
        } catch (IOException e) {
            log.error("Error unpacking scripts", e);
        }

        // don't block startup waiting - allow the user to hit the main page if they left the launch when ready option
        // checked during install
        CompletableFuture.runAsync(() -> {
            try {
                winUtils.trustCert();
            } catch (IOException | InterruptedException e) {
                log.error("Error running trustCert.ps1", e);
            }
        });

        // todo is this needed if we have ping controller? It would seem safe enough to just wrap in a runAsync but is it really needed?
        CompletableFuture.runAsync(jobRunner::requeuePendingJobs);
    }

    private void unpackFiles(Path f) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath:scripts/*.ps1");
        for (Resource resource : resources) {
            log.info("Unpacking {} script", resource.getFilename());
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, Paths.get(f.toAbsolutePath().toString(), resource.getFilename()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("Error unpacking changePassword from classpath", e);
            }
        }
    }
}
