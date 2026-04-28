package org.example.DomainLayer.EventAggregate;

/**
 * Describes one row of numbered seats in a sitting area (row label + seat count).
 */
public final class SeatRowSpec {
    private final String rowLabel;
    private final int seatCount;

    public SeatRowSpec(String rowLabel, int seatCount) {
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("rowLabel must be non-blank");
        }
        if (seatCount <= 0) {
            throw new IllegalArgumentException("seatCount must be positive");
        }
        this.rowLabel = rowLabel;
        this.seatCount = seatCount;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatCount() {
        return seatCount;
    }
}
