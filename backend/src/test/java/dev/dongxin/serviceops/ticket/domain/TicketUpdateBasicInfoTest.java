package dev.dongxin.serviceops.ticket.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * updateBasicInfo is the only sanctioned way to mutate ticket basic info.
 * Semantics under test: null = keep, provided = re-validated, version and all
 * other fields stay untouched.
 */
class TicketUpdateBasicInfoTest {

    private static Ticket newTicket() {
        OffsetDateTime now = OffsetDateTime.now();
        return Ticket.open("TKT-20261007-DEADBEEF1234", "Old title", "Old description",
                "OLD", TicketPriority.P2_HIGH, 1L, 1L, now,
                now.plusMinutes(30), now.plusHours(4));
    }

    @Test
    void updateTitleKeepsOtherFields() {
        Ticket ticket = newTicket();
        OffsetDateTime later = OffsetDateTime.now();
        ticket.updateBasicInfo("New title", null, null, later);
        assertEquals("New title", ticket.getTitle());
        assertEquals("Old description", ticket.getDescription());
        assertEquals("OLD", ticket.getCategory());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(later, ticket.getUpdatedAt());
    }

    @Test
    void updateDescriptionKeepsOtherFields() {
        Ticket ticket = newTicket();
        ticket.updateBasicInfo(null, "New description", null, OffsetDateTime.now());
        assertEquals("New description", ticket.getDescription());
        assertEquals("Old title", ticket.getTitle());
        assertEquals("OLD", ticket.getCategory());
    }

    @Test
    void updateCategoryKeepsOtherFields() {
        Ticket ticket = newTicket();
        ticket.updateBasicInfo(null, null, "NETWORK", OffsetDateTime.now());
        assertEquals("NETWORK", ticket.getCategory());
        assertEquals("Old title", ticket.getTitle());
        assertEquals("Old description", ticket.getDescription());
    }

    @Test
    void updateMultipleFieldsAtOnce() {
        Ticket ticket = newTicket();
        ticket.updateBasicInfo("T2", "D2", "C2", OffsetDateTime.now());
        assertEquals("T2", ticket.getTitle());
        assertEquals("D2", ticket.getDescription());
        assertEquals("C2", ticket.getCategory());
    }

    @Test
    void emptyUpdateIsRejectedByTheAggregateItself() {
        Ticket ticket = newTicket();
        OffsetDateTime before = ticket.getUpdatedAt();
        // The no-op rule lives in the domain, not only in web / command layers.
        assertThrows(TicketValidationException.class,
                () -> ticket.updateBasicInfo(null, null, null, OffsetDateTime.now()));
        assertEquals("Old title", ticket.getTitle());
        assertEquals("Old description", ticket.getDescription());
        assertEquals("OLD", ticket.getCategory());
        assertEquals(before, ticket.getUpdatedAt(), "rejected update must not move updatedAt");
    }

    @Test
    void blankTitleIsRejected() {
        Ticket ticket = newTicket();
        assertThrows(TicketValidationException.class,
                () -> ticket.updateBasicInfo("   ", null, null, OffsetDateTime.now()));
    }

    @Test
    void oversizedTitleIsRejected() {
        Ticket ticket = newTicket();
        assertThrows(TicketValidationException.class,
                () -> ticket.updateBasicInfo("T".repeat(201), null, null, OffsetDateTime.now()));
    }

    @Test
    void blankDescriptionIsRejected() {
        Ticket ticket = newTicket();
        assertThrows(TicketValidationException.class,
                () -> ticket.updateBasicInfo(null, "  ", null, OffsetDateTime.now()));
    }

    @Test
    void oversizedCategoryIsRejected() {
        Ticket ticket = newTicket();
        assertThrows(TicketValidationException.class,
                () -> ticket.updateBasicInfo(null, null, "C".repeat(65), OffsetDateTime.now()));
    }

    @Test
    void missingUpdatedAtIsRejected() {
        Ticket ticket = newTicket();
        assertThrows(TicketValidationException.class,
                () -> ticket.updateBasicInfo("New title", null, null, null));
    }

    @Test
    void domainUpdateNeverTouchesVersion() {
        Ticket ticket = newTicket();
        assertNull(ticket.getVersion());
        ticket.updateBasicInfo("New title", "New description", "NEW", OffsetDateTime.now());
        assertNull(ticket.getVersion(), "version must stay untouched - JPA @Version owns it");
    }
}
