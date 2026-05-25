package org.example.ApplicationLayer.dto.EventDTOs;

import java.util.UUID;

/**
 * For replacing the event purchase policy.
 *
 * Null fields mean that this rule should not be created.
 * If all policy fields are null, the current policy will be cleared.
 */
public class EditEventPolicyRequest {
    public String username;
    public UUID companyId;

    public Float age;
    public Integer minTicket;
    public Integer maxTicket;
    public Boolean allowLoneSeat;

    public EditEventPolicyRequest() {}
}