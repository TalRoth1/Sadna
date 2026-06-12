package org.example.InfrastructureLayer.Persistence;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.Layout;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("localdb")
@Transactional
public class JpaEventRepository implements IEventRepository {

    private final SpringDataEventRepository eventJpa;
    private final SpringDataLayoutRepository layoutJpa;
    private final SpringDataAreaRepository areaJpa;
    private final SpringDataSeatRepository seatJpa;
    private final SpringDataTicketRepository ticketJpa;

    public JpaEventRepository(SpringDataEventRepository eventJpa,
                              SpringDataLayoutRepository layoutJpa,
                              SpringDataAreaRepository areaJpa,
                              SpringDataSeatRepository seatJpa,
                              SpringDataTicketRepository ticketJpa) {
        this.eventJpa = eventJpa;
        this.layoutJpa = layoutJpa;
        this.areaJpa = areaJpa;
        this.seatJpa = seatJpa;
        this.ticketJpa = ticketJpa;
    }

    @Override
    public Event getById(UUID eventId) {
        if (eventId == null) {
            return null;
        }

        return eventJpa.findById(eventId)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> getAll() {
        return eventJpa.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        UUID layoutId = UUID.randomUUID();
        saveLayout(event, layoutId);

        UUID lotteryUuid = null;
        if (event.getLotteryId() != null && !event.getLotteryId().isBlank()) {
            lotteryUuid = UUID.fromString(event.getLotteryId());
        }

        EventEntity entity = new EventEntity(
                event.getEventId(),
                event.getName() == null ? "" : event.getName(),
                event.getCompanyId(),
            event.getManagerUsername() == null ? "" : event.getManagerUsername(),
                event.getLocation() == null ? "" : event.getLocation(),
                event.getDescription(),
                event.getTagsView(),
                event.getArtist() == null ? "" : event.getArtist(),
                event.getType() == null ? "" : event.getType(),
                event.getDate(),
                event.getRating(),
                event.getStatus(),
                layoutId,
            lotteryUuid,
                null,
                null
        );

        eventJpa.save(entity);
        saveTickets(event);
    }

    @Override
    public void delete(UUID eventId) {
        if (eventId == null) {
            return;
        }
        eventJpa.deleteById(eventId);
    }

    private Event toDomain(EventEntity entity) {
        Event event = new Event(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDate(),
                entity.getLocation(),
                entity.getArtist(),
                entity.getType(),
                entity.getStatus() == null ? EventStatus.ACTIVE : entity.getStatus(),
                DiscountType.ALL
        );

        if (entity.getName() != null) {
            event.setName(entity.getName());
        }
        if (entity.getManagerUsername() != null) {
            event.setManagerUsername(entity.getManagerUsername());
        }
        if (entity.getDescription() != null) {
            event.setDescription(entity.getDescription());
        }
        if (entity.getTags() != null) {
            event.setTags(entity.getTags());
        }
        if (entity.getRating() != null) {
            event.setRating(entity.getRating());
        }
        if (entity.getLotteryId() != null) {
            event.setLotteryId(entity.getLotteryId().toString());
        }

        restoreLayout(event, entity.getLayoutId());
        restoreTickets(event);

        return event;
    }

    private void saveLayout(Event event, UUID layoutId) {
        Layout layout = event.getLayout();
        layoutJpa.save(new LayoutEntity(layoutId, layout.getMapImage()));

        for (Area area : layout.getAreasView()) {
            areaJpa.save(new AreaEntity(area.getAreaId(), layoutId, area instanceof SittingArea ? "SEATING" : "STANDING", area.getPrice()));
        }
    }

    private void saveTickets(Event event) {
        for (Ticket ticket : event.getTicketsView().values()) {
            ticketJpa.save(new TicketEntity(
                    ticket.getTicketId(),
                    event.getEventId(),
                    ticket.getAreaId(),
                    null,
                    null,
                    ticket.getStatus(),
                    ticket.getPrice(),
                    null,
                    null
            ));
        }
    }

    private void restoreLayout(Event event, UUID layoutId) {
        if (layoutId == null) {
            return;
        }

        Layout layout = event.getLayout();
        List<AreaEntity> areas = areaJpa.findByLayoutId(layoutId);
        for (AreaEntity areaEntity : areas) {
            Area area = areaEntity.getType().equals("SEATING")
                    ? new SittingArea(areaEntity.getId(), areaEntity.getPrice())
                    : new StandingArea(areaEntity.getId(), areaEntity.getPrice());
            layout.addArea(area);
        }
    }

    private void restoreTickets(Event event) {
        List<TicketEntity> tickets = ticketJpa.findByEventId(event.getEventId());

        for (TicketEntity ticketEntity : tickets) {
            Ticket ticket = new org.example.DomainLayer.EventAggregate.StandingTicket(
                    ticketEntity.getId(),
                    event.getEventId(),
                    ticketEntity.getAreaId(),
                    (float) ticketEntity.getPrice()
            );

            restoreTicketStatus(ticket, ticketEntity.getStatus());

            event.addTicket(ticket);
        }
    }

    private void restoreTicketStatus(Ticket ticket, TicketStatus status) {
        if (status == null || status == TicketStatus.AVAILABLE) {
            return;
        }

        if (status == TicketStatus.RESERVED) {
            ticket.reserve();
            return;
        }

        if (status == TicketStatus.SOLD) {
            ticket.reserve();
            ticket.markSold();
        }
    }
}
