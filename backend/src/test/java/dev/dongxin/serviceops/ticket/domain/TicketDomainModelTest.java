package dev.dongxin.serviceops.ticket.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the frozen Ticket State Machine states and priority values.
 * Any accidental rename/addition here must fail before code review, not in production.
 */
class TicketDomainModelTest {

    @Test
    void ticketStatusKeepsExactlyTheSixFrozenStates() {
        assertEquals(
                "OPEN,ASSIGNED,IN_PROGRESS,RESOLVED,CLOSED,REOPENED",
                java.util.Arrays.stream(TicketStatus.values()).map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    @Test
    void ticketPriorityKeepsExactlyTheFourFrozenLevels() {
        assertEquals(
                "P1_CRITICAL,P2_HIGH,P3_MEDIUM,P4_LOW",
                java.util.Arrays.stream(TicketPriority.values()).map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(",")));
    }
}
