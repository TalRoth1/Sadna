package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

/**
 * Rule evaluated against a purchase context; part of {@link PurchasePolicy}.
 */
public interface IPurchaseRule {

    boolean doesHold(ActivePurchase ap, User user, Event event);
}
