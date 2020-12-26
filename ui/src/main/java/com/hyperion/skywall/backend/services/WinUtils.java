package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WinUtils {

    public static final String FILTER_SERVICE_NAME = "SkyWall Filter";

    private static final Logger log = LoggerFactory.getLogger(WinUtils.class);

    private static final boolean inDevMode = "dev".equalsIgnoreCase(System.getenv("RUN_MODE"));
    public static final String GPS_ERROR = "Access Denied for Location Information";

    private Pair<Process, String> runProc(ProcessBuilder builder) throws IOException, InterruptedException {
        Process p = builder.start();
        p.waitFor();

        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream stdOut = p.getInputStream(); InputStream stdErr = p.getErrorStream()) {
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdOut));
            while ((line = reader.readLine()) != null) {
                log.info(line);
                stringBuilder.append(line).append("\n");
            }
            reader = new BufferedReader(new InputStreamReader(stdErr));
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
                log.error(line);
            }
        }

        return Pair.of(p, stringBuilder.toString().trim());
    }

    public void restartService(String serviceName) throws IOException, InterruptedException {
        if (inDevMode) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));
        builder.command("powershell.exe", "-File", "restartService.ps1", "-serviceName", serviceName);

        runProc(builder);
    }

    public void stopService(String serviceName) throws IOException, InterruptedException {
        if (inDevMode) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));
        builder.command("powershell.exe", "-File", "stopService.ps1", "-serviceName", serviceName);

        runProc(builder);
    }

    public void startService(String serviceName) throws IOException, InterruptedException {
        if (inDevMode) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));
        builder.command("powershell.exe", "-File", "startService.ps1", "-serviceName", serviceName);

        runProc(builder);
    }

    public void restartComputer() throws IOException, InterruptedException {
        if (inDevMode) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));
        builder.command("powershell.exe", "-File", "restartComputer.ps1");

        runProc(builder);
    }

    public String generatePassword() {
        int leftLimit = 33; // numeral '0'
        int rightLimit = 126; // character '~'
        int targetStringLength = 12;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public int changeLocalAdminPassword(String newPassword) {
        if (inDevMode) {
            return 0;
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));
        builder.command("powershell.exe", "-File", "changePassword.ps1", "-password", newPassword);

        try {
            Pair<Process, String> p = runProc(builder);
            return p.first.exitValue();
        } catch (IOException | InterruptedException e) {
            log.error("Error running changePassword.ps1", e);
            return -1;
        }
    }

    public Optional<Double[]> getGpsCoordinates() throws IOException, InterruptedException, GPSAccessDeniedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));
        builder.command("powershell.exe", "-File", "gpsLocation.ps1");

        Pair<Process, String> results = runProc(builder);

        if (results.second.contains(GPS_ERROR)) {
            throw new GPSAccessDeniedException();
        } else {
            if (!results.second.isBlank()) {
                return Optional.of(Arrays.stream(results.second.split(",")).map(Double::valueOf).toArray(Double[]::new));
            } else {
                return Optional.empty();
            }
        }
    }

    public List<String> toggleStrictMode(boolean on, List<String> usersToReEnable) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(new File("scripts"));

        if (on) {
            builder.command("powershell.exe", "-File", "toggleStrictMode.ps1", "-mode", "on");
        } else {
            builder.command("powershell.exe", "-File", "toggleStrictMode.ps1", "-mode", "off", String.join("~,~", usersToReEnable));
        }
        Pair<Process, String> returnVal = runProc(builder);
        if (!returnVal.second.isBlank()) {
            return Arrays.stream(returnVal.second.split("~,~")).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
