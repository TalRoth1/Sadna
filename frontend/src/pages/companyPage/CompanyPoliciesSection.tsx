import "./CompanyPoliciesSection.css";

type PurchaseRuleViewModel =
    | {
          id: string;
          kind: "AGE";
          minAge: number;
      }
    | {
          id: string;
          kind: "MIN_TICKETS";
          minTickets: number;
      }
    | {
          id: string;
          kind: "MAX_TICKETS";
          maxTickets: number;
      }
    | {
          id: string;
          kind: "LONE_SEAT";
          allowLoneSeat: boolean;
      }
    | {
          id: string;
          kind: "COMPOSITE";
          operator: "AND" | "OR";
          left: PurchaseRuleViewModel;
          right: PurchaseRuleViewModel;
      };

type DiscountRuleViewModel =
    | {
          id: string;
          kind: "OVERT";
          percent: number;
          fromDate: string;
          toDate: string;
      }
    | {
          id: string;
          kind: "CONDITIONAL";
          percent: number;
          fromDate: string;
          toDate: string;
          requiredTickets: number;
          appliedTickets: number;
      }
    | {
          id: string;
          kind: "COUPON";
          percent: number;
          fromDate: string;
          toDate: string;
          code: string;
      };

type CompanyPoliciesSectionProps = {
    purchaseRules?: PurchaseRuleViewModel[];
    discountRules?: DiscountRuleViewModel[];
    onRemovePurchaseRule?: (ruleId: string, ruleLabel: string) => void;
    onRemoveDiscountRule?: (ruleId: string, ruleLabel: string) => void;
};

const SUPPORTED_PURCHASE_RULES = [
    {
        title: "Age rule",
        description: "Requires the buyer to be at least a minimum age.",
    },
    {
        title: "Minimum tickets",
        description: "Requires a purchase to include at least a certain ticket count.",
    },
    {
        title: "Maximum tickets",
        description: "Caps how many tickets can be purchased together.",
    },
    {
        title: "Lone seat protection",
        description: "Allows or blocks purchases that would leave a single isolated seat.",
    },
    {
        title: "Composite rules",
        description: "Purchase rules can be combined recursively with AND / OR groups.",
    },
];

const SUPPORTED_DISCOUNT_RULES = [
    {
        title: "Overt discount",
        description: "A company-wide percentage discount within a date range.",
    },
    {
        title: "Conditional discount",
        description: "Applies a percentage discount to a limited number of tickets when conditions match.",
    },
    {
        title: "Coupon code",
        description: "Applies a percentage discount only when the correct coupon is used.",
    },
];

function formatDateRange(fromDate: string, toDate: string) {
    const formatPart = (value: string) => {
        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) {
            return value;
        }

        return parsed.toLocaleDateString("en-US", {
            dateStyle: "medium",
        });
    };

    return `${formatPart(fromDate)} - ${formatPart(toDate)}`;
}

function formatPercent(percent: number) {
    return Number.isInteger(percent) ? `${percent}%` : `${percent.toFixed(2)}%`;
}

function PurchaseRuleCard({ rule }: { rule: PurchaseRuleViewModel }) {
    if (rule.kind === "COMPOSITE") {
        return (
            <div className="company-policy-rule-group">
                <div className="company-policy-rule-card company-policy-rule-card--composite">
                    <div className="company-policy-rule-card-header">
                        <div>
                            <span className="company-policy-rule-kind">Composite rule</span>
                            <h4>{rule.operator} group</h4>
                        </div>
                        <span className="company-policy-rule-badge">{rule.operator}</span>
                    </div>
                    <p className="company-policy-rule-note">
                        This group combines two rules into a single purchase requirement.
                    </p>
                </div>
                <div className="company-policy-rule-children">
                    <PurchaseRuleCard rule={rule.left} />
                    <PurchaseRuleCard rule={rule.right} />
                </div>
            </div>
        );
    }

    return (
        <article className="company-policy-rule-card">
            <div className="company-policy-rule-card-header">
                <div>
                    <span className="company-policy-rule-kind">{getPurchaseRuleKindLabel(rule.kind)}</span>
                    <h4>{getPurchaseRuleTitle(rule)}</h4>
                </div>
                <div className="company-policy-rule-actions">
                    <span className="company-policy-rule-id">ID {rule.id.slice(0, 8)}</span>
                </div>
            </div>
            <p className="company-policy-rule-note">{getPurchaseRuleDescription(rule)}</p>
        </article>
    );
}

