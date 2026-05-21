package org.example.ApplicationLayer.dto.EventDTOs;

import java.util.UUID;

public class AddEventPolicyRuleRequest {
    public String username;
    public UUID companyId;
    public Float age;            // null = not set
    public Integer minTicket;
    public Integer maxTicket;
    public Boolean allowLoneSeat;

    public AddEventPolicyRuleRequest() {}
}
