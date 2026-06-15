package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.PolicyManagment.*;
import org.example.DomainLayer.Rating;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile("localdb")
@Transactional
public class JpaCompanyRepository implements ICompanyRepository {

    private final SpringDataCompanyRepository companyJpa;
    private final SpringDataRatingRepository ratingJpa;
    private final SpringDataPurchasePolicyRepository purchasePolicyJpa;
    private final SpringDataDiscountPolicyRepository discountPolicyJpa;
    private final SpringDataRuleRepository ruleJpa;
    private final SpringDataDiscountRuleRepository discountRuleJpa;

    public JpaCompanyRepository(SpringDataCompanyRepository companyJpa,
                                SpringDataRatingRepository ratingJpa,
                                SpringDataPurchasePolicyRepository purchasePolicyJpa,
                                SpringDataDiscountPolicyRepository discountPolicyJpa,
                                SpringDataRuleRepository ruleJpa,
                                SpringDataDiscountRuleRepository discountRuleJpa) {
        this.companyJpa = companyJpa;
        this.ratingJpa = ratingJpa;
        this.purchasePolicyJpa = purchasePolicyJpa;
        this.discountPolicyJpa = discountPolicyJpa;
        this.ruleJpa = ruleJpa;
        this.discountRuleJpa = discountRuleJpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createCompany(String founderEmail, String companyName, DiscountType discountType) {
        UUID purchasePolicyId = UUID.randomUUID();
        UUID discountPolicyId = UUID.randomUUID();

        purchasePolicyJpa.saveAndFlush(new PurchasePolicyEntity(purchasePolicyId));
        discountPolicyJpa.saveAndFlush(new DiscountPolicyEntity(discountPolicyId));

        UUID companyId = UUID.randomUUID();
        String founderUsername = normalizeIdentifier(founderEmail);

        companyJpa.saveAndFlush(new CompanyEntity(
                companyId,
                companyName,
                founderUsername,
                Company.CompanyStatus.ACTIVE,
                discountPolicyId,
                purchasePolicyId
        ));

        return companyId;
    }

    @Override
    public void save(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company is required");
        }

        String founderUsername = normalizeIdentifier(company.getFounderEmail());

        Optional<CompanyEntity> existing = companyJpa.findById(company.getId());

        UUID purchasePolicyId;
        UUID discountPolicyId;

        if (existing.isPresent()) {
            // Reuse the existing policy IDs so FK constraints remain valid
            purchasePolicyId = existing.get().getPurchasePolicyId();
            discountPolicyId = existing.get().getDiscountPolicyId();

            // Create policy records if they were somehow missing
            if (purchasePolicyId == null) {
                purchasePolicyId = UUID.randomUUID();
                purchasePolicyJpa.save(new PurchasePolicyEntity(purchasePolicyId));
            }
            if (discountPolicyId == null) {
                discountPolicyId = UUID.randomUUID();
                discountPolicyJpa.save(new DiscountPolicyEntity(discountPolicyId));
            }

            companyJpa.save(new CompanyEntity(
                    company.getId(),
                    company.getName(),
                    founderUsername,
                    company.getStatus(),
                    existing.get().getCreatedAt(),
                    discountPolicyId,
                    purchasePolicyId
            ));
        } else {
            purchasePolicyId = UUID.randomUUID();
            discountPolicyId = UUID.randomUUID();

            purchasePolicyJpa.save(new PurchasePolicyEntity(purchasePolicyId));
            discountPolicyJpa.save(new DiscountPolicyEntity(discountPolicyId));

            companyJpa.save(new CompanyEntity(
                    company.getId(),
                    company.getName(),
                    founderUsername,
                    company.getStatus(),
                    discountPolicyId,
                    purchasePolicyId
            ));
        }

        saveRatings(company);
        savePurchasePolicy(company.getPurchasePolicy(), purchasePolicyId);
        saveDiscountPolicy(company.getDiscountPolicy(), discountPolicyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findByID(UUID companyId) {
        if (companyId == null) {
            return Optional.empty();
        }

        return companyJpa.findById(companyId)
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> getAll() {
        return companyJpa.findAll()
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> getAllActive() {
        return companyJpa.findByStatus(Company.CompanyStatus.ACTIVE)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Entity → Domain
    // -------------------------------------------------------------------------

    private Company toDomain(CompanyEntity entity) {
        Map<UUID, Rating> ratingsByUsers = restoreRatings(entity.getId());
        double rating = computeAvgRating(ratingsByUsers);
        int amountRated = ratingsByUsers.size();

        PurchasePolicy purchasePolicy = restorePurchasePolicy(entity.getPurchasePolicyId());
        DiscountPolicy discountPolicy = restoreDiscountPolicy(entity.getDiscountPolicyId());

        return new Company(
                entity.getId(),
                entity.getFounderUsername(),
                entity.getFounderUsername(),
                entity.getName(),
                entity.getStatus(),
                rating,
                amountRated,
                ratingsByUsers,
                new ArrayList<>(),   // eventIds are owned by the events table
                discountPolicy,
                purchasePolicy
        );
    }

    // -------------------------------------------------------------------------
    // Ratings
    // -------------------------------------------------------------------------

    private void saveRatings(Company company) {
        ratingJpa.deleteByCompanyId(company.getId());

        for (Map.Entry<UUID, Rating> entry : company.getRatingsByUsers().entrySet()) {
            UUID userId = entry.getKey();
            Rating r = entry.getValue();

            ratingJpa.save(new RatingEntity(
                    UUID.randomUUID(),
                    userId,
                    null,
                    company.getId(),
                    r.getRating(),
                    null
            ));
        }
    }

    private Map<UUID, Rating> restoreRatings(UUID companyId) {
        Map<UUID, Rating> result = new HashMap<>();

        for (RatingEntity entity : ratingJpa.findByCompanyId(companyId)) {
            result.put(entity.getUserId(), new Rating(entity.getRating(), entity.getUserId()));
        }

        return result;
    }

    private double computeAvgRating(Map<UUID, Rating> ratingsByUsers) {
        if (ratingsByUsers.isEmpty()) {
            return 0.0;
        }

        double sum = ratingsByUsers.values().stream()
                .mapToInt(Rating::getRating)
                .sum();

        return sum / ratingsByUsers.size();
    }

    // -------------------------------------------------------------------------
    // Purchase policy persistence
    // -------------------------------------------------------------------------

    private void savePurchasePolicy(PurchasePolicy policy, UUID policyId) {
        ruleJpa.deleteByPolicyId(policyId);

        IPurchaseRule root = policy.getRulesView();

        if (root == null) {
            return;
        }

        String rootType = purchaseRuleType(root);
        Map<String, Object> params = serializePurchaseRule(root);

        ruleJpa.save(new RuleEntity(UUID.randomUUID(), policyId, rootType, params));
    }

    private PurchasePolicy restorePurchasePolicy(UUID policyId) {
        PurchasePolicy policy = new PurchasePolicy();

        if (policyId == null) {
            return policy;
        }

        ruleJpa.findByPolicyId(policyId).ifPresent(entity -> {
            IPurchaseRule root = deserializePurchaseRule(entity.getRuleType(), entity.getParameters());

            if (root != null) {
                restorePurchaseRuleTree(policy, root);
            }
        });

        return policy;
    }

    /**
     * Walks the rule tree and re-adds each leaf to the policy using the same
     * left-to-right AND chain used when the composite was originally built.
     * Composite nodes encode their operator in {@code parameters["operator"]}.
     */
    private void restorePurchaseRuleTree(PurchasePolicy policy, IPurchaseRule rule) {
        if (rule instanceof PurchaseComposite pc) {
            restorePurchaseRuleTree(policy, pc.getLeftRule());
            restorePurchaseRuleTree(policy, pc.getRightRule());
        } else {
            // For leaf rules we use AND (true) as a safe default when re-adding;
            // the original operator is preserved in the composite structure above.
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

    // -------------------------------------------------------------------------
    // Discount policy persistence
    // -------------------------------------------------------------------------

    private void saveDiscountPolicy(DiscountPolicy policy, UUID policyId) {
        discountRuleJpa.deleteByPolicyId(policyId);

        for (IDiscountRule rule : policy.getDiscountRules()) {
            String type = discountRuleType(rule);
            Map<String, Object> params = serializeDiscountRule(rule);
            LocalDateTime fromDate = discountFromDate(rule);
            LocalDateTime toDate = discountToDate(rule);

            discountRuleJpa.save(new DiscountRuleEntity(
                    UUID.randomUUID(),
                    policyId,
                    type,
                    fromDate,
                    toDate,
                    params
            ));
        }
    }

    private DiscountPolicy restoreDiscountPolicy(UUID policyId) {
        DiscountPolicy policy = new DiscountPolicy(DiscountType.ALL); // default type, will be overridden when rules are added

        if (policyId == null) {
            return policy;
        }

        for (DiscountRuleEntity entity : discountRuleJpa.findByPolicyId(policyId)) {
            IDiscountRule rule = deserializeDiscountRule(entity);

            if (rule != null) {
                policy.addRule(rule);
            }
        }

        return policy;
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
    // Helpers
    // -------------------------------------------------------------------------

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }
}