function DiscountRuleCard({ rule }: { rule: DiscountRuleViewModel }) {
    return (
        <article className="company-policy-rule-card company-policy-rule-card--discount">
            <div className="company-policy-rule-card-header">
                <div>
                    <span className="company-policy-rule-kind">{getDiscountRuleKindLabel(rule.kind)}</span>
                    <h4>{getDiscountRuleTitle(rule)}</h4>
                </div>
                <div className="company-policy-rule-actions">
                    <span className="company-policy-rule-id">ID {rule.id.slice(0, 8)}</span>
                </div>
            </div>
            <p className="company-policy-rule-note">{getDiscountRuleDescription(rule)}</p>
        </article>
    );
}

function getPurchaseRuleKindLabel(kind: PurchaseRuleViewModel["kind"]) {
    switch (kind) {
        case "AGE":
            return "Age";
        case "MIN_TICKETS":
            return "Minimum tickets";
        case "MAX_TICKETS":
            return "Maximum tickets";
        case "LONE_SEAT":
            return "Lone seat";
        case "COMPOSITE":
            return "Composite";
    }
}

function getPurchaseRuleTitle(rule: Exclude<PurchaseRuleViewModel, { kind: "COMPOSITE" }>) {
    switch (rule.kind) {
        case "AGE":
            return `Buyer age must be at least ${rule.minAge}`;
        case "MIN_TICKETS":
            return `At least ${rule.minTickets} tickets`;
        case "MAX_TICKETS":
            return `At most ${rule.maxTickets} tickets`;
        case "LONE_SEAT":
            return rule.allowLoneSeat ? "Lone seats are allowed" : "Lone seats are blocked";
    }
}

function getPurchaseRuleDescription(rule: Exclude<PurchaseRuleViewModel, { kind: "COMPOSITE" }>) {
    switch (rule.kind) {
        case "AGE":
            return "The buyer must meet or exceed the configured minimum age.";
        case "MIN_TICKETS":
            return "The purchase must include at least this many tickets.";
        case "MAX_TICKETS":
            return "The purchase cannot exceed this number of tickets.";
        case "LONE_SEAT":
            return rule.allowLoneSeat
                ? "The policy allows purchases that leave a single empty seat behind."
                : "The policy blocks purchases that would create an isolated lone seat.";
    }
}

function getDiscountRuleKindLabel(kind: DiscountRuleViewModel["kind"]) {
    switch (kind) {
        case "OVERT":
            return "Overt discount";
        case "CONDITIONAL":
            return "Conditional discount";
        case "COUPON":
            return "Coupon code";
    }
}

function getDiscountRuleTitle(rule: DiscountRuleViewModel) {
    switch (rule.kind) {
        case "OVERT":
            return `${formatPercent(rule.percent)} off`;
        case "CONDITIONAL":
            return `${formatPercent(rule.percent)} off after ${rule.requiredTickets} tickets`;
        case "COUPON":
            return `${formatPercent(rule.percent)} off with coupon ${rule.code}`;
    }
}

function getDiscountRuleDescription(rule: DiscountRuleViewModel) {
    const range = formatDateRange(rule.fromDate, rule.toDate);

    switch (rule.kind) {
        case "OVERT":
            return `Applies automatically during ${range}.`;
        case "CONDITIONAL":
            return `Applies to ${rule.appliedTickets} ticket(s) when the purchase includes at least ${rule.requiredTickets}. Active during ${range}.`;
        case "COUPON":
            return `Only applies when the buyer enters coupon code ${rule.code}. Active during ${range}.`;
    }
}

function renderRuleLegend(
    rules: Array<{ title: string; description: string }>,
    kindLabel: string,
) {
    return (
        <div className="company-policy-legend">
            <span className="company-policy-legend-title">Supported {kindLabel}</span>
            <div className="company-policy-legend-grid">
                {rules.map((rule) => (
                    <article key={rule.title} className="company-policy-legend-item">
                        <strong>{rule.title}</strong>
                        <p>{rule.description}</p>
                    </article>
                ))}
            </div>
        </div>
    );
}

