import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import {
    closeProductionCompany,
    getProductionCompanies,
} from "../../services/admin/adminCompaniesService";
import type { ProductionCompany } from "../../types/admin";

export default function AdminCompaniesPage() {
    const [companies, setCompanies] = useState<ProductionCompany[]>([]);
    const [selectedCompanyId, setSelectedCompanyId] = useState("");
    const [adminUserId, setAdminUserId] = useState("");
    const [message, setMessage] = useState("");

    useEffect(() => {
        async function loadCompanies() {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                return;
            }

            const result = await getProductionCompanies(currentUser.id);

            setAdminUserId(currentUser.id);
            setCompanies(result);
            setSelectedCompanyId(result[0]?.id ?? "");
        }

        loadCompanies();
    }, []);

    async function handleCloseCompany() {
        if (!selectedCompanyId) {
            return;
        }

        await closeProductionCompany(adminUserId, selectedCompanyId);
        setMessage("Close company request was submitted.");
    }

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>Close Production Company</h1>
                <p>Select a production company and submit a close request.</p>
            </section>

            <section className="admin-panel">
                <select
                    value={selectedCompanyId}
                    onChange={(event) => setSelectedCompanyId(event.target.value)}
                >
                    {companies.map((company) => (
                        <option key={company.id} value={company.id}>
                            {company.name} - {company.status}
                        </option>
                    ))}
                </select>

                <button type="button" onClick={handleCloseCompany}>
                    Close Company
                </button>

                {message && <p className="success-message">{message}</p>}
            </section>
        </main>
    );
}