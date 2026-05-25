package org.example.DomainLayer.EventAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Venue layout for one event
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
            if (Objects.equals(existing.getAreaId(), area.getAreaId())) {
                throw new IllegalStateException("duplicate area id: " + area.getAreaId());
            }
        }
        areas.add(area);
    }

    public Area requireArea(UUID areaId) 
    {
        for (Area a : areas) {
            if (Objects.equals(areaId, a.getAreaId())) {
                return a;
            }
        }
        throw new IllegalArgumentException("unknown area: " + areaId);
    }

    public void removeArea(UUID areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("areaId is required");
        }

        Area area = requireArea(areaId);
        areas.remove(area);
    }
}
