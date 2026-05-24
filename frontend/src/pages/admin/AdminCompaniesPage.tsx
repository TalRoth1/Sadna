import { useEffect, useMemo, useState } from "react";
import {
    closeProductionCompany,
    getProductionCompanies,
} from "../../services/admin/adminCompaniesService";
import { getCurrentUser } from "../../services/currentUserService";
import type { ProductionCompany } from "../../types/admin";

function getErrorMessage(error: unknown): string {
    if (
        typeof error === "object" &&
        error !== null &&
        "response" in error
    ) {
        const axiosError = error as {
            response?: {
                data?: {
                    message?: string;
                };
            };
        };

        return axiosError.response?.data?.message ?? "Failed to close company.";
    }

    return "Failed to close company.";
}

export default function AdminCompaniesPage() {
    const [companies, setCompanies] = useState<ProductionCompany[]>([]);
    const [selectedCompanyId, setSelectedCompanyId] = useState("");
    const [statusMessage, setStatusMessage] = useState("");
    const [errorMessage, setErrorMessage] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const selectedCompany = useMemo(
        () =>
            companies.find((company) => company.id === selectedCompanyId) ??
            null,
        [companies, selectedCompanyId],
    );

    useEffect(() => {
        loadCompanies();
    }, []);

    async function loadCompanies() {
        setIsLoading(true);
        setStatusMessage("");
        setErrorMessage("");

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setCompanies([]);
                setSelectedCompanyId("");
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            const loadedCompanies = await getProductionCompanies(currentUser.id);

            setCompanies(loadedCompanies);
            setSelectedCompanyId(loadedCompanies[0]?.id ?? "");
        } catch {
            setCompanies([]);
            setSelectedCompanyId("");
            setErrorMessage("Failed to load production companies.");
        } finally {
            setIsLoading(false);
        }
    }

    async function handleCloseCompany() {
        setStatusMessage("");
        setErrorMessage("");

        if (!selectedCompanyId) {
            setErrorMessage("Select an active company first.");
            return;
        }

        setIsSubmitting(true);

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            await closeProductionCompany(currentUser.id, selectedCompanyId);

            const updatedCompanies = await getProductionCompanies(currentUser.id);

            setCompanies(updatedCompanies);
            setSelectedCompanyId(updatedCompanies[0]?.id ?? "");
            setStatusMessage("Company was closed successfully.");
        } catch (error) {
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="page-shell admin-page-shell">
            <section className="page-header">
                <h1>Close Production Company</h1>
                <p>Select an active production company and submit a close request.</p>
            </section>

            <section className="admin-form-card">
                {isLoading ? (
                    <p className="empty-state">Loading companies...</p>
                ) : companies.length === 0 ? (
                    <p className="empty-state">
                        There are no active production companies to close.
                    </p>
                ) : (
                    <>
                        <div className="admin-field-group">
                            <label
                                className="admin-field-label"
                                htmlFor="company-select"
                            >
                                Active company
                            </label>

                            <select
                                id="company-select"
                                className="admin-select"
                                value={selectedCompanyId}
                                onChange={(event) => {
                                    setSelectedCompanyId(event.target.value);
                                    setStatusMessage("");
                                    setErrorMessage("");
                                }}
                            >
                                {companies.map((company) => (
                                    <option key={company.id} value={company.id}>
                                        {company.name} - {company.status}
                                    </option>
                                ))}
                            </select>
                        </div>

                        {selectedCompany && (
                            <div className="admin-details-box">
                                <div className="admin-detail-row">
                                    <span>Company</span>
                                    <strong>{selectedCompany.name}</strong>
                                </div>

                                <div className="admin-detail-row">
                                    <span>Status</span>
                                    <strong>{selectedCompany.status}</strong>
                                </div>

                                <div className="admin-detail-row">
                                    <span>Owners</span>
                                    <strong>{selectedCompany.ownersCount}</strong>
                                </div>

                                <div className="admin-detail-row">
                                    <span>Managers</span>
                                    <strong>{selectedCompany.managersCount}</strong>
                                </div>
                            </div>
                        )}

                        <button
                            type="button"
                            className="admin-primary-button"
                            onClick={handleCloseCompany}
                            disabled={isSubmitting}
                        >
                            {isSubmitting ? "Closing..." : "Close Company"}
                        </button>
                    </>
                )}

                {statusMessage && (
                    <div className="success-message">{statusMessage}</div>
                )}

                {errorMessage && (
                    <div className="error-message">{errorMessage}</div>
                )}
            </section>
        </main>
    );
}