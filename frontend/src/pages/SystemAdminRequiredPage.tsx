export default function SystemAdminRequiredPage() {
    return (
        <main style={{
            minHeight: "100vh",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "32px",
            background: "#f8fafc"
        }}>
            <section style={{
                maxWidth: "620px",
                width: "100%",
                background: "white",
                borderRadius: "16px",
                padding: "32px",
                boxShadow: "0 12px 30px rgba(15, 23, 42, 0.12)",
                textAlign: "center"
            }}>
                <h1 style={{ marginBottom: "16px", color: "#0f172a" }}>
                    System Admin Not Configured
                </h1>

                <p style={{ fontSize: "18px", lineHeight: 1.7, color: "#334155" }}>
                    The system cannot be opened because no System Admin is configured.
                </p>

                <p style={{ fontSize: "16px", lineHeight: 1.7, color: "#475569" }}>
                    Please define a System Admin in the database, then restart the backend server.
                </p>
            </section>
        </main>
    );
}