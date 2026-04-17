package org.example.DomainLayer.EventAggregate;

import java.util.List;

/**
 * Rule evaluated against a purchase context; part of {@link PurchasePolicy}.
 */
public interface IPurchaseRule {

    boolean doesHold();
}
