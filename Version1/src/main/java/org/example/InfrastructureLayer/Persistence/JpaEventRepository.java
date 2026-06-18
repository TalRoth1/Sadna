package org.example.InfrastructureLayer.Persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.Layout;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.StandingTicket;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.DiscountPolicy;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchaseComposite;
import org.example.DomainLayer.PolicyManagment.PurchasePolicy;
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
    private final SpringDataDiscountPolicyRepository discountPolicyJpa;
    private final SpringDataDiscountRuleRepository discountRuleJpa;
    private final SpringDataPurchasePolicyRepository purchasePolicyJpa;
    private final SpringDataRuleRepository ruleJpa;

    public JpaEventRepository(SpringDataEventRepository eventJpa,
                              SpringDataLayoutRepository layoutJpa,
                              SpringDataAreaRepository areaJpa,
                              SpringDataSeatRepository seatJpa,
                              SpringDataTicketRepository ticketJpa,
                              SpringDataDiscountPolicyRepository discountPolicyJpa,
                              SpringDataDiscountRuleRepository discountRuleJpa,
                              SpringDataPurchasePolicyRepository purchasePolicyJpa,
                              SpringDataRuleRepository ruleJpa) {
        this.eventJpa = eventJpa;
        this.layoutJpa = layoutJpa;
        this.areaJpa = areaJpa;
        this.seatJpa = seatJpa;
        this.ticketJpa = ticketJpa;
        this.discountPolicyJpa = discountPolicyJpa;
        this.discountRuleJpa = discountRuleJpa;
        this.purchasePolicyJpa = purchasePolicyJpa;
        this.ruleJpa = ruleJpa;
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

        // Persist the event's policies (reusing existing policy ids so the
        // events.*_policy_id FKs stay valid across edits).
        EventEntity existingEntity = eventJpa.findById(event.getEventId()).orElse(null);
        UUID discountPolicyId = saveDiscountPolicy(event,
                existingEntity == null ? null : existingEntity.getDiscountPolicyId());
        UUID purchasePolicyId = savePurchasePolicy(event,
                existingEntity == null ? null : existingEntity.getPurchasePolicyId());

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
                discountPolicyId,
                purchasePolicyId
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
        restoreDiscountPolicy(event, entity.getDiscountPolicyId());
        restorePurchasePolicy(event, entity.getPurchasePolicyId());

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
            // Assigned-seating tickets carry a row/seat: persist a Seat row and
            // link it via seat_id so the seat survives a reload (otherwise the
            // grid has no seats to render and the area looks empty).
            UUID seatId = null;
            if (ticket instanceof SittingTicket sittingTicket) {
                seatId = UUID.randomUUID();
                seatJpa.save(new SeatEntity(
                        seatId,
                        sittingTicket.getSeatRow(),
                        sittingTicket.getSeatNumber()));
            }

            ticketJpa.save(new TicketEntity(
                    ticket.getTicketId(),
                    event.getEventId(),
                    ticket.getAreaId(),
                    null,
                    seatId,
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
            Ticket ticket;

            // A linked seat means this was an assigned-seating ticket: rebuild it
            // as a SittingTicket carrying its row/seat so the seat map renders.
            SeatEntity seat = ticketEntity.getSeatId() == null
                    ? null
                    : seatJpa.findById(ticketEntity.getSeatId()).orElse(null);

            if (seat != null) {
                ticket = new SittingTicket(
                        ticketEntity.getId(),
                        event.getEventId(),
                        ticketEntity.getAreaId(),
                        (float) ticketEntity.getPrice(),
                        seat.getNumber(),
                        seat.getRow()
                );
            } else {
                ticket = new StandingTicket(
                        ticketEntity.getId(),
                        event.getEventId(),
                        ticketEntity.getAreaId(),
                        (float) ticketEntity.getPrice()
                );
            }

            restoreTicketStatus(ticket, ticketEntity.getStatus());

            event.addTicket(ticket);
        }
    }

    // -------------------------------------------------------------------------
    // Discount policy persistence (event-level). Mirrors JpaCompanyRepository so
    // an event's discounts survive a reload and reach the checkout flow.
    // -------------------------------------------------------------------------

    private UUID saveDiscountPolicy(Event event, UUID existingPolicyId) {
        UUID policyId = existingPolicyId != null ? existingPolicyId : UUID.randomUUID();
        discountPolicyJpa.save(new DiscountPolicyEntity(policyId));
        discountRuleJpa.deleteByPolicyId(policyId);

        DiscountPolicy policy = event.getDiscountPolicy();
        if (policy == null) {
            return policyId;
        }

        for (IDiscountRule rule : policy.getDiscountRules()) {
            discountRuleJpa.save(new DiscountRuleEntity(
                    UUID.randomUUID(),
                    policyId,
                    discountRuleType(rule),
                    discountFromDate(rule),
                    discountToDate(rule),
                    serializeDiscountRule(rule)
            ));
        }

        return policyId;
    }

    private void restoreDiscountPolicy(Event event, UUID policyId) {
        if (policyId == null) {
            return;
        }

        for (DiscountRuleEntity entity : discountRuleJpa.findByPolicyId(policyId)) {
            IDiscountRule rule = deserializeDiscountRule(entity);
            if (rule != null) {
                event.getDiscountPolicy().addRule(rule);
            }
        }
    }

    private String discountRuleType(IDiscountRule rule) {
        if (rule instanceof OvertDiscount) return "OVERT_DISCOUNT";
        if (rule instanceof ConditionalDiscount) return "CONDITIONAL_DISCOUNT";
        if (rule instanceof CouponCode) return "COUPON_CODE_DISCOUNT";
        return "UNKNOWN";
    }

    private Map<String, Object> serializeDiscountRule(IDiscountRule rule) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (rule instanceof OvertDiscount od) {
            params.put("discountPercent", od.getDiscountPercent());
        } else if (rule instanceof ConditionalDiscount cd) {
            params.put("discountPercent", cd.getDiscountPercent());
            params.put("requiredTickets", cd.getRequiredTickets());
            params.put("appliedTickets", cd.getAppliedTickets());
        } else if (rule instanceof CouponCode cc) {
            params.put("discountPercent", cc.getDiscountPercent());
            params.put("code", cc.getCode());
        }

        return params;
    }

    private LocalDateTime discountFromDate(IDiscountRule rule) {
        LocalDate date = null;
        if (rule instanceof OvertDiscount od) date = od.getFromDate();
        else if (rule instanceof ConditionalDiscount cd) date = cd.getFromDate();
        else if (rule instanceof CouponCode cc) date = cc.getFromDate();
        return date != null ? LocalDateTime.of(date, LocalTime.MIDNIGHT) : LocalDateTime.now();
    }

    private LocalDateTime discountToDate(IDiscountRule rule) {
        LocalDate date = null;
        if (rule instanceof OvertDiscount od) date = od.getToDate();
        else if (rule instanceof ConditionalDiscount cd) date = cd.getToDate();
        else if (rule instanceof CouponCode cc) date = cc.getToDate();
        return date != null ? LocalDateTime.of(date, LocalTime.MIDNIGHT) : LocalDateTime.now();
    }

    private IDiscountRule deserializeDiscountRule(DiscountRuleEntity entity) {
        Map<String, Object> params = entity.getParameters();
        LocalDate from = entity.getFromDate().toLocalDate();
        LocalDate to = entity.getToDate().toLocalDate();

        return switch (entity.getDiscountType()) {
            case "OVERT_DISCOUNT" -> new OvertDiscount(
                    ((Number) params.get("discountPercent")).floatValue(),
                    from,
                    to
            );
            case "CONDITIONAL_DISCOUNT" -> new ConditionalDiscount(
                    from,
                    to,
                    ((Number) params.get("discountPercent")).floatValue(),
                    ((Number) params.get("requiredTickets")).intValue(),
                    ((Number) params.get("appliedTickets")).intValue()
            );
            case "COUPON_CODE_DISCOUNT" -> new CouponCode(
                    from,
                    to,
                    ((Number) params.get("discountPercent")).floatValue(),
                    (String) params.get("code")
            );
            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Purchase policy persistence (event-level). Mirrors JpaCompanyRepository so
    // an event's purchase rules survive a reload and reach the checkout flow.
    // -------------------------------------------------------------------------

    private UUID savePurchasePolicy(Event event, UUID existingPolicyId) {
        UUID policyId = existingPolicyId != null ? existingPolicyId : UUID.randomUUID();
        purchasePolicyJpa.save(new PurchasePolicyEntity(policyId));
        ruleJpa.deleteByPolicyId(policyId);

        PurchasePolicy policy = event.getPurchasePolicy();
        IPurchaseRule root = policy == null ? null : policy.getRulesView();
        if (root != null) {
            ruleJpa.save(new RuleEntity(
                    UUID.randomUUID(),
                    policyId,
                    purchaseRuleType(root),
                    serializePurchaseRule(root)
            ));
        }

        return policyId;
    }

    private void restorePurchasePolicy(Event event, UUID policyId) {
        if (policyId == null) {
            return;
        }

        ruleJpa.findByPolicyId(policyId).ifPresent(entity -> {
            IPurchaseRule root = deserializePurchaseRule(entity.getRuleType(), entity.getParameters());
            if (root != null) {
                restorePurchaseRuleTree(event.getPurchasePolicy(), root);
            }
        });
    }

    private void restorePurchaseRuleTree(PurchasePolicy policy, IPurchaseRule rule) {
        if (rule instanceof PurchaseComposite pc) {
            restorePurchaseRuleTree(policy, pc.getLeftRule());
            restorePurchaseRuleTree(policy, pc.getRightRule());
        } else {
            policy.addRule(rule, true);
        }
    }

    private String purchaseRuleType(IPurchaseRule rule) {
        if (rule instanceof AgeRule) return "AGE_RULE";
        if (rule instanceof MinTicketRule) return "MIN_TICKET_RULE";
        if (rule instanceof MaxTicketRule) return "MAX_TICKET_RULE";
        if (rule instanceof LoneSeatRule) return "LONE_SEAT_RULE";
        if (rule instanceof PurchaseComposite) return "COMPOSITE";
        return "UNKNOWN";
    }

    private Map<String, Object> serializePurchaseRule(IPurchaseRule rule) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (rule instanceof AgeRule ar) {
            params.put("minAge", ar.getMinAge());
        } else if (rule instanceof MinTicketRule mr) {
            params.put("minTicket", mr.getMinTicket());
        } else if (rule instanceof MaxTicketRule mr) {
            params.put("maxTicket", mr.getMaxTicket());
        } else if (rule instanceof LoneSeatRule lr) {
            params.put("allowLoneSeat", lr.isAllowLoneSeat());
        } else if (rule instanceof PurchaseComposite pc) {
            params.put("operator", pc.isAnd());
            params.put("leftType", purchaseRuleType(pc.getLeftRule()));
            params.put("left", serializePurchaseRule(pc.getLeftRule()));
            params.put("rightType", purchaseRuleType(pc.getRightRule()));
            params.put("right", serializePurchaseRule(pc.getRightRule()));
        }

        return params;
    }

    @SuppressWarnings("unchecked")
    private IPurchaseRule deserializePurchaseRule(String type, Map<String, Object> params) {
        if (params == null) {
            return null;
        }

        return switch (type) {
            case "AGE_RULE" -> new AgeRule(((Number) params.get("minAge")).floatValue());
            case "MIN_TICKET_RULE" -> new MinTicketRule(((Number) params.get("minTicket")).intValue());
            case "MAX_TICKET_RULE" -> new MaxTicketRule(((Number) params.get("maxTicket")).intValue());
            case "LONE_SEAT_RULE" -> new LoneSeatRule((Boolean) params.get("allowLoneSeat"));
            case "COMPOSITE" -> {
                boolean operator = (Boolean) params.get("operator");
                String leftType = (String) params.get("leftType");
                String rightType = (String) params.get("rightType");
                Map<String, Object> left = (Map<String, Object>) params.get("left");
                Map<String, Object> right = (Map<String, Object>) params.get("right");
                IPurchaseRule leftRule = deserializePurchaseRule(leftType, left);
                IPurchaseRule rightRule = deserializePurchaseRule(rightType, right);
                if (leftRule == null || rightRule == null) yield null;
                yield new PurchaseComposite(leftRule, rightRule, operator);
            }
            default -> null;
        };
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
