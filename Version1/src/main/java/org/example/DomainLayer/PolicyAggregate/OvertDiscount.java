package org.example.DomainLayer.PolicyAggregate;

import java.time.LocalDate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;


public class OvertDiscount implements IDiscountRule {
    private float discountPrecent;
    private LocalDate fromDate;
    private LocalDate toDate;

    public OvertDiscount(float discountPrecent, LocalDate fromDate, LocalDate toDate)
    {
        this.discountPrecent = discountPrecent;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public float apply(ActivePurchase purchase)
    {
        if (LocalDate.now().isAfter(fromDate) && LocalDate.now().isBefore(toDate))
            return  purchase.getPrice() * (100 - discountPrecent);
        throw new IllegalArgumentException("date is not in the discount time");
    }
}
