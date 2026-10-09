package io.github.kennethfan.mpes.geo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * 地理坐标值对象（lat/lon），映射为 ES geo_point 类型。
 * Jackson 序列化为 {@code {"lat":..,"lon":..}}（ES geo_point 原生格式）。
 */
public final class GeoPoint implements Serializable {

    private final double lat;
    private final double lon;

    @JsonCreator
    public GeoPoint(@JsonProperty("lat") double lat, @JsonProperty("lon") double lon) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("纬度须在 [-90, 90]，实际: " + lat);
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("经度须在 [-180, 180]，实际: " + lon);
        }
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoPoint that)) {
            return false;
        }
        return Double.compare(lat, that.lat) == 0 && Double.compare(lon, that.lon) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }

    @Override
    public String toString() {
        return "GeoPoint(" + lat + ", " + lon + ")";
    }
}
