package org.example.DomainLayer.EventAggregate;

/**
 * Seated zone (assigned seats; individual seats are modeled as {@link SittingTicket}s).
 */
public class SittingArea extends Area {

    public SittingArea(String areaId, double price) {
        super(areaId, price);
    }
}
