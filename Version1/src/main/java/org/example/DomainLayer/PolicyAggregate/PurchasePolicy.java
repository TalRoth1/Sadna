package org.example.DomainLayer.PolicyAggregate;

public class PurchasePolicy implements IPurchaseRule{
    private double minAge = 18;
    private int minTicket = 1;
    private int maxTicket = Integer.MAX_VALUE;
    private boolean allowLoneSeat = true;

    public PurchasePolicy(){}
    public PurchasePolicy(double minAge, int minTicket, int maxTicket, boolean allowLoneSeat)
    {
        this.minAge = minAge;
        this.minTicket = minTicket;
        this.maxTicket = maxTicket;
        this.allowLoneSeat = allowLoneSeat;
    }

    public double getMinAge()
    {
        return this.minAge;
    }

    public int getMinTicket()
    {
        return this.minTicket;
    }

    public int getMaxTicket()
    {
        return this.maxTicket;
    }

    public boolean getAllowLoneSeat()
    {
        return this.allowLoneSeat;
    }

    public void setMinAge(double minAge)
    {
        this.minAge = minAge;
    }

    public void setMinTicket(int minTicket)
    {
        this.minTicket = minTicket;
    }

    public void setMaxTicket(int maxTicket)
    {
        this.maxTicket = maxTicket;
    }

    public void ChangeLoneSeat()
    {
        this.allowLoneSeat = !allowLoneSeat;
    }
}
