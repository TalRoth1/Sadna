package org.example.ApplicationLayer.dto.PurchaseDTOs;

import org.example.ApplicationLayer.PaymentDetails;

public class CompletePurchaseRequest {
    public PaymentDetails paymentDetails;
    public String couponCode;
    public CompletePurchaseRequest() {}
}
