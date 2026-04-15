package org.example.DomainLayer.EventAggregate;

import java.util.ArrayList;
import java.util.List;

/**
 * Venue layout for one event (1:1 with {@link Event}). Owns {@link Area}s (1:*).
 * {@code mapImage} holds a reference for the visual map.
 */
public class Layout {

    private final List<Area> areas = new ArrayList<>();
    private String mapImage;

    public String getMapImage() {
        return mapImage;
    }

    public void setMapImage(String mapImage) {
        this.mapImage = mapImage;
    }

    public List<Area> getAreasView() {
        return List.copyOf(areas);
    }

    public void addArea(Area area) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        areas.add(area);
    }

    public Area requireArea(String areaId) {
        if (areaId == null) throw new IllegalArgumentException("areaId must not be null");
        for (Area a : areas) {
            if (areaId.equals(a.getAreaId())) {
                return a;
            }
        }
        throw new IllegalArgumentException("unknown area: " + areaId);
    }
}
