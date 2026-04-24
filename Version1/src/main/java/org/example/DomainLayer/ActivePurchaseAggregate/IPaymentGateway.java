package org.example.DomainLayer.ActivePurchaseAggregate;

import org.example.ApplicationLayer.PaymentDetails;

public interface IPaymentGateway
{
    boolean pay(String userID, double amount, PaymentDetails paymentDetails);
}
