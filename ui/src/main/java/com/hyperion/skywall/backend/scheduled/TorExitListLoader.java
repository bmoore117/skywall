package com.hyperion.skywall.backend.scheduled;

import com.hyperion.skywall.PathUtil;
import com.hyperion.skywall.backend.services.WinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Component
public class TorExitListLoader {

    private static final Logger log = LoggerFactory.getLogger(TorExitListLoader.class);

    private static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
    };

    private final WinUtils winUtils;

    @Autowired
    public TorExitListLoader(WinUtils winUtils) {
        this.winUtils = winUtils;
    }

    // every 30 minutes, initial delay of 10s to not conflict with script extraction in StartupTaskRunner
    @Scheduled(fixedDelay = 3600000L, initialDelay = 10000L)
    public void downloadTorExitList() {
        String getEndpoint = "https://www.dan.me.uk/torlist/";

        var request = HttpRequest.newBuilder()
                .uri(URI.create(getEndpoint))
                .GET()
                .build();

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

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
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Error fetching tor exit list", e);
        }
    }
}
