package org.example.DomainLayer.PolicyAggregate;

import java.time.LocalDate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class CouponCode implements IDiscountRule {
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

    public float apply(ActivePurchase purchase) 
    {
        if(!purchase.getCoupon().equals(couponCode) || LocalDate.now().isBefore(fromDate) || LocalDate.now().isAfter(toDate))
            return purchase.getPrice();
        return purchase.getPrice() * ((100 - discoutPrecent)/100.0f);
    }
    
}
