package org.example.DomainLayer.ActivePurchaseAggregate;

import java.util.UUID;

import org.example.ApplicationLayer.PaymentDetails;

public interface IPaymentGateway
{
    boolean pay(UUID userID, float amount, PaymentDetails paymentDetails);
}
