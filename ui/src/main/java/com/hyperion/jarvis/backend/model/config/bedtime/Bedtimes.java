package com.hyperion.jarvis.backend.model.config.bedtime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public class Bedtimes {

    private LocalTime sunday;
    private LocalTime monday;
    private LocalTime tuesday;
    private LocalTime wednesday;
    private LocalTime thursday;
    private LocalTime friday;
    private LocalTime saturday;

    public LocalTime getSunday() {
        return sunday;
    }

    public void setSunday(LocalTime sunday) {
        this.sunday = sunday;
    }

    public LocalTime getMonday() {
        return monday;
    }

    public void setMonday(LocalTime monday) {
        this.monday = monday;
    }

    public LocalTime getTuesday() {
        return tuesday;
    }

    public void setTuesday(LocalTime tuesday) {
        this.tuesday = tuesday;
    }

    public LocalTime getWednesday() {
        return wednesday;
    }

    public void setWednesday(LocalTime wednesday) {
        this.wednesday = wednesday;
    }

    public LocalTime getThursday() {
        return thursday;
    }

    public void setThursday(LocalTime thursday) {
        this.thursday = thursday;
    }

    public LocalTime getFriday() {
        return friday;
    }

    public void setFriday(LocalTime friday) {
        this.friday = friday;
    }

    public LocalTime getSaturday() {
        return saturday;
    }

    public void setSaturday(LocalTime saturday) {
        this.saturday = saturday;
    }

    public LocalTime today() {
        LocalDate date = LocalDate.now();
        DayOfWeek dow = date.getDayOfWeek();

        LocalTime cutoffTime;
        if ("sunday".equalsIgnoreCase(dow.toString())) {
            cutoffTime = getSunday();
        } else if ("monday".equalsIgnoreCase(dow.toString())) {
            cutoffTime = getMonday();
        } else if ("tuesday".equalsIgnoreCase(dow.toString())) {
            cutoffTime = getTuesday();
        } else if ("wednesday".equalsIgnoreCase(dow.toString())) {
            cutoffTime = getWednesday();
        } else if ("thursday".equalsIgnoreCase(dow.toString())) {
            cutoffTime = getThursday();
        } else if ("friday".equalsIgnoreCase(dow.toString())) {
            cutoffTime = getFriday();
        } else {
            cutoffTime = getSaturday();
        }

        return cutoffTime;
    }
}
