package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDateTime;

public class CreateLotteryRequest {
    public LocalDateTime registrationOpen;
    public LocalDateTime registrationClose;

    public CreateLotteryRequest() {}
}
