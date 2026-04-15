package org.example.DomainLayer.EventAggregate;

/**
 * Rule evaluated against a purchase context; part of {@link PurchasePolicy}.
 */
public interface IPurchaseRule {

    boolean doesHold();
}
