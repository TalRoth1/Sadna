package org.example.DomainLayer.EventAggregate;

import java.util.ArrayList;
import java.util.List;

/**
 * Venue layout for one event (1:1 with {@link Event}). Owns {@link Area}s (1:*).
 * {@code mapImage} is the venue / hall graphic (diagram: Map Image).
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
        for (Area existing : areas) {
            if (existing.getAreaId() == area.getAreaId()) {
                throw new IllegalStateException("duplicate area id: " + area.getAreaId());
            }
        }
        areas.add(area);
    }

    public Area requireArea(int areaId) {
        for (Area a : areas) {
            if (areaId == a.getAreaId()) {
                return a;
            }
        }
        throw new IllegalArgumentException("unknown area: " + areaId);
    }
}
