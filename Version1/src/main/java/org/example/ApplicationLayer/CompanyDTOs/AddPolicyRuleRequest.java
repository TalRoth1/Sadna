package org.example.ApplicationLayer.CompanyDTOs;

public class AddPolicyRuleRequest {
    public String username;
    public Float age;           // null = not set
    public Integer minTicket;
    public Integer maxTicket;
    public Boolean allowLoneSeat;
}
