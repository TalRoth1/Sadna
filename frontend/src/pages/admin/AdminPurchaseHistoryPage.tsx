import { useEffect, useMemo, useState } from "react";
import {
    getFilteredPurchaseHistory,
    getGlobalPurchaseHistory,
} from "../../services/admin/adminPurchasesService";
import { getCurrentUser } from "../../services/currentUserService";
import type {
    AdminPurchaseFilterType,
    GlobalPurchaseRecord,
} from "../../types/admin";

function formatDate(value?: string): string {
    if (!value) {
        return "N/A";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return date.toLocaleString();
}

function formatPrice(value: number): string {
    return `${value.toLocaleString()} NIS`;
}

export default function AdminPurchaseHistoryPage() {
    const [purchases, setPurchases] = useState<GlobalPurchaseRecord[]>([]);
    const [filterType, setFilterType] = useState<AdminPurchaseFilterType>("all");
    const [filterId, setFilterId] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isFiltering, setIsFiltering] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    const totalRevenue = useMemo(
        () => purchases.reduce((sum, purchase) => sum + purchase.totalPrice, 0),
        [purchases],
    );

    const totalTickets = useMemo(
        () => purchases.reduce((sum, purchase) => sum + purchase.ticketsAmount, 0),
        [purchases],
    );

    useEffect(() => {
        loadAllPurchases();
    }, []);

    async function loadAllPurchases() {
        setIsLoading(true);
        setErrorMessage("");

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setPurchases([]);
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            const loadedPurchases = await getGlobalPurchaseHistory(currentUser.id);
            setPurchases(loadedPurchases);
            setFilterType("all");
            setFilterId("");
        } catch {
            setPurchases([]);
            setErrorMessage("Failed to load purchase history.");
        } finally {
            setIsLoading(false);
        }
    }

    function isValidUuid(value: string): boolean {
        return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
            value.trim(),
        );
    }

    async function handleApplyFilter() {
        if (filterType !== "all" && !filterId.trim()) {
            setPurchases([]);
            setErrorMessage("");
            return;
        }

        if (filterType !== "all" && !isValidUuid(filterId)) {
            setPurchases([]);
            setErrorMessage("");
            return;
        }
        setIsFiltering(true);
        setErrorMessage("");

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setPurchases([]);
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            if (filterType !== "all" && !filterId.trim()) {
                setErrorMessage("Filter ID is required.");
                return;
            }

            const filteredPurchases = await getFilteredPurchaseHistory(
                currentUser.id,
                filterType,
                filterId,
            );

            setPurchases(filteredPurchases);
        } catch {
            setPurchases([]);
            setErrorMessage("Failed to apply purchase history filter.");
        } finally {
            setIsFiltering(false);
        }
    }

    return (
        <main className="page-shell admin-page-shell">
            <section className="page-header">
                <h1>Global Purchase History</h1>
                <p>System-wide purchase history by buyers, companies, and events.</p>
            </section>

            <section className="admin-history-toolbar">
                <div className="admin-history-filter-row">
                    <div className="admin-field-group">
                        <label className="admin-field-label" htmlFor="purchase-filter-type">
                            Filter by
                        </label>

                        <select
                            id="purchase-filter-type"
                            className="admin-select"
                            value={filterType}
                            onChange={(event) => {
                                const nextFilterType = event.target.value as AdminPurchaseFilterType;
                                setFilterType(nextFilterType);
                                setErrorMessage("");

                                if (nextFilterType === "all") {
                                    setFilterId("");
                                }
                            }}
                        >
                            <option value="all">All purchases</option>
                            <option value="user">User ID</option>
                            <option value="event">Event ID</option>
                            <option value="company">Company ID</option>
                        </select>
                    </div>

                    <div className="admin-field-group">
                        <label className="admin-field-label" htmlFor="purchase-filter-id">
                            Filter ID
                        </label>

                        <input
                            id="purchase-filter-id"
                            className="admin-input"
                            value={filterId}
                            onChange={(event) => setFilterId(event.target.value)}
                            placeholder={
                                filterType === "all"
                                    ? "Not required for all purchases"
                                    : "Paste UUID here"
                            }
                            disabled={filterType === "all"}
                        />
                    </div>

                    <div className="admin-history-actions">
                        <button
                            type="button"
                            className="admin-primary-button"
                            onClick={handleApplyFilter}
                            disabled={isFiltering}
                        >
                            {isFiltering ? "Filtering..." : "Apply Filter"}
                        </button>

                        <button
                            type="button"
                            className="admin-secondary-button"
                            onClick={loadAllPurchases}
                            disabled={isLoading}
                        >
                            Reset
                        </button>
                    </div>
                </div>

                <div className="admin-history-summary">
                    <div>
                        <span>Records</span>
                        <strong>{purchases.length}</strong>
                    </div>

                    <div>
                        <span>Tickets</span>
                        <strong>{totalTickets}</strong>
                    </div>

                    <div>
                        <span>Total revenue</span>
                        <strong>{formatPrice(totalRevenue)}</strong>
                    </div>
                </div>
            </section>

            {errorMessage && (
                <div className="error-message admin-wide-message">
                    {errorMessage}
                </div>
            )}

            {isLoading ? (
                <section className="admin-form-card">
                    <p className="empty-state">Loading purchase history...</p>
                </section>
            ) : purchases.length === 0 ? (
                <section className="admin-form-card">
                    <p className="empty-state">No purchase history records found.</p>
                </section>
            ) : (
                <section className="admin-history-table-card">
                    <div className="admin-history-table">
                        <div className="admin-history-row admin-history-header-row">
                            <span>Buyer</span>
                            <span>Company</span>
                            <span>Event</span>
                            <span>Tickets</span>
                            <span>Payment</span>
                            <span>Total</span>
                            <span>Purchased</span>
                        </div>

                        {purchases.map((purchase) => (
                            <div key={purchase.id} className="admin-history-row">
                                <span title={purchase.buyerId}>
                                    {purchase.buyerName}
                                </span>

                                <span title={purchase.companyId}>
                                    {purchase.companyName}
                                </span>

                                <span>
                                    <strong>{purchase.eventName}</strong>
                                    <small>{purchase.eventLocation || "Unknown location"}</small>
                                    <small>{formatDate(purchase.eventDate)}</small>
                                </span>

                                <span>{purchase.ticketsAmount}</span>

                                <span>{purchase.paymentInfo || "N/A"}</span>

                                <span>{formatPrice(purchase.totalPrice)}</span>

                                <span>{formatDate(purchase.purchaseDate)}</span>
                            </div>
                        ))}
                    </div>
                </section>
            )}
        </main>
    );
}