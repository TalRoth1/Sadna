import { useState, type FormEvent } from "react";

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
    onCreatePurchaseRule?: (request: AddPurchasePolicyRequest) => Promise<void>;
    onCreateDiscountRule?: (request: AddDiscountPolicyRequest) => Promise<void>;
};

type AddPurchasePolicyRequest =
    | {
          kind: "AGE";
          age: number;
      }
    | {
          kind: "MIN_TICKETS";
          minTicket: number;
      }
    | {
          kind: "MAX_TICKETS";
          maxTicket: number;
      }
    | {
          kind: "LONE_SEAT";
          allowLoneSeat: boolean;
      };

type AddDiscountPolicyRequest =
    | {
          kind: "OVERT";
          fromDate: string;
          toDate: string;
          discountPercent: number;
      }
    | {
          kind: "CONDITIONAL";
          fromDate: string;
          toDate: string;
          discountPercent: number;
          requiredTickets: number;
          appliedTickets: number;
      }
    | {
          kind: "COUPON";
          fromDate: string;
          toDate: string;
          discountPercent: number;
          code: string;
      };

type PolicyFamily = "PURCHASE" | "DISCOUNT";

type PurchaseDraftState = {
    kind: PurchaseRuleViewModel["kind"];
    minAge: string;
    minTickets: string;
    maxTickets: string;
    allowLoneSeat: "true" | "false";
    operator: "AND" | "OR";
    leftRuleLabel: string;
    rightRuleLabel: string;
};