export default function CompanyPoliciesSection({
    purchaseRules = [],
    discountRules = [],
    onRemovePurchaseRule,
    onRemoveDiscountRule,
}: CompanyPoliciesSectionProps) {
    return (
        <section id="company-policies" className="company-policies-card">
            <div className="company-policies-header">
                <div>
                    <h2>Company policies</h2>
                    <p>
                        Purchase policy controls who can buy tickets. Discount policy controls how the
                        company can reduce prices.
                    </p>
                </div>

                <span className="company-policies-badge">UI preview</span>
            </div>

            <div className="company-policies-grid">
                <article className="company-policy-panel">
                    <div className="company-policy-panel-header">
                        <div>
                            <span className="company-policy-panel-label">Purchase policy</span>
                            <h3>Buying restrictions</h3>
                        </div>
                        <span className="company-policy-panel-count">
                            {purchaseRules.length > 0 ? `${purchaseRules.length} rule(s)` : "Empty"}
                        </span>
                    </div>

                    {purchaseRules.length > 0 ? (
                        <div className="company-policy-rules-list">
                            {purchaseRules.map((rule) => (
                                <article key={rule.id} className="company-policy-rule-card company-policy-rule-card--purchase">
                                    <div className="company-policy-rule-card-header">
                                        <div>
                                            <span className="company-policy-rule-kind">
                                                {getPurchaseRuleKindLabel(rule.kind)}
                                            </span>
                                            <h4>{getPurchaseRuleTitle(rule)}</h4>
                                        </div>

                                        <div className="company-policy-rule-actions">
                                            <span className="company-policy-rule-id">
                                                ID {rule.id.slice(0, 8)}
                                            </span>
                                            {onRemovePurchaseRule && rule.kind !== "COMPOSITE" && (
                                                <button
                                                    type="button"
                                                    className="company-policy-remove-button"
                                                    onClick={() => onRemovePurchaseRule(rule.id, getPurchaseRuleTitle(rule))}
                                                >
                                                    Remove policy
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                    <p className="company-policy-rule-note">
                                        {getPurchaseRuleDescription(rule)}
                                    </p>
                                </article>
                            ))}
                        </div>
                    ) : (
                        <>
                            <p className="company-policy-empty-state">
                                No purchase policy rules have been loaded yet.
                            </p>
                            {renderRuleLegend(SUPPORTED_PURCHASE_RULES, "purchase rules")}
                        </>
                    )}
                </article>

                <article className="company-policy-panel">
                    <div className="company-policy-panel-header">
                        <div>
                            <span className="company-policy-panel-label">Discount policy</span>
                            <h3>Price reduction rules</h3>
                        </div>
                        <span className="company-policy-panel-count">
                            {discountRules.length > 0 ? `${discountRules.length} rule(s)` : "Empty"}
                        </span>
                    </div>

                    {discountRules.length > 0 ? (
                        <div className="company-policy-rules-list">
                            {discountRules.map((rule) => (
                                <article key={rule.id} className="company-policy-rule-card company-policy-rule-card--discount">
                                    <div className="company-policy-rule-card-header">
                                        <div>
                                            <span className="company-policy-rule-kind">
                                                {getDiscountRuleKindLabel(rule.kind)}
                                            </span>
                                            <h4>{getDiscountRuleTitle(rule)}</h4>
                                        </div>

                                        <div className="company-policy-rule-actions">
                                            <span className="company-policy-rule-id">
                                                ID {rule.id.slice(0, 8)}
                                            </span>
                                            {onRemoveDiscountRule && (
                                                <button
                                                    type="button"
                                                    className="company-policy-remove-button"
                                                    onClick={() => onRemoveDiscountRule(rule.id, getDiscountRuleTitle(rule))}
                                                >
                                                    Remove policy
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                    <p className="company-policy-rule-note">
                                        {getDiscountRuleDescription(rule)}
                                    </p>
                                </article>
                            ))}
                        </div>
                    ) : (
                        <>
                            <p className="company-policy-empty-state">
                                No discount policy rules have been loaded yet.
                            </p>
                            {renderRuleLegend(SUPPORTED_DISCOUNT_RULES, "discount rules")}
                        </>
                    )}
                </article>
            </div>
        </section>
    );
}
