package com.hyperion.skywall.backend.services;

import com.hyperion.skywall.backend.model.filter.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private static final Pattern CERTIFICATE_PATTERN = Pattern.compile("(The client may not trust the proxy's certificate for (.*?)).$");
    private static final Path LOG_DIR = Paths.get("C:\\Users\\Public\\Documents\\skywall-logs\\filter");

    public List<LogEntry> getLogEntries() {
        List<LogEntry> results = new LinkedList<>();
        LocalDate today = LocalDate.now();
        Path logFile = LOG_DIR.resolve("skywall-filter_" + today + ".out.log");
        try {
            List<String> lines = Files.readAllLines(logFile);
            for (String line : lines) {
                Matcher m = CERTIFICATE_PATTERN.matcher(line);
                if (m.find()) {
                    results.add(new LogEntry(m.group(1), null));
                }
            }
        } catch (IOException e) {
            log.error("Error reading log file", e);
        }

        return results;
    }
}
