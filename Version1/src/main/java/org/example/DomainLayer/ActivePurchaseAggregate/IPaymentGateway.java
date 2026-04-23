package org.example.DomainLayer.ActivePurchaseAggregate;

public interface IPaymentGateway
{
    boolean pay(String userID, double amount);
}
