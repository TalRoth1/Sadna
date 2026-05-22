import { useEffect, useState } from "react";
import { getPurchaseHistory } from "../services/purchaseHistoryService";
import type { PurchaseHistoryItem } from "../types/purchase";
import { getCurrentUser } from "../services/currentUserService";

function formatEventDate(date: string) {
    const eventDate = new Date(date);

    if (Number.isNaN(eventDate.getTime())) {
        return "Invalid date";
    }

    return eventDate.toLocaleString("he-IL", {
        dateStyle: "short",
        timeStyle: "short",
    });
}

function isUpcomingEvent(eventDate: string) {
    const parsedDate = new Date(eventDate);

    if (Number.isNaN(parsedDate.getTime())) {
        return false;
    }

    return parsedDate >= new Date();
}

function PurchaseCard({ purchase }: { purchase: PurchaseHistoryItem }) {
    return (
        <article className="purchase-card">
            <div>
                <h2>{purchase.eventName}</h2>
                <p>Event date: {formatEventDate(purchase.eventDate)}</p>
                <p>Location: {purchase.eventLocation}</p>
                <p>Purchased at: {formatEventDate(purchase.purchaseDate)}</p>
                <p>Payment: {purchase.paymentInfo}</p>
            </div>

            <div className="purchase-details">
                <span>Tickets: {purchase.ticketsAmount}</span>
                <span>Total: {purchase.totalPrice} NIS</span>
            </div>
        </article>
    );
}
export default function PurchaseHistoryPage() {
    const [purchases, setPurchases] = useState<PurchaseHistoryItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadPurchaseHistory() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const currentUser = await getCurrentUser();

                if (!currentUser) {
                    setErrorMessage("Please log in to view your purchase history.");
                    setPurchases([]);
                    return;
                }

                const purchaseHistory = await getPurchaseHistory(currentUser.id);
                setPurchases(purchaseHistory);
            } catch {
                setErrorMessage("Failed to load purchase history.");
            } finally {
                setIsLoading(false);
            }
        }

        loadPurchaseHistory();
    }, []);

    const upcomingPurchases = purchases.filter((purchase) =>
        isUpcomingEvent(purchase.eventDate),
    );

    const pastPurchases = purchases.filter(
        (purchase) => !isUpcomingEvent(purchase.eventDate),
    );

    return (
        <main className="purchase-history-page">
            <section className="page-header">
                <h1>Purchase History</h1>
                <p>All tickets purchased by the current user.</p>
            </section>

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading purchases...</h2>
                    <p>Please wait while we load your purchase history.</p>
                </section>
            )}

            {!isLoading && errorMessage && (
                <section className="empty-state">
                    <h2>Something went wrong</h2>
                    <p>{errorMessage}</p>
                </section>
            )}

            {!isLoading && !errorMessage && purchases.length === 0 && (
                <section className="empty-state">
                    <h2>No tickets found</h2>
                    <p>You have not purchased any tickets yet.</p>
                </section>
            )}

            {!isLoading && !errorMessage && purchases.length > 0 && (
                <div className="history-content">
                    <section className="history-section">
                        <h2>Upcoming Events</h2>

                        {upcomingPurchases.length === 0 ? (
                            <p className="section-empty-text">No upcoming purchases.</p>
                        ) : (
                            <div className="purchase-list">
                                {upcomingPurchases.map((purchase) => (
                                    <PurchaseCard key={purchase.id} purchase={purchase} />
                                ))}
                            </div>
                        )}
                    </section>

                    <section className="history-section">
                        <h2>Past Events</h2>

                        {pastPurchases.length === 0 ? (
                            <p className="section-empty-text">No past purchases.</p>
                        ) : (
                            <div className="purchase-list">
                                {pastPurchases.map((purchase) => (
                                    <PurchaseCard key={purchase.id} purchase={purchase} />
                                ))}
                            </div>
                        )}
                    </section>
                </div>
            )}
        </main>
    );
}