package org.example.DomainLayer.PolicyManagment;

import java.time.LocalDate;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;

public class CouponCode implements IDiscountRule {
    private final UUID id = UUID.randomUUID();
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final float discoutPrecent;
    private final String couponCode;

    public CouponCode(LocalDate from, LocalDate to, float discountPrecent, String couponCode) {
        this.fromDate = from;
        this.toDate = to;
        this.discoutPrecent = discountPrecent;
        this.couponCode = couponCode;
    }

    public UUID getId() {
        return this.id;
    }

    public String getCode() {
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

    public boolean matchesCode(String providedCouponCode) {
        return providedCouponCode != null
                && couponCode != null
                && couponCode.equals(providedCouponCode.trim());
    }

    public boolean isActiveNow() {
        LocalDate today = LocalDate.now();

        boolean startsOk = fromDate == null || !today.isBefore(fromDate);
        boolean endsOk = toDate == null || !today.isAfter(toDate);

        return startsOk && endsOk;
    }

    public float apply(ActivePurchase purchase, String providedCouponCode) {
        if (!matchesCode(providedCouponCode)) {
            return purchase.getPrice();
        }

        if (!isActiveNow()) {
            throw new DomainException("date is not in the discount time");
        }

        return purchase.getPrice() * ((100 - discoutPrecent) / 100.0f);
    }

    @Override
    public float apply(ActivePurchase purchase) {
        String purchaseCoupon = purchase.getCoupon();

        if (purchaseCoupon == null || purchaseCoupon.isBlank()) {
            return purchase.getPrice();
        }

        return apply(purchase, purchaseCoupon);
    }
}