package org.example.DomainLayer.PurchaseHistoryAggregate;

public class Payment {
    private final double total;
    private final String paymentInfo;
    // External clearing-system transaction id captured at charge time. Needed
    // to issue a refund later (e.g. on event cancellation). Immutable.
    // Defaults to -1 for historical records created before it was tracked.
    private final int transactionId;

    public Payment(double total, String paymentInfo) {
        this(total, paymentInfo, -1);
    }

    public Payment(double total, String paymentInfo, int transactionId) {
        this.total = total;
        this.paymentInfo = paymentInfo;
        this.transactionId = transactionId;
    }

    public double getTotal() {
        return total;
    }

    public String getPaymentInfo() {
        return paymentInfo;
    }

    public int getTransactionId() {
        return transactionId;
    }
}
