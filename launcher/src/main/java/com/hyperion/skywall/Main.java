package com.hyperion.skywall;

import java.awt.*;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Main {

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        URI target = new URI("http://localhost:9090");

        for (int i = 0; i < 5; i++) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .GET().uri(target)
                        .timeout(Duration.ofSeconds(20))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    break;
                }
            } catch (ConnectException ignored) {}
            Thread.sleep(1500);
        }

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(target);
        }
    }
}
