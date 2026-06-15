package org.example.ApplicationLayer;

public class PaymentResult {
    private final boolean successful;
    private final int transactionId;

    public PaymentResult(boolean successful, int transactionId) {
        this.successful = successful;
        this.transactionId = transactionId;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public static PaymentResult success(int transactionId) {
        return new PaymentResult(true, transactionId);
    }

    public static PaymentResult failure() {
        return new PaymentResult(false, -1);
    }
}