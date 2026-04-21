package org.example.DomainLayer.PolicyAggregate;

public interface IPurchaseRule {
    public double getMinAge();
    public int getMinTicket();
    public int getMaxTicket();
    public boolean getAllowLoneSeat();
}
