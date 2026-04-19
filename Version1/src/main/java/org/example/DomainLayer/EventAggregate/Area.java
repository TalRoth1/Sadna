package org.example.DomainLayer.EventAggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Priced zone in a venue layout. References ticket ids owned by the {@link Event} aggregate (1:* tickets).
 */
public abstract class Area {

    private final int areaId;
    private double price;
    private final List<Integer> ticketIds = new ArrayList<>();

    protected Area(int areaId, double price) {
        this.areaId = Objects.requireNonNull(areaId);
        if (price < 0) {
            throw new IllegalArgumentException("price must be non-negative");
        }
        this.price = price;
    }

    public int getAreaId() {
        return areaId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("price must be non-negative");
        }
        this.price = price;
    }

    public List<Integer> getTicketIdsView() {
        return Collections.unmodifiableList(ticketIds);
    }

    /**
     * Registers a ticket id issued for this area (ticket object lives on {@link Event}).
     */
    public void linkTicketId(int ticketId) {
        if (ticketIds.contains(Objects.requireNonNull(ticketId))) {
            throw new IllegalStateException("ticket already linked to area: " + ticketId);
        }
        ticketIds.add(ticketId);
    }
}
