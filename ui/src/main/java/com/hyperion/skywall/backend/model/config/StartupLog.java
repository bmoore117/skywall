package com.hyperion.skywall.backend.model.config;

import java.util.Date;
import java.util.List;

public class StartupLog {

    private List<Date> times;

    public List<Date> getTimes() {
        return times;
    }

    public void setTimes(List<Date> times) {
        this.times = times;
    }
}