type DiscountDraftState = {
    kind: DiscountRuleViewModel["kind"];
    percent: string;
    fromDate: string;
    toDate: string;
    requiredTickets: string;
    appliedTickets: string;
    code: string;
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

function getPurchaseRuleTitle(rule: PurchaseRuleViewModel) {
    switch (rule.kind) {
        case "AGE":
            return `Buyer age must be at least ${rule.minAge}`;
        case "MIN_TICKETS":
            return `At least ${rule.minTickets} tickets`;
        case "MAX_TICKETS":
            return `At most ${rule.maxTickets} tickets`;
        case "LONE_SEAT":
            return rule.allowLoneSeat ? "Lone seats are allowed" : "Lone seats are blocked";
        case "COMPOSITE":
            return `${rule.operator} group`;
    }
}

function getPurchaseRuleDescription(rule: PurchaseRuleViewModel) {
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
        case "COMPOSITE":
            return "This group combines two rules into a single purchase requirement.";
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

function getPurchaseDraftDetails(draft: PurchaseDraftState) {
    switch (draft.kind) {
        case "AGE":
            return [
                { label: "Rule family", value: "Purchase policy" },
                { label: "Rule type", value: "Age rule" },
                { label: "Minimum age", value: draft.minAge || "Not set" },
            ];
        case "MIN_TICKETS":
            return [
                { label: "Rule family", value: "Purchase policy" },
                { label: "Rule type", value: "Minimum tickets" },
                { label: "Ticket floor", value: draft.minTickets || "Not set" },
            ];
        case "MAX_TICKETS":
            return [
                { label: "Rule family", value: "Purchase policy" },
                { label: "Rule type", value: "Maximum tickets" },
                { label: "Ticket ceiling", value: draft.maxTickets || "Not set" },
            ];
        case "LONE_SEAT":
            return [
                { label: "Rule family", value: "Purchase policy" },
                { label: "Rule type", value: "Lone seat protection" },
                { label: "Seat behavior", value: draft.allowLoneSeat === "true" ? "Allowed" : "Blocked" },
            ];
        case "COMPOSITE":
            return [
                { label: "Rule family", value: "Purchase policy" },
                { label: "Rule type", value: "Composite rule" },
                { label: "Operator", value: draft.operator },
                { label: "Left rule", value: draft.leftRuleLabel || "Choose or describe a rule" },
                { label: "Right rule", value: draft.rightRuleLabel || "Choose or describe a rule" },
            ];
    }
}

function getDiscountDraftDetails(draft: DiscountDraftState) {
    switch (draft.kind) {
        case "OVERT":
            return [
                { label: "Rule family", value: "Discount policy" },
                { label: "Rule type", value: "Overt discount" },
                { label: "Percent", value: draft.percent || "Not set" },
                { label: "Date range", value: `${draft.fromDate || "Start date"} - ${draft.toDate || "End date"}` },
            ];
        case "CONDITIONAL":
            return [
                { label: "Rule family", value: "Discount policy" },
                { label: "Rule type", value: "Conditional discount" },
                { label: "Percent", value: draft.percent || "Not set" },
                { label: "Required tickets", value: draft.requiredTickets || "Not set" },
                { label: "Applied tickets", value: draft.appliedTickets || "Not set" },
            ];
        case "COUPON":
            return [
                { label: "Rule family", value: "Discount policy" },
                { label: "Rule type", value: "Coupon code" },
                { label: "Percent", value: draft.percent || "Not set" },
                { label: "Coupon code", value: draft.code || "Not set" },
            ];
    }
}

export default function CompanyPoliciesSection({
    purchaseRules = [],
    discountRules = [],
    onRemovePurchaseRule,
    onRemoveDiscountRule,
    onCreatePurchaseRule,
    onCreateDiscountRule,
}: CompanyPoliciesSectionProps) {
    const [isAddPolicyDialogOpen, setIsAddPolicyDialogOpen] = useState(false);
    const [policyFamily, setPolicyFamily] = useState<PolicyFamily>("PURCHASE");
    const [isSubmittingPolicy, setIsSubmittingPolicy] = useState(false);
    const [purchaseDraft, setPurchaseDraft] = useState<PurchaseDraftState>({
        kind: "AGE",
        minAge: "",
        minTickets: "",
        maxTickets: "",
        allowLoneSeat: "true",
        operator: "AND",
        leftRuleLabel: "",
        rightRuleLabel: "",
    });
    const [discountDraft, setDiscountDraft] = useState<DiscountDraftState>({
        kind: "OVERT",
        percent: "",
        fromDate: "",
        toDate: "",
        requiredTickets: "",
        appliedTickets: "",
        code: "",
    });

    function openAddPolicyDialog() {
        setIsAddPolicyDialogOpen(true);
    }

    function closeAddPolicyDialog() {
        setIsAddPolicyDialogOpen(false);
    }

    async function handleAddPolicySubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (isSubmittingPolicy) {
            return;
        }

        if (policyFamily === "PURCHASE") {
            if (purchaseDraft.kind === "COMPOSITE") {
                window.alert("Composite purchase rules are not connected to the server yet.");
                return;
            }

            const purchaseRequest = buildPurchaseRequest(purchaseDraft);
            if (!purchaseRequest) {
                window.alert("Please fill in the required purchase rule fields.");
                return;
            }

            if (!onCreatePurchaseRule) {
                window.alert("Purchase policy creation is not available yet.");
                return;
            }

            try {
                setIsSubmittingPolicy(true);
                await onCreatePurchaseRule(purchaseRequest);
                closeAddPolicyDialog();
            } catch (error) {
                window.alert(error instanceof Error ? error.message : "Failed to create the purchase rule.");
            } finally {
                setIsSubmittingPolicy(false);
            }
            return;
        }

        const discountRequest = buildDiscountRequest(discountDraft);
        if (!discountRequest) {
            window.alert("Please fill in the required discount rule fields.");
            return;
        }

        if (!onCreateDiscountRule) {
            window.alert("Discount policy creation is not available yet.");
            return;
        }

        try {
            setIsSubmittingPolicy(true);
            await onCreateDiscountRule(discountRequest);
            closeAddPolicyDialog();
        } catch (error) {
            window.alert(error instanceof Error ? error.message : "Failed to create the discount rule.");
        } finally {
            setIsSubmittingPolicy(false);
        }
    }

    function buildPurchaseRequest(draft: PurchaseDraftState): AddPurchasePolicyRequest | null {
        switch (draft.kind) {
            case "AGE": {
                const age = Number(draft.minAge);
                return Number.isFinite(age) ? { kind: "AGE", age } : null;
            }
            case "MIN_TICKETS": {
                const minTicket = Number(draft.minTickets);
                return Number.isFinite(minTicket) ? { kind: "MIN_TICKETS", minTicket } : null;
            }
            case "MAX_TICKETS": {
                const maxTicket = Number(draft.maxTickets);
                return Number.isFinite(maxTicket) ? { kind: "MAX_TICKETS", maxTicket } : null;
            }
            case "LONE_SEAT":
                return { kind: "LONE_SEAT", allowLoneSeat: draft.allowLoneSeat === "true" };
            case "COMPOSITE":
                return null;
        }
    }

    function buildDiscountRequest(draft: DiscountDraftState): AddDiscountPolicyRequest | null {
        const discountPercent = Number(draft.percent);
        const hasDateRange = draft.fromDate.trim().length > 0 && draft.toDate.trim().length > 0;
        if (!Number.isFinite(discountPercent) || !hasDateRange) {
            return null;
        }

        switch (draft.kind) {
            case "OVERT":
                return {
                    kind: "OVERT",
                    fromDate: draft.fromDate,
                    toDate: draft.toDate,
                    discountPercent,
                };
            case "CONDITIONAL": {
                const requiredTickets = Number(draft.requiredTickets);
                const appliedTickets = Number(draft.appliedTickets);
                if (!Number.isFinite(requiredTickets) || !Number.isFinite(appliedTickets)) {
                    return null;
                }

                return {
                    kind: "CONDITIONAL",
                    fromDate: draft.fromDate,
                    toDate: draft.toDate,
                    discountPercent,
                    requiredTickets,
                    appliedTickets,
                };
            }
            case "COUPON":
                if (draft.code.trim().length === 0) {
                    return null;
                }

                return {
                    kind: "COUPON",
                    fromDate: draft.fromDate,
                    toDate: draft.toDate,
                    discountPercent,
                    code: draft.code.trim(),
                };
        }
    }

    const activeDraftDetails =
        policyFamily === "PURCHASE" ? getPurchaseDraftDetails(purchaseDraft) : getDiscountDraftDetails(discountDraft);

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

                <div className="company-policies-header-actions">
                    <span className="company-policies-badge">UI preview</span>
                    <button type="button" className="company-policy-add-button" onClick={openAddPolicyDialog}>
                        Add policy
                    </button>
                </div>
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

            {isAddPolicyDialogOpen && (
                <div className="company-policy-modal-overlay" role="presentation" onClick={closeAddPolicyDialog}>
                    <div
                        className="company-policy-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="company-policy-modal-title"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div className="company-policy-modal-header">
                            <div>
                                <span className="company-policy-modal-kicker">Add policy</span>
                                <h3 id="company-policy-modal-title">Build a new policy draft</h3>
                                <p>
                                    Choose whether you are creating a purchase policy or a discount policy, then
                                    fill in the rule form.
                                </p>
                            </div>

                            <button
                                type="button"
                                className="company-policy-modal-close-button"
                                onClick={closeAddPolicyDialog}
                            >
                                Close
                            </button>
                        </div>

                        <form className="company-policy-modal-body" onSubmit={handleAddPolicySubmit}>
                            <div className="company-policy-family-switch" aria-label="Policy family">
                                <button
                                    type="button"
                                    className={
                                        policyFamily === "PURCHASE"
                                            ? "company-policy-family-option company-policy-family-option--active"
                                            : "company-policy-family-option"
                                    }
                                    onClick={() => setPolicyFamily("PURCHASE")}
                                >
                                    Purchase policy
                                </button>
                                <button
                                    type="button"
                                    className={
                                        policyFamily === "DISCOUNT"
                                            ? "company-policy-family-option company-policy-family-option--active"
                                            : "company-policy-family-option"
                                    }
                                    onClick={() => setPolicyFamily("DISCOUNT")}
                                >
                                    Discount policy
                                </button>
                            </div>

                            <div className="company-policy-modal-grid">
                                <div className="company-policy-modal-form-panel">
                                    <label className="company-policy-field">
                                        <span>Rule type</span>
                                        {policyFamily === "PURCHASE" ? (
                                            <select
                                                value={purchaseDraft.kind}
                                                onChange={(event) =>
                                                    setPurchaseDraft((draft) => ({
                                                        ...draft,
                                                        kind: event.target.value as PurchaseDraftState["kind"],
                                                    }))
                                                }
                                            >
                                                <option value="AGE">Age rule</option>
                                                <option value="MIN_TICKETS">Minimum tickets</option>
                                                <option value="MAX_TICKETS">Maximum tickets</option>
                                                <option value="LONE_SEAT">Lone seat protection</option>
                                                <option value="COMPOSITE">Composite rule</option>
                                            </select>
                                        ) : (
                                            <select
                                                value={discountDraft.kind}
                                                onChange={(event) =>
                                                    setDiscountDraft((draft) => ({
                                                        ...draft,
                                                        kind: event.target.value as DiscountDraftState["kind"],
                                                    }))
                                                }
                                            >
                                                <option value="OVERT">Overt discount</option>
                                                <option value="CONDITIONAL">Conditional discount</option>
                                                <option value="COUPON">Coupon code</option>
                                            </select>
                                        )}
                                    </label>

                                    {policyFamily === "PURCHASE" ? (
                                        <div className="company-policy-form-stack">
                                            <p className="company-policy-field-help">
                                                This form is only a UI draft for now. We will wire the save action to
                                                the backend next.
                                            </p>

                                            {purchaseDraft.kind === "AGE" && (
                                                <label className="company-policy-field">
                                                    <span>Minimum age</span>
                                                    <input
                                                        type="number"
                                                        min="0"
                                                        value={purchaseDraft.minAge}
                                                        onChange={(event) =>
                                                            setPurchaseDraft((draft) => ({
                                                                ...draft,
                                                                minAge: event.target.value,
                                                            }))
                                                        }
                                                        placeholder="18"
                                                    />
                                                </label>
                                            )}

                                            {purchaseDraft.kind === "MIN_TICKETS" && (
                                                <label className="company-policy-field">
                                                    <span>Minimum ticket count</span>
                                                    <input
                                                        type="number"
                                                        min="0"
                                                        value={purchaseDraft.minTickets}
                                                        onChange={(event) =>
                                                            setPurchaseDraft((draft) => ({
                                                                ...draft,
                                                                minTickets: event.target.value,
                                                            }))
                                                        }
                                                        placeholder="2"
                                                    />
                                                </label>
                                            )}

                                            {purchaseDraft.kind === "MAX_TICKETS" && (
                                                <label className="company-policy-field">
                                                    <span>Maximum ticket count</span>
                                                    <input
                                                        type="number"
                                                        min="0"
                                                        value={purchaseDraft.maxTickets}
                                                        onChange={(event) =>
                                                            setPurchaseDraft((draft) => ({
                                                                ...draft,
                                                                maxTickets: event.target.value,
                                                            }))
                                                        }
                                                        placeholder="8"
                                                    />
                                                </label>
                                            )}

                                            {purchaseDraft.kind === "LONE_SEAT" && (
                                                <label className="company-policy-field">
                                                    <span>Lone seat behavior</span>
                                                    <select
                                                        value={purchaseDraft.allowLoneSeat}
                                                        onChange={(event) =>
                                                            setPurchaseDraft((draft) => ({
                                                                ...draft,
                                                                allowLoneSeat: event.target.value as "true" | "false",
                                                            }))
                                                        }
                                                    >
                                                        <option value="true">Allow lone seats</option>
                                                        <option value="false">Block lone seats</option>
                                                    </select>
                                                </label>
                                            )}

                                            {purchaseDraft.kind === "COMPOSITE" && (
                                                <div className="company-policy-split-fields">
                                                    <label className="company-policy-field">
                                                        <span>Operator</span>
                                                        <select
                                                            value={purchaseDraft.operator}
                                                            onChange={(event) =>
                                                                setPurchaseDraft((draft) => ({
                                                                    ...draft,
                                                                    operator: event.target.value as "AND" | "OR",
                                                                }))
                                                            }
                                                        >
                                                            <option value="AND">AND</option>
                                                            <option value="OR">OR</option>
                                                        </select>
                                                    </label>

                                                    <label className="company-policy-field">
                                                        <span>Left rule label</span>
                                                        <input
                                                            type="text"
                                                            value={purchaseDraft.leftRuleLabel}
                                                            onChange={(event) =>
                                                                setPurchaseDraft((draft) => ({
                                                                    ...draft,
                                                                    leftRuleLabel: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="Age rule"
                                                        />
                                                    </label>

                                                    <label className="company-policy-field">
                                                        <span>Right rule label</span>
                                                        <input
                                                            type="text"
                                                            value={purchaseDraft.rightRuleLabel}
                                                            onChange={(event) =>
                                                                setPurchaseDraft((draft) => ({
                                                                    ...draft,
                                                                    rightRuleLabel: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="Minimum tickets"
                                                        />
                                                    </label>
                                                </div>
                                            )}
                                        </div>
                                    ) : (
                                        <div className="company-policy-form-stack">
                                            <p className="company-policy-field-help">
                                                This is a UI mock. We will connect the actual discount save flow next.
                                            </p>

                                            {discountDraft.kind === "OVERT" && (
                                                <div className="company-policy-split-fields">
                                                    <label className="company-policy-field">
                                                        <span>Percent</span>
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            step="0.01"
                                                            value={discountDraft.percent}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    percent: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="12"
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>From date</span>
                                                        <input
                                                            type="date"
                                                            value={discountDraft.fromDate}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    fromDate: event.target.value,
                                                                }))
                                                            }
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>To date</span>
                                                        <input
                                                            type="date"
                                                            value={discountDraft.toDate}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    toDate: event.target.value,
                                                                }))
                                                            }
                                                        />
                                                    </label>
                                                </div>
                                            )}

                                            {discountDraft.kind === "CONDITIONAL" && (
                                                <div className="company-policy-split-fields">
                                                    <label className="company-policy-field">
                                                        <span>Percent</span>
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            step="0.01"
                                                            value={discountDraft.percent}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    percent: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="18"
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>Required tickets</span>
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            value={discountDraft.requiredTickets}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    requiredTickets: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="4"
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>Applied tickets</span>
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            value={discountDraft.appliedTickets}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    appliedTickets: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="2"
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>From date</span>
                                                        <input
                                                            type="date"
                                                            value={discountDraft.fromDate}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    fromDate: event.target.value,
                                                                }))
                                                            }
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>To date</span>
                                                        <input
                                                            type="date"
                                                            value={discountDraft.toDate}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    toDate: event.target.value,
                                                                }))
                                                            }
                                                        />
                                                    </label>
                                                </div>
                                            )}

                                            {discountDraft.kind === "COUPON" && (
                                                <div className="company-policy-split-fields">
                                                    <label className="company-policy-field">
                                                        <span>Percent</span>
                                                        <input
                                                            type="number"
                                                            min="0"
                                                            step="0.01"
                                                            value={discountDraft.percent}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    percent: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="25"
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>Coupon code</span>
                                                        <input
                                                            type="text"
                                                            value={discountDraft.code}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    code: event.target.value,
                                                                }))
                                                            }
                                                            placeholder="MEGA25"
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>From date</span>
                                                        <input
                                                            type="date"
                                                            value={discountDraft.fromDate}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    fromDate: event.target.value,
                                                                }))
                                                            }
                                                        />
                                                    </label>
                                                    <label className="company-policy-field">
                                                        <span>To date</span>
                                                        <input
                                                            type="date"
                                                            value={discountDraft.toDate}
                                                            onChange={(event) =>
                                                                setDiscountDraft((draft) => ({
                                                                    ...draft,
                                                                    toDate: event.target.value,
                                                                }))
                                                            }
                                                        />
                                                    </label>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>

                                <aside className="company-policy-modal-preview">
                                    <span className="company-policy-modal-preview-label">Draft preview</span>
                                    <div className="company-policy-modal-preview-card">
                                        <strong>
                                            {policyFamily === "PURCHASE"
                                                ? getPurchaseRuleKindLabel(purchaseDraft.kind)
                                                : getDiscountRuleKindLabel(discountDraft.kind)}
                                        </strong>
                                        <p>
                                            {policyFamily === "PURCHASE"
                                                ? "Purchase policy changes are usually built from one leaf rule or a composite grouping."
                                                : "Discount policy changes can model an overt discount, a conditional discount, or a coupon code."}
                                        </p>
                                    </div>

                                    <div className="company-policy-preview-list">
                                        {activeDraftDetails.map((item) => (
                                            <div key={item.label} className="company-policy-preview-item">
                                                <span>{item.label}</span>
                                                <strong>{item.value}</strong>
                                            </div>
                                        ))}
                                    </div>

                                    <p className="company-policy-field-help">
                                        This dialog is UI-only for now. The save action will be wired to the API in the
                                        next step.
                                    </p>
                                </aside>
                            </div>

                            <div className="company-policy-modal-footer">
                                <button type="button" className="company-policy-secondary-button" onClick={closeAddPolicyDialog}>
                                    Cancel
                                </button>
                                <button type="submit" className="company-policy-primary-button" disabled={isSubmittingPolicy}>
                                    {isSubmittingPolicy ? "Saving..." : "Create policy"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </section>
    );
}
