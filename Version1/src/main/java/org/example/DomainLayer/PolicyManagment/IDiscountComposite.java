package org.example.DomainLayer.PolicyManagment;

import java.util.List;
import java.util.UUID;

public interface IDiscountComposite extends IDiscountRule{
    public void addRule(IDiscountRule rule);
    public void removeRule(UUID id);
    public List<IDiscountRule> getRules();
}
