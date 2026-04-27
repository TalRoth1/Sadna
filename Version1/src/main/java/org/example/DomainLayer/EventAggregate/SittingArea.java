package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

/**
 * Seated zone (assigned seats; individual seats are modeled as {@link SittingTicket}s).
 */
public class SittingArea extends Area {

    public SittingArea(UUID areaId, double price) {
        super(areaId, price);
    }
}
