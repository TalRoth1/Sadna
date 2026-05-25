package org.example.DomainLayer.PolicyManagment;

import java.time.LocalDate;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;


public class OvertDiscount implements IDiscountRule {
    private final UUID id = UUID.randomUUID();
    private float discountPrecent;
    private LocalDate fromDate;
    private LocalDate toDate;

    public OvertDiscount(float discountPrecent, LocalDate fromDate, LocalDate toDate)
    {
        this.discountPrecent = discountPrecent;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public UUID getId()
    {
        return this.id;
    }

    public float getDiscountPercent()
    {
        return this.discountPrecent;
    }

    public LocalDate getFromDate() {
        return this.fromDate;
    }

    public LocalDate getToDate() {
        return this.toDate;
    }

    public float apply(ActivePurchase purchase)
    {
        LocalDate today = LocalDate.now();
        if (today.isBefore(fromDate) || today.isAfter(toDate)) {
            return purchase.getPrice();
        }
        return purchase.getPrice() * ((100 - discountPrecent) / 100.0f);
    }
}
