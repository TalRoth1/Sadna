import { useEffect, useMemo, useState } from "react";
import {
    getComplaints,
    respondToComplaint,
    sendSystemMessage,
} from "../../services/admin/adminComplaintsService";
import { getCurrentUser } from "../../services/currentUserService";
import type { Complaint } from "../../types/admin";

export default function AdminComplaintsPage() {
    const [complaints, setComplaints] = useState<Complaint[]>([]);
    const [selectedComplaintId, setSelectedComplaintId] = useState("");
    const [responseText, setResponseText] = useState("");
    const [systemMessage, setSystemMessage] = useState("");
    const [statusMessage, setStatusMessage] = useState("");
    const [errorMessage, setErrorMessage] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmittingResponse, setIsSubmittingResponse] = useState(false);
    const [isSendingSystemMessage, setIsSendingSystemMessage] = useState(false);

    const selectedComplaint = useMemo(
        () =>
            complaints.find(
                (complaint) => complaint.id === selectedComplaintId,
            ) ?? null,
        [complaints, selectedComplaintId],
    );

    useEffect(() => {
        loadComplaints();
    }, []);

    async function loadComplaints() {
        setIsLoading(true);
        setErrorMessage("");

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setComplaints([]);
                setSelectedComplaintId("");
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            const loadedComplaints = await getComplaints(currentUser.id);

            setComplaints(loadedComplaints);
            setSelectedComplaintId(loadedComplaints[0]?.id ?? "");
        } catch {
            setComplaints([]);
            setSelectedComplaintId("");
            setErrorMessage("Failed to load complaints.");
        } finally {
            setIsLoading(false);
        }
    }

    async function handleSubmitResponse() {
        setStatusMessage("");
        setErrorMessage("");

        if (!selectedComplaintId) {
            setErrorMessage("Select a complaint first.");
            return;
        }

        if (!responseText.trim()) {
            setErrorMessage("Response text is required.");
            return;
        }

        setIsSubmittingResponse(true);

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            await respondToComplaint(
                currentUser.id,
                selectedComplaintId,
                responseText.trim(),
            );

            const updatedComplaints = await getComplaints(currentUser.id);

            setComplaints(updatedComplaints);
            setSelectedComplaintId(updatedComplaints[0]?.id ?? "");
            setResponseText("");
            setStatusMessage("Complaint response was submitted.");
        } catch {
            setErrorMessage("Failed to submit complaint response.");
        } finally {
            setIsSubmittingResponse(false);
        }
    }

    async function handleSendSystemMessage() {
        setStatusMessage("");
        setErrorMessage("");

        if (!systemMessage.trim()) {
            setErrorMessage("System message is required.");
            return;
        }

        setIsSendingSystemMessage(true);

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            await sendSystemMessage(currentUser.id, systemMessage.trim());

            setSystemMessage("");
            setStatusMessage("System message was sent.");
        } catch {
            setErrorMessage("Failed to send system message.");
        } finally {
            setIsSendingSystemMessage(false);
        }
    }

    return (
        <main className="page-shell admin-page-shell">
            <section className="page-header">
                <h1>Handle Complaints</h1>
                <p>View open complaints, respond to users, and send system messages.</p>
            </section>

            <section className="admin-form-card">
                {isLoading ? (
                    <p className="empty-state">Loading complaints...</p>
                ) : (
                    <>
                        {complaints.length > 0 ? (
                            <>
                                <div className="admin-field-group">
                                    <label className="admin-field-label" htmlFor="complaint-select">
                                        Open complaint
                                    </label>

                                    <select
                                        id="complaint-select"
                                        className="admin-select"
                                        value={selectedComplaintId}
                                        onChange={(event) => {
                                            setSelectedComplaintId(event.target.value);
                                            setStatusMessage("");
                                            setErrorMessage("");
                                        }}
                                    >
                                        {complaints.map((complaint) => (
                                            <option key={complaint.id} value={complaint.id}>
                                                {complaint.title} - {complaint.status}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                {selectedComplaint && (
                                    <div className="admin-details-box">
                                        <div className="admin-detail-row">
                                            <span>Reporter</span>
                                            <strong>{selectedComplaint.reporterName}</strong>
                                        </div>

                                        <div className="admin-detail-row admin-detail-row-block">
                                            <span>Complaint</span>
                                            <p>{selectedComplaint.message}</p>
                                        </div>

                                        {selectedComplaint.createdAt && (
                                            <div className="admin-detail-row">
                                                <span>Created at</span>
                                                <strong>
                                                    {new Date(selectedComplaint.createdAt).toLocaleString()}
                                                </strong>
                                            </div>
                                        )}
                                    </div>
                                )}
                                <textarea
                                    className="admin-textarea"
                                    value={responseText}
                                    onChange={(event) => setResponseText(event.target.value)}
                                    placeholder="Write complaint response"
                                    rows={5}
                                />

                                <button
                                    type="button"
                                    className="admin-primary-button"
                                    onClick={handleSubmitResponse}
                                    disabled={isSubmittingResponse}
                                >
                                    {isSubmittingResponse ? "Sending..." : "Send Complaint Response"}
                                </button>
                            </>
                        ) : (
                            <p className="empty-state">
                                There are no open complaints to handle.
                            </p>
                        )}

                        <div className="admin-form-section">

                        <textarea
                            className="admin-textarea"
                            value={systemMessage}
                            onChange={(event) => setSystemMessage(event.target.value)}
                            placeholder="Write system message"
                            rows={5}
                        />

                            <button
                                type="button"
                                className="admin-primary-button"
                                onClick={handleSendSystemMessage}
                                disabled={isSendingSystemMessage}
                            >
                                {isSendingSystemMessage ? "Sending..." : "Send System Message"}
                            </button>
                        </div>

                        {statusMessage && (
                            <div className="success-message">{statusMessage}</div>
                        )}

                        {errorMessage && (
                            <div className="error-message">{errorMessage}</div>
                        )}
                    </>
                )}
            </section>
        </main>
    );
}