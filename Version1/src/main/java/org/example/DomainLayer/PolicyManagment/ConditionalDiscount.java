package org.example.DomainLayer.PolicyManagment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class ConditionalDiscount implements IDiscountRule
{
    private final UUID id = UUID.randomUUID();
    private LocalDate fromDate;
    private LocalDate toDate;
    private float discoutPrecent;
    private int requiredTickets;
    private int appliedTickets;

    public ConditionalDiscount(LocalDate from, LocalDate to, float discount, int requiredTickets, int appliedTickets)
    {
        this.fromDate = from;
        this.toDate = to;
        this.discoutPrecent = discount;
        this.requiredTickets = requiredTickets;
        this.appliedTickets = appliedTickets;
    }

    public UUID getId()
    {
        return this.id;
    }

    public float getDiscountPercent() {
        return this.discoutPrecent;
    }

    public LocalDate getFromDate() {
        return this.fromDate;
    }

    public LocalDate getToDate() {
        return this.toDate;
    }

    public int getRequiredTickets() {
        return this.requiredTickets;
    }

    public int getAppliedTickets() {
        return this.appliedTickets;
    }

    public float apply(ActivePurchase purchase)
    {
        float price = purchase.getPrice();
        LocalDate today = LocalDate.now();

        if (today.isBefore(fromDate) || today.isAfter(toDate)) {
            return price;
        }

        int bundleSize = requiredTickets + appliedTickets;
        Map<UUID, Float> ticketIdPrice = purchase.getTicketIDs();

        // "Buy 3, get 1" requires a full 4-ticket bundle: 3 paid + 1 discounted.
        if (requiredTickets <= 0 || appliedTickets <= 0 || bundleSize <= 0 || ticketIdPrice.size() < bundleSize) {
            return price;
        }

        int discountedTicketCount = Math.min(
                ticketIdPrice.size(),
                (ticketIdPrice.size() / bundleSize) * appliedTickets
        );

        PriorityQueue<Map.Entry<UUID, Float>> pq = new PriorityQueue<>(Comparator.comparing(Map.Entry::getValue));
        pq.addAll(ticketIdPrice.entrySet());
        List<Map.Entry<UUID, Float>> lowestPriceTickets = new ArrayList<>();

        for (int i = 0; i < discountedTicketCount && !pq.isEmpty(); i++) {
            lowestPriceTickets.add(pq.poll());
        }

        for (Map.Entry<UUID,Float> entry : lowestPriceTickets)
        {
            price -= entry.getValue();
            price += entry.getValue() * ((100 - discoutPrecent) / 100.0f);
        }
        return price;
    }
}
