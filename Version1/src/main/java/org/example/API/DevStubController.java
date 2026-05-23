package org.example.API;

import java.util.LinkedHashMap;
import java.util.Map;

import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.InfrastructureLayer.SimulatedPaymentGateway;
import org.example.InfrastructureLayer.SimulatedTicketingGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that flip the one-shot outcome on the simulated
 * payment and ticketing stubs. Wrapped in {@code @Profile("dev")} so
 * production builds (run with {@code SPRING_PROFILES_ACTIVE=prod}) don't
 * expose these knobs — they're strictly for exercising the refund /
 * cancellation paths the assignment requires us to demonstrate.
 *
 * Typical workflow:
 *   1. {@code POST /api/dev/stubs/payment/decline-next}
 *   2. Click "Confirm payment" in the UI — observe the decline path.
 *   3. {@code POST /api/dev/stubs/ticketing/fail-next}
 *   4. Click "Confirm payment" again — observe the refund + cancellation
 *      path.
 *
 * Each toggle is one-shot (auto-resets to the success outcome after a
 * single consumption), so you don't have to remember to "turn it back
 * on".
 */
@RestController
@RequestMapping("/api/dev/stubs")
@Profile("dev")
public class DevStubController {

    private final SimulatedPaymentGateway paymentGateway;
    private final SimulatedTicketingGateway ticketingGateway;

    public DevStubController(SimulatedPaymentGateway paymentGateway,
                             SimulatedTicketingGateway ticketingGateway) {
        this.paymentGateway = paymentGateway;
        this.ticketingGateway = ticketingGateway;
    }

    @PostMapping("/payment/decline-next")
    public ResponseEntity<ApiResponse<Map<String, String>>> declineNextPayment() {
        paymentGateway.declineNextPayment();
        return ResponseEntity.ok(ApiResponse.success(
                "The next pay() call will decline.",
                Map.of("nextPayOutcome", paymentGateway.peekNextPayOutcome().name())));
    }

    @PostMapping("/payment/approve-next")
    public ResponseEntity<ApiResponse<Map<String, String>>> approveNextPayment() {
        paymentGateway.approveNextPayment();
        return ResponseEntity.ok(ApiResponse.success(
                "The next pay() call will approve.",
                Map.of("nextPayOutcome", paymentGateway.peekNextPayOutcome().name())));
    }

    @PostMapping("/payment/refund/fail-next")
    public ResponseEntity<ApiResponse<Map<String, String>>> failNextRefund() {
        paymentGateway.failNextRefund();
        return ResponseEntity.ok(ApiResponse.success(
                "The next refund() call will fail.",
                Map.of("nextRefundOutcome", paymentGateway.peekNextRefundOutcome().name())));
    }

    @PostMapping("/payment/refund/succeed-next")
    public ResponseEntity<ApiResponse<Map<String, String>>> succeedNextRefund() {
        paymentGateway.succeedNextRefund();
        return ResponseEntity.ok(ApiResponse.success(
                "The next refund() call will succeed.",
                Map.of("nextRefundOutcome", paymentGateway.peekNextRefundOutcome().name())));
    }

    @PostMapping("/ticketing/fail-next")
    public ResponseEntity<ApiResponse<Map<String, String>>> failNextTicketing() {
        ticketingGateway.failNextIssue();
        return ResponseEntity.ok(ApiResponse.success(
                "The next issueTickets() call will throw.",
                Map.of("nextTicketingOutcome", ticketingGateway.peekNextOutcome().name())));
    }

    @PostMapping("/ticketing/succeed-next")
    public ResponseEntity<ApiResponse<Map<String, String>>> succeedNextTicketing() {
        ticketingGateway.succeedNextIssue();
        return ResponseEntity.ok(ApiResponse.success(
                "The next issueTickets() call will succeed.",
                Map.of("nextTicketingOutcome", ticketingGateway.peekNextOutcome().name())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> currentState() {
        Map<String, String> state = new LinkedHashMap<>();
        state.put("nextPayOutcome", paymentGateway.peekNextPayOutcome().name());
        state.put("nextRefundOutcome", paymentGateway.peekNextRefundOutcome().name());
        state.put("nextTicketingOutcome", ticketingGateway.peekNextOutcome().name());
        return ResponseEntity.ok(ApiResponse.success("Current stub state", state));
    }
}
