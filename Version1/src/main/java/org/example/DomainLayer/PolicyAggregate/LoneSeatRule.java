package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class LoneSeatRule implements IPurchaseRule {

    private boolean allowLoneSeat;

    public LoneSeatRule(boolean allowLoneSeat)
    {
        this.allowLoneSeat = allowLoneSeat;
    }

    @Override
    public boolean doesHold(ActivePurchase purchase, User user) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'doesHold'");
    }
    
}
