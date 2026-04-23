package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.UserAggregate.User;

import java.time.LocalDateTime;
import java.util.List;

public class VisibleSaleRule implements IDiscountRule
{
    private final double discountPercentage; // למשל 0.2 = 20%
    private final LocalDateTime endTime;
    private final List<Class<? extends Ticket>> ticketTypes;

    public VisibleSaleRule(double discountPercentage,
                           LocalDateTime endTime,
                           List<Class<? extends Ticket>> ticketTypes)
    {
        if (discountPercentage < 0 || discountPercentage > 1) {
            throw new IllegalArgumentException("discountPercentage must be between 0 and 1");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("endTime must not be null");
        }

        this.discountPercentage = discountPercentage;
        this.endTime = endTime;
        this.ticketTypes = ticketTypes;
    }

    public void apply(ActivePurchase ap, Event event)
    {
        if (LocalDateTime.now().isAfter(endTime) || ap.getTicketIDs().isEmpty()) {
            return;
        }
        for (int ticketId : ap.getTicketIDs())
        {
            Ticket ticket = event.getTicket(ticketId);

            if (matchesTicketType(ticket)) {
                ap.setNewPrice(ticketId, ap.getCurrentPrice(ticketId) * (1 - discountPercentage));
            }
        }

    }

    private boolean matchesTicketType(Ticket ticket)
    {
        if (ticketTypes == null || ticketTypes.isEmpty()) {
            return true;
        }

        for (Class<? extends Ticket> ticketType : ticketTypes)
        {
            if (ticketType.isInstance(ticket)) {
                return true;
            }
        }

        return false;
    }
}