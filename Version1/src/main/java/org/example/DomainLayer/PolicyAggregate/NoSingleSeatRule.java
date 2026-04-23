package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.UserAggregate.User;

import java.util.*;

public class NoSingleSeatRule implements IPurchaseRule
{
    public boolean doesHold(ActivePurchase ap, User user, Event event)
    {
        Map<Integer, Ticket> allTickets = event.getTicketsView();
        List<Integer> currentSelection = ap.getTicketIDs();

        // 1. נזהה את כל השורות שבהן המשתמש בחר כרטיסים כדי לא לבדוק סתם את כל האולם
        Set<Integer> affectedRows = new HashSet<>();
        for (int i = 0; i < currentSelection.size(); i++)
        {
            int ticketID = currentSelection.get(i);
            Ticket currentTicket = allTickets.get(ticketID);

            if (currentTicket instanceof SittingTicket)
            {
                SittingTicket st = (SittingTicket) currentTicket;
                affectedRows.add(st.getSeatRow());
            }
        }

        // 2. עבור כל שורה שהושפעה, נבדוק אם נשאר בה כיסא בודד "מסכן"
        for (Integer rowNum : affectedRows)
        {
            // א. נאסוף את כל הכיסאות ששייכים לשורה הזו
            List<SittingTicket> rowTickets = new ArrayList<>();
            for (Ticket t : allTickets.values())
            {
                if (t instanceof SittingTicket)
                {
                    SittingTicket st = (SittingTicket) t;
                    if (st.getSeatRow() == rowNum)
                    {
                        rowTickets.add(st);
                    }
                }
            }

            // ב. נמיין את הכיסאות בשורה לפי מספר המושב כדי שנוכל לבדוק שכנים
            rowTickets.sort(new Comparator<SittingTicket>() {
                @Override
                public int compare(SittingTicket s1, SittingTicket s2) {
                    return Integer.compare(s1.getSeatNumber(), s2.getSeatNumber());
                }
            });

            // ג. נעבור על השורה הממוינת ונחפש כיסא שנשאר פנוי לגמרי לבד
            for (int j = 0; j < rowTickets.size(); j++)
            {
                SittingTicket current = rowTickets.get(j);

                // האם הכיסא הזה יישאר פנוי (AVAILABLE) ולא נבחר ברכישה הנוכחית?
                boolean remainsAvailable = (current.getStatus() == TicketStatus.AVAILABLE) &&
                        !currentSelection.contains(current.getTicketId());

                if (remainsAvailable)
                {
                    // נבדוק אם השכן משמאל "חוסם" אותו (או שזה קיר)
                    boolean blockedOnLeft = true;
                    if (j > 0) {
                        SittingTicket leftNeighbor = rowTickets.get(j - 1);
                        // שכן חוסם אם הוא כבר תפוס במערכת או נבחר עכשיו ברכישה
                        blockedOnLeft = (leftNeighbor.getStatus() != TicketStatus.AVAILABLE) ||
                                currentSelection.contains(leftNeighbor.getTicketId());
                    }

                    // נבדוק אם השכן מימין "חוסם" אותו (או שזה קיר)
                    boolean blockedOnRight = true;
                    if (j < rowTickets.size() - 1) {
                        SittingTicket rightNeighbor = rowTickets.get(j + 1);
                        blockedOnRight = (rightNeighbor.getStatus() != TicketStatus.AVAILABLE) ||
                                currentSelection.contains(rightNeighbor.getTicketId());
                    }

                    // אם הוא חסום משני הצדדים - מצאנו כיסא בודד! החוק הופר.
                    if (blockedOnLeft && blockedOnRight) {
                        return false;
                    }
                }
            }
        }

        return true; // עברנו על כל השורות ולא מצאנו בעיה
    }
}
