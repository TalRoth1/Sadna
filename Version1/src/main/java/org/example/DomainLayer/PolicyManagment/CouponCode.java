package org.example.DomainLayer.PolicyManagment;

import java.time.LocalDate;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class CouponCode implements IDiscountRule {
    private final UUID id = UUID.randomUUID(); 
    private LocalDate fromDate;
    private LocalDate toDate;
    private float discoutPrecent;
    private String couponCode;

    public CouponCode(LocalDate from, LocalDate to, float discountPrecent, String couponCode)
    {
        this.fromDate = from;
        this.toDate = to;
        this.discoutPrecent = discountPrecent;
        this.couponCode = couponCode;
    }

    public UUID getId()
    {
        return this.id;
    }

    public String getCode()
    {
        return this.couponCode;
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

    public float apply(ActivePurchase purchase) 
    {
        if(!purchase.getCoupon().equals(couponCode) || LocalDate.now().isBefore(fromDate) || LocalDate.now().isAfter(toDate))
            return purchase.getPrice();
        return purchase.getPrice() * ((100 - discoutPrecent)/100.0f);
    }
    
}
