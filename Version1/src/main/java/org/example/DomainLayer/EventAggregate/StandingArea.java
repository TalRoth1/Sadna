package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

/**
 * Standing-room zone (no assigned seats).
 */
public class StandingArea extends Area {

    public StandingArea(UUID areaId, double price) {
        super(areaId, price);
    }
}
