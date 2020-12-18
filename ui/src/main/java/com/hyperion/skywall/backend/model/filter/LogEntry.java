package com.hyperion.skywall.backend.model.filter;

import java.time.LocalDateTime;

public class LogEntry {
    private String host;
    private LocalDateTime time;

    public LogEntry(String host, LocalDateTime time) {
        this.host = host;
        this.time = time;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}
