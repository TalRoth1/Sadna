import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import {
    getComplaints,
    respondToComplaint,
    sendSystemMessage,
} from "../../services/admin/adminComplaintsService";
import type { Complaint } from "../../types/admin";

export default function AdminComplaintsPage() {
    const [complaints, setComplaints] = useState<Complaint[]>([]);
    const [selectedComplaintId, setSelectedComplaintId] = useState("");
    const [adminUserId, setAdminUserId] = useState("");
    const [response, setResponse] = useState("");
    const [systemMessage, setSystemMessage] = useState("");
    const [message, setMessage] = useState("");

    useEffect(() => {
        async function loadComplaints() {
            const currentUser = await getCurrentUser();
            const result = await getComplaints(currentUser.id);

            setAdminUserId(currentUser.id);
            setComplaints(result);
            setSelectedComplaintId(result[0]?.id ?? "");
        }

        loadComplaints();
    }, []);

    async function handleRespondToComplaint() {
        if (!selectedComplaintId || !response.trim()) {
            return;
        }

        await respondToComplaint(adminUserId, selectedComplaintId, response);
        setResponse("");
        setMessage("Complaint response was submitted.");
    }

    async function handleSendSystemMessage() {
        if (!systemMessage.trim()) {
            return;
        }

        await sendSystemMessage(adminUserId, systemMessage);
        setSystemMessage("");
        setMessage("System message was submitted.");
    }

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>Handle Complaints</h1>
                <p>View complaints, respond to users, and send system messages.</p>
            </section>

            <section className="admin-panel">
                <select
                    value={selectedComplaintId}
                    onChange={(event) => setSelectedComplaintId(event.target.value)}
                >
                    {complaints.map((complaint) => (
                        <option key={complaint.id} value={complaint.id}>
                            {complaint.title} - {complaint.status}
                        </option>
                    ))}
                </select>

                <textarea
                    value={response}
                    onChange={(event) => setResponse(event.target.value)}
                    placeholder="Write response to selected complaint"
                />

                <button type="button" onClick={handleRespondToComplaint}>
                    Send Complaint Response
                </button>

                <textarea
                    value={systemMessage}
                    onChange={(event) => setSystemMessage(event.target.value)}
                    placeholder="Write system message"
                />

                <button type="button" onClick={handleSendSystemMessage}>
                    Send System Message
                </button>

                {message && <p className="success-message">{message}</p>}
            </section>
        </main>
    );
}