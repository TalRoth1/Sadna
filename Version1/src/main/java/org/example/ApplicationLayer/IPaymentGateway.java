package org.example.ApplicationLayer;

import java.util.UUID;

public interface IPaymentGateway
{
    boolean pay(UUID userID, float amount, PaymentDetails paymentDetails);
}
