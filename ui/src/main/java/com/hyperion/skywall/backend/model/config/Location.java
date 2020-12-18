package com.hyperion.skywall.backend.model.config;

import java.util.Objects;

public class Location {

    private String name;
    private Double lat;
    private Double lon;

    public Location() {}

    public Location(String name, Double lat, Double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(name, location.name) &&
                Objects.equals(lat, location.lat) &&
                Objects.equals(lon, location.lon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, lat, lon);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }
}
