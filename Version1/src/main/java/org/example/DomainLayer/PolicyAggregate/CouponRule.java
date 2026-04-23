package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;

public class CouponRule implements IDiscountRule //TODO:
{
    private final String couponCode;
    private final double discountPercentage;

    public CouponRule(String couponCode, double discountPercentage) {
        this.couponCode = couponCode;
        this.discountPercentage = discountPercentage;
    }

    public boolean matches(String enteredCode) {
        return couponCode.equals(enteredCode);
    }

    @Override
    public void apply(ActivePurchase ap, Event event)
    {
        if (matches(couponCode))
        {
            for (int i = 0; i < ap.getTicketIDs().size(); i++)
            {
                int ticketId = ap.getTicketIDs().get(i);
                ap.setNewPrice(ticketId, ap.getCurrentPrice(ticketId) * (1 - discountPercentage));
            }
        }
    }
}
