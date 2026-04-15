package org.example.DomainLayer.EventAggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event aggregate root: 1:1 {@link Layout}, {@link PurchasePolicy}, {@link DiscountPolicy}; 1:* {@link Ticket}.
 * Optional {@code lotteryId} links to a lottery mechanism elsewhere in the system.
 */
public class Event {

    private final String eventId;
    private final String companyId;
    private LocalDateTime date;
    private String location;
    private final List<String> tags = new ArrayList<>();
    private EventStatus status;
    private String artist;
    private String type;
    private final Layout layout;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;
    private double rating;
    private String lotteryId;
    private final Map<String, Ticket> ticketsById = new LinkedHashMap<>();

    public Event(String eventId, String companyId, LocalDateTime date, String location,
                   String artist, String type, EventStatus status, double rating) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("companyId must not be null");
        }
        this.eventId = eventId;
        this.companyId = companyId;
        setDate(date);
        setLocation(location);
        setArtist(artist);
        setType(type);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        if (rating < 0) {
            throw new IllegalArgumentException("rating must be non-negative");
        }
        this.rating = rating;
        this.layout = new Layout();
        this.purchasePolicy = new PurchasePolicy();
        this.discountPolicy = new DiscountPolicy();
    }

    public String getEventId() {
        return eventId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location required");
        }
        this.location = location.trim();
    }

    public List<String> getTagsView() {
        return List.copyOf(tags);
    }

    public void setTags(List<String> tags) {
        this.tags.clear();
        if (tags != null) {
            for (String t : tags) {
                if (t != null && !t.isBlank()) {
                    this.tags.add(t.trim());
                }
            }
        }
    }

    public void addTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag required");
        }
        tags.add(tag.trim());
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("artist required");
        }
        this.artist = artist.trim();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type required");
        }
        this.type = type.trim();
    }

    public Layout getLayout() {
        return layout;
    }

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        if (rating < 0) {
            throw new IllegalArgumentException("rating must be non-negative");
        }
        this.rating = rating;
    }

    public String getLotteryId() {
        return lotteryId;
    }

    public void setLotteryId(String lotteryId) {
        this.lotteryId = lotteryId;
    }

    public Map<String, Ticket> getTicketsView() {
        return Map.copyOf(ticketsById);
    }

    /**
     * Registers a ticket on the event and links its id to the given layout area (Area 1:* Ticket by id).
     */
    public void addTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket must not be null");
        }
        if (!eventId.equals(ticket.getEventId())) {
            throw new IllegalArgumentException("ticket event mismatch");
        }
        String tid = ticket.getTicketId();
        if (ticketsById.containsKey(tid)) {
            throw new IllegalStateException("duplicate ticket id: " + tid);
        }
        layout.requireArea(ticket.getAreaId()).linkTicketId(tid);
        ticketsById.put(tid, ticket);
    }

    public Ticket getTicket(String ticketId) {
        if (ticketId == null) {
            throw new IllegalArgumentException("ticketId must not be null");
        }
        return ticketsById.get(ticketId);
    }
}
