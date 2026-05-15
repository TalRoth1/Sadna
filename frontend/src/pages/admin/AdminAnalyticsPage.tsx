import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import { getSystemAnalytics } from "../../services/admin/adminAnalyticsService";
import type { SystemAnalytics } from "../../types/admin";

export default function AdminAnalyticsPage() {
    const [analytics, setAnalytics] = useState<SystemAnalytics | null>(null);

    useEffect(() => {
        async function loadAnalytics() {
            const currentUser = await getCurrentUser();
            const result = await getSystemAnalytics(currentUser.id);

            setAnalytics(result);
        }

        loadAnalytics();
    }, []);

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>System Analytics</h1>
                <p>Live and historical system behavior and performance metrics.</p>
            </section>

            {analytics && (
                <section className="analytics-grid">
                    <div>
                        <span>Active visitors</span>
                        <strong>{analytics.activeVisitors}</strong>
                    </div>

                    <div>
                        <span>New subscribers rate</span>
                        <strong>{analytics.newSubscribersRate}/min</strong>
                    </div>

                    <div>
                        <span>Ticket reservation rate</span>
                        <strong>{analytics.ticketReservationRate}/min</strong>
                    </div>

                    <div>
                        <span>Ticket purchase rate</span>
                        <strong>{analytics.ticketPurchaseRate}/min</strong>
                    </div>

                    <div>
                        <span>Active queues</span>
                        <strong>{analytics.activeQueues}</strong>
                    </div>
                </section>
            )}
        </main>
    );
}