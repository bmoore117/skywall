package com.hyperion.skywall.backend.scheduled;

import com.hyperion.skywall.PathUtil;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TorExitListLoader {

    private static final Logger log = LoggerFactory.getLogger(TorExitListLoader.class);

    private final WinUtils winUtils;

    @Autowired
    public TorExitListLoader(WinUtils winUtils) {
        this.winUtils = winUtils;
    }

    // every 30 minutes, initial delay of 10s to not conflict with script extraction in StartupTaskRunner
    @Scheduled(fixedDelay = 1800000L, initialDelay = 10000L)
    public void downloadTorExitList() {
        String getEndpoint = "https://www.dan.me.uk/torlist/";

        var request = HttpRequest.newBuilder()
                .uri(URI.create(getEndpoint))
                .GET()
                .build();

        var client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HttpStatus.OK.value()) {
                Path file = PathUtil.getWindowsPath("tor", "nodes.txt");
                if (!Files.exists(file.getParent())) {
                    Files.createDirectories(file.getParent());
                }
                Files.write(file, response.body().getBytes());
                log.info("Successfully fetched tor node list");
                log.info("Updating firewall rules");
                winUtils.blockTor(file);
            } else {
                log.warn("Unable to reach tor node list, perhaps you hit the 30 minute window. Http status: {}", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching tor exit list", e);
        }
    }
}
