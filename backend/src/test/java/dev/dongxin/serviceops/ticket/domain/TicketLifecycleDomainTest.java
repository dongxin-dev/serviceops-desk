package dev.dongxin.serviceops.ticket.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The frozen Ticket State Machine, exercised as pure domain behaviour:
 * six legal transitions, everything else rejected, and the lifecycle
 * timestamp rules (write-once firstRespondedAt, re-stamped resolvedAt,
 * terminal closedAt).
 */
class TicketLifecycleDomainTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-07T08:00:00+08:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-10-07T09:00:00+08:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-10-07T10:00:00+08:00");
    private static final OffsetDateTime T3 = OffsetDateTime.parse("2026-10-07T11:00:00+08:00");
    private static final OffsetDateTime T4 = OffsetDateTime.parse("2026-10-07T12:00:00+08:00");
    private static final OffsetDateTime T5 = OffsetDateTime.parse("2026-10-07T13:00:00+08:00");

    private static Ticket fresh() {
        return Ticket.open("TKT-20261007-DEADBEEF1234", "title", "description",
                "MISC", TicketPriority.P2_HIGH, 1L, 1L, T0,
                T0.plusMinutes(30), T0.plusHours(4));
    }

    /** OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED (timestamped per stage). */
    private static Ticket resolved(Ticket ticket, long assigneeId) {
        ticket.assign(assigneeId, T1);
        ticket.startProgress(T2);
        ticket.resolve(T3);
        return ticket;
    }

    // ---------------- assign ----------------

    @Test
    void assignFromOpenReturnsAssignAndMovesState() {
        Ticket ticket = fresh();
        AssignmentType type = ticket.assign(10L, T1);
        assertEquals(AssignmentType.ASSIGN, type);
        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
        assertEquals(10L, ticket.getCurrentAssigneeId());
        assertEquals(T1, ticket.getUpdatedAt());
    }

    @Test
    void assignFromReopenedReturnsReassign() {
        Ticket ticket = resolved(fresh(), 10L);
        ticket.reopen(T4);
        assertEquals(TicketStatus.REOPENED, ticket.getStatus());

        AssignmentType type = ticket.assign(20L, T5);

        assertEquals(AssignmentType.REASSIGN, type);
        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
        assertEquals(20L, ticket.getCurrentAssigneeId());
    }

    @Test
    void assignRejectedFromEveryOtherState() {
        Ticket assigned = fresh();
        assigned.assign(10L, T1);
        assertThrows(IllegalTicketStateTransitionException.class, () -> assigned.assign(20L, T2));

        Ticket inProgress = fresh();
        inProgress.assign(10L, T1);
        inProgress.startProgress(T2);
        assertThrows(IllegalTicketStateTransitionException.class, () -> inProgress.assign(20L, T3));

        Ticket resolved = resolved(fresh(), 10L);
        assertThrows(IllegalTicketStateTransitionException.class, () -> resolved.assign(20L, T4));

        Ticket closed = resolved(fresh(), 10L);
        closed.close(T4);
        assertThrows(IllegalTicketStateTransitionException.class, () -> closed.assign(20L, T5));
    }

    @Test
    void assignRejectsMissingActorTimeAndBadId() {
        Ticket ticket = fresh();
        assertThrows(TicketValidationException.class, () -> ticket.assign(10L, null));
        assertThrows(TicketValidationException.class, () -> ticket.assign(null, T1));
        assertThrows(TicketValidationException.class, () -> ticket.assign(0L, T1));
    }

    // ---------------- startProgress / firstRespondedAt ----------------

    @Test
    void startFromAssignedWritesFirstRespondedAtOnce() {
        Ticket ticket = fresh();
        ticket.assign(10L, T1);
        assertNull(ticket.getFirstRespondedAt());

        ticket.startProgress(T2);

        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(T2, ticket.getFirstRespondedAt());
    }

    @Test
    void firstRespondedAtSurvivesReopenReassignRestartCycle() {
        Ticket ticket = resolved(fresh(), 10L);
        OffsetDateTime firstStamp = ticket.getFirstRespondedAt();
        assertEquals(T2, firstStamp);

        ticket.reopen(T4);
        ticket.assign(20L, T5);
        ticket.startProgress(OffsetDateTime.parse("2026-10-07T14:00:00+08:00"));

        assertEquals(T2, ticket.getFirstRespondedAt(), "first response is a once-ever stamp");
    }

    // ---------------- resolve / reopen timestamps ----------------

    @Test
    void resolveStampsResolvedAt() {
        Ticket ticket = fresh();
        ticket.assign(10L, T1);
        ticket.startProgress(T2);
        ticket.resolve(T3);
        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertEquals(T3, ticket.getResolvedAt());
    }

    @Test
    void reopenClearsResolvedAtAndKeepsAssignee() {
        Ticket ticket = resolved(fresh(), 10L);
        ticket.reopen(T4);
        assertEquals(TicketStatus.REOPENED, ticket.getStatus());
        assertNull(ticket.getResolvedAt());
        assertEquals(10L, ticket.getCurrentAssigneeId(), "assignee survives reopen");
        assertNull(ticket.getClosedAt());
        assertEquals(T4, ticket.getUpdatedAt());
    }

    @Test
    void secondResolveStampsFreshResolvedAt() {
        Ticket ticket = resolved(fresh(), 10L);
        ticket.reopen(T4);
        ticket.assign(10L, T5);
        OffsetDateTime tNewStart = OffsetDateTime.parse("2026-10-07T14:00:00+08:00");
        OffsetDateTime tNewResolve = OffsetDateTime.parse("2026-10-07T15:00:00+08:00");
        ticket.startProgress(tNewStart);
        ticket.resolve(tNewResolve);
        assertEquals(tNewResolve, ticket.getResolvedAt());
    }

    // ---------------- close ----------------

    @Test
    void closeStampsClosedAtAndKeepsResolvedAt() {
        Ticket ticket = resolved(fresh(), 10L);
        ticket.close(T4);
        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertEquals(T4, ticket.getClosedAt());
        assertEquals(T3, ticket.getResolvedAt(), "close must not touch resolvedAt");
    }

    @Test
    void closedTicketRejectsEveryLifecycleAction() {
        Ticket ticket = resolved(fresh(), 10L);
        ticket.close(T4);
        assertThrows(IllegalTicketStateTransitionException.class, () -> ticket.assign(20L, T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> ticket.startProgress(T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> ticket.resolve(T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> ticket.close(T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> ticket.reopen(T5));
    }

    // ---------------- illegal transition matrix (typical cases) ----------------

    @Test
    void openTicketCannotSkipForward() {
        Ticket open = fresh();
        assertThrows(IllegalTicketStateTransitionException.class, () -> open.startProgress(T1));
        assertThrows(IllegalTicketStateTransitionException.class, () -> open.resolve(T1));
        assertThrows(IllegalTicketStateTransitionException.class, () -> open.close(T1));
        assertThrows(IllegalTicketStateTransitionException.class, () -> open.reopen(T1));
    }

    @Test
    void assignedAndInProgressAndReopenedRejectWrongActions() {
        Ticket assigned = fresh();
        assigned.assign(10L, T1);
        assertThrows(IllegalTicketStateTransitionException.class, () -> assigned.close(T2));
        assertThrows(IllegalTicketStateTransitionException.class, () -> assigned.resolve(T2));
        assertThrows(IllegalTicketStateTransitionException.class, () -> assigned.reopen(T2));

        Ticket inProgress = fresh();
        inProgress.assign(10L, T1);
        inProgress.startProgress(T2);
        assertThrows(IllegalTicketStateTransitionException.class, () -> inProgress.close(T3));
        assertThrows(IllegalTicketStateTransitionException.class, () -> inProgress.reopen(T3));
        assertThrows(IllegalTicketStateTransitionException.class, () -> inProgress.startProgress(T3));

        Ticket reopened = resolved(fresh(), 10L);
        reopened.reopen(T4);
        assertThrows(IllegalTicketStateTransitionException.class, () -> reopened.startProgress(T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> reopened.resolve(T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> reopened.close(T5));
        assertThrows(IllegalTicketStateTransitionException.class, () -> reopened.reopen(T5));
    }

    @Test
    void repeatedResolveOnResolvedIsRejected() {
        Ticket ticket = resolved(fresh(), 10L);
        assertThrows(IllegalTicketStateTransitionException.class, () -> ticket.resolve(T4));
    }

    @Test
    void exceptionCarriesActionAndCurrentState() {
        Ticket ticket = fresh();
        IllegalTicketStateTransitionException ex = assertThrows(
                IllegalTicketStateTransitionException.class, () -> ticket.resolve(T1));
        assertNotNull(ex.getMessage());
        assertEquals(true, ex.getMessage().contains("resolve"));
        assertEquals(true, ex.getMessage().contains("OPEN"));
    }
}
