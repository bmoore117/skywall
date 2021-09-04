package com.hyperion.skywall.backend.scheduled;

import com.hyperion.skywall.PathUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Scheduled(fixedDelay = 3600000L)
    public void downloadTorExitList() {
        String getEndpoint = "https://check.torproject.org/torbulkexitlist";

        var request = HttpRequest.newBuilder()
                .uri(URI.create(getEndpoint))
                .GET()
                .build();

        var client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String[] addresses = response.body().split("\n");
            JSONArray array = new JSONArray();
            for (String address : addresses) {
                array.put(address);
            }
            JSONObject nodes = new JSONObject();
            nodes.put("nodes", array);
            Path file = PathUtil.getWindowsPath("filter", "tor", "nodes.json");
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, nodes.toString(4).getBytes());
            log.info("Successfully fetched tor exit node list");
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching tor exit list", e);
        }
    }
}
