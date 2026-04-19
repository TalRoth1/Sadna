package org.example.DomainLayer.EventAggregate;

/**
 * Seated zone (assigned seats; individual seats are modeled as {@link SittingTicket}s).
 */
public class SittingArea extends Area {

    public SittingArea(int areaId, double price) {
        super(areaId, price);
    }
}
