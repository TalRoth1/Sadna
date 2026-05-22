package org.example.DomainLayer.PolicyManagment;

import java.util.Map;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.UserAggregate.User;

public class LoneSeatRule implements IPurchaseRule {

    private UUID id = UUID.randomUUID();
    private boolean allowLoneSeat;

    public LoneSeatRule(boolean allowLoneSeat)
    {
        this.allowLoneSeat = allowLoneSeat;
    }

    @Override
    public UUID getId()
    {
        return this.id;
    }

    public boolean isAllowLoneSeat() {
        return this.allowLoneSeat;
    }

    @Override
    public boolean doesHold(ActivePurchase purchase, User user, Event event) {
        if (allowLoneSeat) {
            return true;
        }

        Map<UUID, Float> purchaseTickets = purchase.getTicketIDs();

        for (UUID ticketId : purchaseTickets.keySet()) {
            Ticket ticket = event.getTicket(ticketId);

            if (ticket instanceof SittingTicket sittingTicket) {
                Area area = event.getLayout().requireArea(sittingTicket.getAreaId());

                if (area instanceof SittingArea sittingArea) {
                    int row = sittingTicket.getSeatRow();
                    int col = sittingTicket.getSeatNumber();

                    // Check neighbors to the immediate Left (-1) and Right (+1)
                    for (int offset : new int[]{-1, 1}) {
                        SittingTicket neighbor = sittingArea.getTicketAt(row, col + offset, event);

                        // If the neighbor is EMPTY and NOT being bought, it might become "lone"
                        if (neighbor != null && 
                            !purchaseTickets.containsKey(neighbor.getTicketId()) && 
                            neighbor.getStatus() == TicketStatus.AVAILABLE) {

                            // Check the neighbor's own flanking seats
                            SittingTicket nLeft = sittingArea.getTicketAt(row, neighbor.getSeatNumber() - 1, event);
                            SittingTicket nRight = sittingArea.getTicketAt(row, neighbor.getSeatNumber() + 1, event);

                            // A side is "blocked" if it's a wall, already sold, or in this purchase
                            boolean leftBlocked = (nLeft == null || 
                                                nLeft.getStatus() != TicketStatus.AVAILABLE || 
                                                purchaseTickets.containsKey(nLeft.getTicketId()));
                            
                            boolean rightBlocked = (nRight == null || 
                                                    nRight.getStatus() != TicketStatus.AVAILABLE || 
                                                    purchaseTickets.containsKey(nRight.getTicketId()));

                            // If both sides are blocked, the neighbor is now a lone seat
                            if (leftBlocked && rightBlocked) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }   
}
