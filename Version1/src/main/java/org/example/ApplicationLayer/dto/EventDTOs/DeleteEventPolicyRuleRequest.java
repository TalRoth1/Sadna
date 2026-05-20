package org.example.ApplicationLayer.dto.EventDTOs;

import java.util.UUID;

public class DeleteEventPolicyRuleRequest {
    public String username;
    public UUID companyId;
    public boolean age;
    public boolean minTicket;
    public boolean maxTicket;
    public boolean allowLoneSeat;

    public DeleteEventPolicyRuleRequest() {}
}
