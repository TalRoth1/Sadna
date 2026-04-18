package org.example.DomainLayer.EventAggregate;

public class Event {
    private final int eventId;
    private final int companyId;
    private final int lotteryId;

    public Event(int eventId, int companyId, int lotteryId) {
        this.eventId = eventId;
        this.companyId = companyId;
        this.lotteryId = lotteryId;
    }

    public int getEventId() {
        return eventId;
    }

    public int getCompanyId() {
        return companyId;
    }

    public int getLotteryId() {
        return lotteryId;
    }
}
