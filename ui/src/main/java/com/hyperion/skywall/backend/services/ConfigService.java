package com.hyperion.skywall.backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hyperion.skywall.PathUtil;
import com.hyperion.skywall.backend.model.config.Config;
import com.hyperion.skywall.backend.model.config.Delay;
import com.hyperion.skywall.backend.model.config.StartupLog;
import com.hyperion.skywall.backend.model.config.service.Host;
import com.hyperion.skywall.backend.model.filter.FilterConfig;
import com.hyperion.skywall.backend.model.filter.ProcessConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    public static final String FILTER_CONFIG_LOCATION = "filter";
    public static final String FILTER_FILE_NAME = "hosts.json";
    public static final String FILTER_HOSTS_LOCATION = "hosts";
    public static final String FILTER_PROCESSES_LOCATION = "processes";
    public static final String FILTER_PROCESSES_FILE = "processes.json";
    public static final String STARTUP_LOG_FILE = "startup.json";
    public static final String FILE_LOCATION = "data";
    public static final String FILE_NAME = "config.json";
    public static final String STOCK_PASSWORD = "P@ssw0rd";

    private Config config;
    private FilterConfig filterConfig;
    private ProcessConfig processConfig;
    private StartupLog startupLog;
    public static final ObjectMapper mapper;
    private static final DefaultPrettyPrinter printer;

    private final ResourceLoader resourceLoader;

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setDateFormat(new SimpleDateFormat("MM/dd/yyyy HH:mm"));
        // 4 space indent for arrays and objects, as god intended
        DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("    ", DefaultIndenter.SYS_LF);
        printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
    }

    @Autowired
    public ConfigService(ResourceLoader resourceLoader) throws IOException {
        this.resourceLoader = resourceLoader;
        config = refreshFile();
        filterConfig = refreshFilterConfigFile();
        processConfig = refreshProcessConfigFile();
    }

    public void reloadConfig() {
        try {
            log.info("Reloading config");
            config = refreshFile();
        } catch (IOException e) {
            log.error("Error reloading config", e);
        }
    }

    public void reloadFilterConfig() {
        try {
            filterConfig = refreshFilterConfigFile();
        } catch (IOException e) {
            log.error("Error reloading filter config", e);
        }
    }

    public void reloadProcessConfig() {
        try {
            processConfig = refreshProcessConfigFile();
        } catch (IOException e) {
            log.error("Error reloading process config", e);
        }
    }

    public void reloadStartupLog() {
        try {
            startupLog = refreshStartupLogFile();
        } catch (IOException e) {
            log.error("Error reloading startup log", e);
        }
    }

    protected Config refreshFile() throws IOException {
        Config returnVal;
        Path file = PathUtil.getWindowsPath(FILE_LOCATION, FILE_NAME);
        if (Files.notExists(file)) {
            Resource defaultFile = resourceLoader.getResource("classpath:default-config/config.json");
            try (InputStream content = defaultFile.getInputStream()) {
                returnVal = mapper.readValue(content, Config.class);
                // in the case where we're unpacking a fresh install, make sure things are not enabled as leftovers
                returnVal.setStrictModeEnabled(false);
                returnVal.setFormerAdminUsers(Collections.emptyList());
                returnVal.setDelay(Delay.ZERO);
            }
            Files.createDirectories(file.getParent());
            Files.write(file.toAbsolutePath(), mapper.writer(printer).writeValueAsString(returnVal).getBytes());
        } else {
            returnVal = mapper.readValue(Files.newInputStream(file), Config.class);
        }

        return returnVal;
    }

    private FilterConfig refreshFilterConfigFile() throws IOException {
        FilterConfig returnVal;
        Path file = PathUtil.getWindowsPath(FILTER_CONFIG_LOCATION, FILTER_HOSTS_LOCATION, FILTER_FILE_NAME);
        if (Files.notExists(file)) {
            Resource defaultFile = resourceLoader.getResource("classpath:default-config/hosts.json");
            try (InputStream content = defaultFile.getInputStream()) {
                returnVal = mapper.readValue(content, FilterConfig.class);
                returnVal.setFilterActive(true);
            }
            Files.createDirectories(file.getParent());
            Files.write(file.toAbsolutePath(), mapper.writer(printer).writeValueAsString(returnVal).getBytes());
        } else {
            returnVal = mapper.readValue(Files.newInputStream(file), FilterConfig.class);
        }

        return returnVal;
    }

    private ProcessConfig refreshProcessConfigFile() throws IOException {
        ProcessConfig returnVal;
        Path file = PathUtil.getWindowsPath(FILTER_CONFIG_LOCATION, FILTER_PROCESSES_LOCATION, FILTER_PROCESSES_FILE);
        if (Files.notExists(file)) {
            Resource defaultFile = resourceLoader.getResource("classpath:default-config/processes.json");
            try (InputStream content = defaultFile.getInputStream()) {
                returnVal = mapper.readValue(content, ProcessConfig.class);
            }
            Files.createDirectories(file.getParent());
            Files.write(file.toAbsolutePath(), mapper.writer(printer).writeValueAsString(returnVal).getBytes());
        } else {
            returnVal = mapper.readValue(Files.newInputStream(file), ProcessConfig.class);
        }

        return returnVal;
    }

    private StartupLog refreshStartupLogFile() throws IOException {
        StartupLog returnVal;
        Path file = PathUtil.getWindowsPath(FILTER_CONFIG_LOCATION, STARTUP_LOG_FILE);
        if (Files.notExists(file)) {
            returnVal = new StartupLog();
        } else {
            returnVal = mapper.readValue(Files.newInputStream(file), StartupLog.class);
        }

        return returnVal;
    }

    public boolean isHallPassUsed() {
        return config.isHallPassUsed();
    }

    public boolean isEnabled() {
        return 0 == getDelaySeconds();
    }

    public int getDelaySeconds() {
        return Delay.valueInSeconds(config.getDelay());
    }

    public Delay getCurrentDelay() {
        return config.getDelay();
    }

    public void setDelay(Delay delay) {
        config.setDelay(delay);
    }

    public void writeFile() {
        try {
            Path path = PathUtil.getWindowsPath(FILE_LOCATION, FILE_NAME);
            Files.write(path, mapper.writer(printer).writeValueAsString(config).getBytes());
        } catch (IOException e) {
            log.error("Error writing config", e);
        }
    }

    public ByteArrayInputStream exportDefinedServices() {
        try {
            return new ByteArrayInputStream(mapper.writer(printer).writeValueAsBytes(config.getDefinedServices()));
        } catch (JsonProcessingException e) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    public boolean importDefinedServices(InputStream inputStream) {
        try {
            com.hyperion.skywall.backend.model.config.service.Service[] newServices =
                    mapper.readValue(inputStream, com.hyperion.skywall.backend.model.config.service.Service[].class);

            Map<String, com.hyperion.skywall.backend.model.config.service.Service> currentServices =
                    config.getDefinedServices().stream().collect(Collectors.toMap(
                            com.hyperion.skywall.backend.model.config.service.Service::getName, Function.identity()));
            for (com.hyperion.skywall.backend.model.config.service.Service newService : newServices) {
                if (!currentServices.containsKey(newService.getName())) {
                    currentServices.put(newService.getName(), newService);
                } else {
                    com.hyperion.skywall.backend.model.config.service.Service current = currentServices.get(newService.getName());
                    Map<String, Host> currentHosts = new HashMap<>();
                    for (Host host : current.getHosts()) {
                        if (!currentHosts.containsKey(host.getHost())) {
                            currentHosts.put(host.getHost(), host);
                        }
                    }
                    for (Host host : newService.getHosts()) {
                        if (!currentHosts.containsKey(host.getHost())) {
                            currentHosts.put(host.getHost(), host);
                        }
                    }
                    current.setHosts(new ArrayList<>(currentHosts.values()));
                }
            }
            withTransaction(conf -> conf.setDefinedServices(new ArrayList<>(currentServices.values())));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void writeFilterConfig(FilterConfig filterConfig) {
        try {
            Path path = PathUtil.getWindowsPath(FILTER_CONFIG_LOCATION, FILTER_HOSTS_LOCATION, FILTER_FILE_NAME);
            Files.write(path, mapper.writer(printer).writeValueAsString(filterConfig).getBytes());
        } catch (IOException e) {
            log.error("Error writing config", e);
        }
    }

    public void writeProcessConfig(ProcessConfig processConfig) {
        try {
            Path path = PathUtil.getWindowsPath(FILTER_CONFIG_LOCATION, FILTER_PROCESSES_LOCATION, FILTER_PROCESSES_FILE);
            Files.write(path, mapper.writer(printer).writeValueAsString(processConfig).getBytes());
        } catch (IOException e) {
            log.error("Error writing config", e);
        }
    }

    public void writeStartupLog(StartupLog startupLog) {
        try {
            Path path = PathUtil.getWindowsPath(FILTER_CONFIG_LOCATION, STARTUP_LOG_FILE);
            Files.write(path, mapper.writer(printer).writeValueAsString(startupLog).getBytes());
        } catch (IOException e) {
            log.error("Error writing startup log", e);
        }
    }

    public Config getConfig() {
        return config;
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public StartupLog getStartupLog() {
        return startupLog;
    }

    public long getRecentStartupCount() {
        reloadStartupLog();
        Instant now = Instant.now();
        Duration fifteenMinutes = Duration.of(15, ChronoUnit.MINUTES);
        return startupLog.getTimes().stream().filter(time -> {
            Duration elapsed = Duration.between(time.toInstant(), now);
            return elapsed.compareTo(fifteenMinutes) < 0;
        }).count();
    }

    public boolean fifteenMinutesSinceLastConnectionChange() {
        reloadStartupLog();

        List<Date> sortedTimes = startupLog.getTimes().stream().sorted().collect(Collectors.toList());
        Date mostRecent = sortedTimes.get(sortedTimes.size() - 1);
        Date now = new Date();

        Duration elapsedTime = Duration.between(mostRecent.toInstant(), now.toInstant());
        Duration fifteenMinutes = Duration.of(15, ChronoUnit.MINUTES);
        return elapsedTime.compareTo(fifteenMinutes) >= 0;
    }

    public void withTransaction(ConfigTransaction action) {
        action.updateConfig(config);
        writeFile();
    }

    public void withFilterTransaction(FilterTransaction filterTransaction) {
        filterTransaction.updateConfig(filterConfig);
        writeFilterConfig(filterConfig);
    }

    public void withProcessTransaction(ProcessTransaction configTransaction) {
        configTransaction.updateConfig(processConfig);
        writeProcessConfig(processConfig);
    }

    public void withStartupLogTransaction(StartupLogTransaction startupLogTransaction) {
        startupLogTransaction.update(startupLog);
        writeStartupLog(startupLog);
    }
}
