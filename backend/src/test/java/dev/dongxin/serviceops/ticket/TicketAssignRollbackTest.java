package dev.dongxin.serviceops.ticket;

import dev.dongxin.serviceops.ticket.application.CreateTicketCommand;
import dev.dongxin.serviceops.ticket.application.TicketApplicationService;
import dev.dongxin.serviceops.ticket.application.port.NewTicketAssignment;
import dev.dongxin.serviceops.ticket.application.port.TicketAssignmentHistory;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.AppUserJpaEntity;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.SpringDataAppUserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Proves the assign use case is atomic: when the assignment-history write
 * fails, the already-flushed ticket UPDATE must still be rolled back with
 * the transaction - no half-completed lifecycle state may survive.
 * Separate class because the bean override forks its own application context.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketAssignRollbackTest {

    @Autowired
    private TicketApplicationService ticketService;

    @Autowired
    private SpringDataAppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Replaces the real JPA history adapter for this context only. */
    @MockitoBean
    private TicketAssignmentHistory assignmentHistory;

    private final String salt = UUID.randomUUID().toString().substring(0, 8);
    private Long requesterId;
    private Long agentId;

    @BeforeAll
    void createUsers() {
        requesterId = createUser("rollback-requester-" + salt);
        agentId = createUser("rollback-agent-" + salt);
    }

    private Long createUser(String username) {
        OffsetDateTime now = OffsetDateTime.now();
        return appUserRepository.save(new AppUserJpaEntity(username, "no-password",
                "Rollback User", username + "@test.local", "AGENT", "ACTIVE", now, now)).getId();
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM ticket_assignment WHERE ticket_id IN "
                + "(SELECT id FROM ticket WHERE requester_id = ?)", requesterId);
        jdbcTemplate.update("DELETE FROM ticket WHERE requester_id IN (?, ?)", requesterId, agentId);
        jdbcTemplate.update("DELETE FROM app_user WHERE id IN (?, ?)", requesterId, agentId);
    }

    @Test
    void assignRollsBackEntirelyWhenHistoryAppendFails() {
        Ticket ticket = ticketService.create(new CreateTicketCommand(
                "rollback probe", "description", "MISC", TicketPriority.P3_MEDIUM, requesterId));
        long id = ticket.getId();

        doThrow(new RuntimeException("simulated assignment history failure"))
                .when(assignmentHistory).append(any(NewTicketAssignment.class));

        assertThrows(RuntimeException.class,
                () -> ticketService.assign(id, agentId, requesterId, 0));

        // The ticket UPDATE was flushed inside the transaction, yet the
        // rollback must have erased every trace of the failed assign.
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM ticket WHERE id = ?", String.class, id);
        assertEquals("OPEN", status);
        assertNull(jdbcTemplate.queryForObject(
                "SELECT current_assignee_id FROM ticket WHERE id = ?", Long.class, id));
        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "SELECT version FROM ticket WHERE id = ?", Integer.class, id));
        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_assignment WHERE ticket_id = ?", Integer.class, id));
    }
}
