package org.example.DomainLayer.PolicyAggregate;

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
    private UUID id;
    private LocalDate fromDate;
    private LocalDate toDate;
    private float discoutPrecent;
    private int requiredTickets;
    private int appliedTickets;

    public ConditionalDiscount(LocalDate from, LocalDate to, float discount, int requiredTickets, int appliedTickets)
    {
        this.id = UUID.randomUUID();
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

    public float apply(ActivePurchase purchase)
    {
        float price = purchase.getPrice();
        if(purchase.getTicketIDs().size() < requiredTickets || LocalDate.now().isAfter(toDate) || LocalDate.now().isBefore(fromDate))
            return price;
        Map<UUID, Float> ticketIdPrice = purchase.getTicketIDs();
        PriorityQueue<Map.Entry<UUID, Float>> pq = new PriorityQueue<>(Comparator.comparing(Map.Entry::getValue));
        pq.addAll(ticketIdPrice.entrySet());
        List<Map.Entry<UUID, Float>> lowestPriceTickets = new ArrayList<>();
        for (int i = 0; i < appliedTickets && !pq.isEmpty(); i++) {
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
