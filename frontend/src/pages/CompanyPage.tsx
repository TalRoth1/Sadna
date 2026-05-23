type CompanyPageProps = {
    companyId: string;
    companyName: string;
    onBackToCompanies: () => void;
};

export default function CompanyPage({
    companyId,
    companyName,
    onBackToCompanies,
}: CompanyPageProps) {
    return (
        <main className="app-page">
            <section className="page-header">
                <h1>{companyName}</h1>
                <p>Company ID: {companyId}</p>
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