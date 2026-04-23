package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;

import java.util.ArrayList;
import java.util.List;

public class FreeTicketsRule implements IDiscountRule
{
    private int requiredTickets; //קנה מספר כלשהו של כרטיסים
    private int freeTickets; //מספר הכרטיסים שתקבל בחינם

    public FreeTicketsRule(int requiredTickets, int freeTickets)
    {
        this.requiredTickets = requiredTickets;
        this.freeTickets = freeTickets;
    }
    public void apply(ActivePurchase ap, Event event)
    {
        int groupSize = requiredTickets + freeTickets;

        for (int i = 0; i < ap.getTicketIDs().size(); i++)
        {
            int positionInGroup = i % groupSize;

            if (positionInGroup >= requiredTickets)
            {
                int ticketId = ap.getTicketIDs().get(i);
                ap.setNewPrice(ticketId, 0.0);
            }
        }
    }
}
