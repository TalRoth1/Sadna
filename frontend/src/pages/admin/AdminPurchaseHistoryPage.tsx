import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import { getGlobalPurchaseHistory } from "../../services/admin/adminPurchasesService";
import type { GlobalPurchaseRecord } from "../../types/admin";

export default function AdminPurchaseHistoryPage() {
    const [purchases, setPurchases] = useState<GlobalPurchaseRecord[]>([]);

    useEffect(() => {
        async function loadPurchases() {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                return;
            }

            const result = await getGlobalPurchaseHistory(currentUser.id);

            setPurchases(result);
        }

        loadPurchases();
    }, []);

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>Global Purchase History</h1>
                <p>System-wide purchase history by buyers, companies, and events.</p>
            </section>

            <section className="admin-table">
                <div className="admin-table-header">
                    <span>Buyer</span>
                    <span>Company</span>
                    <span>Event</span>
                    <span>Tickets</span>
                    <span>Total</span>
                </div>

                {purchases.map((purchase) => (
                    <div key={purchase.id} className="admin-table-row">
                        <span>{purchase.buyerName}</span>
                        <span>{purchase.companyName}</span>
                        <span>{purchase.eventName}</span>
                        <span>{purchase.ticketsAmount}</span>
                        <span>{purchase.totalPrice} NIS</span>
                    </div>
                ))}
            </section>
        </main>
    );
}