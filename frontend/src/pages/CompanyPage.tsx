import type { CompanyResponse } from "../services/companyService";

type CompanyPageProps = {
    company: CompanyResponse;
    onBackToCompanies: () => void;
};

export default function CompanyPage({
    company,
    onBackToCompanies,
}: CompanyPageProps) {
    return (
        <main className="app-page">
            <section className="page-header">
                <h1>{company.name}</h1>
                <p>Company ID: {company.id}</p>
            </section>

            <section className="empty-state">
                <h2>Company page</h2>
                <p>This dedicated company page is ready for the next backend step.</p>

                <button type="button" className="event-filters-reset" onClick={onBackToCompanies}>
                    Back to My Companies
                </button>
            </section>
        </main>
    );
}