package org.example.ApplicationLayer.dto.PurchaseDTOs;

public class LotteryStatusDTO {
    public boolean lotteryExists;
    public boolean winnersDrawn;
    public boolean isWinner;
    public boolean isRegistered;

    public LotteryStatusDTO() {}

    public LotteryStatusDTO(boolean lotteryExists, boolean winnersDrawn, boolean isWinner, boolean isRegistered) {
        this.lotteryExists = lotteryExists;
        this.winnersDrawn = winnersDrawn;
        this.isWinner = isWinner;
        this.isRegistered = isRegistered;
    }
}
