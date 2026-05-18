package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public interface IPurchaseRule {
    public UUID getId();
    public boolean doesHold(ActivePurchase purchase, User user, Event event);
}
