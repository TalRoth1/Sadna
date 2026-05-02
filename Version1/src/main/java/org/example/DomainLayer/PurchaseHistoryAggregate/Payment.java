package org.example.DomainLayer.PurchaseHistoryAggregate;

public class Payment {
    private final double total;
    private final String paymentInfo;

    public Payment(double total, String paymentInfo) {
        this.total = total;
        this.paymentInfo = paymentInfo;
    }

    public double getTotal() {
        return total;
    }

    public String getPaymentInfo() {
        return paymentInfo;
    }
}
