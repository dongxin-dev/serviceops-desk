package dev.dongxin.serviceops.ticket;

import dev.dongxin.serviceops.ticket.infrastructure.persistence.AppUserJpaEntity;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.SpringDataAppUserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 3 lifecycle flow through the real HTTP API against PostgreSQL:
 * assign / start / resolve / close / reopen, the ticket_assignment history
 * table, and the partial-unique-index invariant (max one active row/ticket).
 * Isolated under a dedicated salted requester; removed after the class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketLifecycleIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataAppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String salt = UUID.randomUUID().toString().substring(0, 8);
    private Long requesterId;
    private Long agentAId;
    private Long agentBId;

    @BeforeAll
    void createUsers() {
        requesterId = createUser("lifecycle-requester-" + salt, "REQUESTER");
        agentAId = createUser("lifecycle-agent-a-" + salt, "AGENT");
        agentBId = createUser("lifecycle-agent-b-" + salt, "AGENT");
    }

    private Long createUser(String username, String role) {
        OffsetDateTime now = OffsetDateTime.now();
        AppUserJpaEntity user = appUserRepository.save(new AppUserJpaEntity(
                username, "no-password", "Lifecycle User",
                username + "@test.local", role, "ACTIVE", now, now));
        return user.getId();
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM ticket_assignment WHERE ticket_id IN "
                + "(SELECT id FROM ticket WHERE requester_id = ?)", requesterId);
        jdbcTemplate.update("DELETE FROM ticket WHERE requester_id = ?", requesterId);
        jdbcTemplate.update("DELETE FROM app_user WHERE id IN (?, ?, ?)",
                requesterId, agentAId, agentBId);
    }

    // ---------------- helpers ----------------

    private long createTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(("""
                                {"title":"lifecycle ticket","description":"d","category":"MISC",\
                                "priority":"P2_HIGH","requesterId":%s}""").formatted(requesterId)))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult call(long id, String action, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/tickets/{id}/{action}", id, action)
                .contentType(APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private static String versionBody(int version) {
        return "{\"version\":" + version + "}";
    }

    private static String assignBody(long assignee, long actor, int version) {
        return "{\"assigneeId\":" + assignee + ",\"actorId\":" + actor
                + ",\"version\":" + version + "}";
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private void expectStatus(long id, String status) {
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ticket WHERE id = ?", String.class, id);
        assertEquals(status, dbStatus);
    }

    private int dbVersion(long id) {
        return jdbcTemplate.queryForObject("SELECT version FROM ticket WHERE id = ?", int.class, id);
    }

    private List<Map<String, Object>> history(long id) {
        return jdbcTemplate.queryForList(
                "SELECT assignment_type, assignee_id, assigned_by, assigned_at, ended_at "
                        + "FROM ticket_assignment WHERE ticket_id = ? ORDER BY id", id);
    }

    // ---------------- assign ----------------

    @Test
    void assignHappyPathUpdatesTicketAndWritesHistory() throws Exception {
        long id = createTicket();

        MvcResult result = call(id, "assign", assignBody(agentAId, requesterId, 0));

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode json = body(result);
        assertEquals("ASSIGNED", json.get("status").asString());
        assertEquals(agentAId, json.get("currentAssigneeId").asLong());
        assertEquals(1, json.get("version").asInt());

        expectStatus(id, "ASSIGNED");
        assertEquals(1, dbVersion(id), "response version must equal the database-incremented version");

        List<Map<String, Object>> rows = history(id);
        assertEquals(1, rows.size());
        assertEquals("ASSIGN", rows.get(0).get("assignment_type"));
        assertEquals(agentAId, rows.get(0).get("assignee_id"));
        assertEquals(requesterId, rows.get(0).get("assigned_by"));
        assertNotNull(rows.get(0).get("assigned_at"));
        assertNull(rows.get(0).get("ended_at"), "fresh assignment is active");

        mockMvc.perform(get("/api/v1/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void assignUnknownAssigneeIs404() throws Exception {
        long id = createTicket();
        MvcResult result = call(id, "assign", assignBody(9_999_999_999L, requesterId, 0));
        assertEquals(404, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("AssigneeNotFound", body(result).get("error").asString());
        expectStatus(id, "OPEN");
    }

    @Test
    void assignUnknownActorIs404() throws Exception {
        long id = createTicket();
        MvcResult result = call(id, "assign", assignBody(agentAId, 9_999_999_999L, 0));
        assertEquals(404, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("ActorNotFound", body(result).get("error").asString());
        expectStatus(id, "OPEN");
    }

    @Test
    void duplicateAssignIsIllegalTransition409() throws Exception {
        long id = createTicket();
        assertEquals(200, call(id, "assign", assignBody(agentAId, requesterId, 0))
                .getResponse().getStatus());

        MvcResult second = call(id, "assign", assignBody(agentBId, requesterId, 1));
        assertEquals(409, second.getResponse().getStatus(), second.getResponse().getContentAsString());
        assertEquals("IllegalTicketStateTransition", body(second).get("error").asString());
        // assignee unchanged: still agent A, still one active ASSIGN row
        assertEquals(agentAId, jdbcTemplate.queryForObject(
                "SELECT current_assignee_id FROM ticket WHERE id = ?", Long.class, id));
        assertEquals(1, history(id).size());
    }

    @Test
    void assignWithStaleVersionIs409() throws Exception {
        long id = createTicket();
        // first assign succeeds and bumps version 0 -> 1
        assertEquals(200, call(id, "assign", assignBody(agentAId, requesterId, 0))
                .getResponse().getStatus());

        // second call still claims version 0: the version pre-check fires
        // before the domain action, so this is a stale-revision 409.
        MvcResult stale = call(id, "assign", assignBody(agentBId, requesterId, 0));
        assertEquals(409, stale.getResponse().getStatus(), stale.getResponse().getContentAsString());
        assertEquals("TicketStaleRevision", body(stale).get("error").asString());
        assertEquals(agentAId, jdbcTemplate.queryForObject(
                "SELECT current_assignee_id FROM ticket WHERE id = ?", Long.class, id));
    }

    // ---------------- actions ----------------

    @Test
    void startFromOpenIsIllegal409() throws Exception {
        long id = createTicket();
        MvcResult result = call(id, "start", versionBody(0));
        assertEquals(409, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("IllegalTicketStateTransition", body(result).get("error").asString());
    }

    @Test
    void fullLifecycleChainPersistsEveryStage() throws Exception {
        long id = createTicket();

        // OPEN -assign-> ASSIGNED -start-> IN_PROGRESS -resolve-> RESOLVED
        assertEquals(200, call(id, "assign", assignBody(agentAId, requesterId, 0)).getResponse().getStatus());
        MvcResult started = call(id, "start", versionBody(1));
        assertEquals(200, started.getResponse().getStatus(), started.getResponse().getContentAsString());
        assertEquals(2, body(started).get("version").asInt());
        OffsetDateTime firstResponded = jdbcTemplate.queryForObject(
                "SELECT first_responded_at FROM ticket WHERE id = ?", OffsetDateTime.class, id);
        assertNotNull(firstResponded);

        assertEquals(200, call(id, "resolve", versionBody(2)).getResponse().getStatus());
        OffsetDateTime firstResolved = jdbcTemplate.queryForObject(
                "SELECT resolved_at FROM ticket WHERE id = ?", OffsetDateTime.class, id);
        assertNotNull(firstResolved);

        // RESOLVED -reopen-> REOPENED: resolved_at cleared, assignee kept
        assertEquals(200, call(id, "reopen", versionBody(3)).getResponse().getStatus());
        expectStatus(id, "REOPENED");
        assertNull(jdbcTemplate.queryForObject(
                "SELECT resolved_at FROM ticket WHERE id = ?", OffsetDateTime.class, id));
        assertEquals(agentAId, jdbcTemplate.queryForObject(
                "SELECT current_assignee_id FROM ticket WHERE id = ?", Long.class, id));

        // REOPENED -assign(agentB)-> ASSIGNED: old history closed, REASSIGN active
        MvcResult reassigned = call(id, "assign", assignBody(agentBId, requesterId, 4));
        assertEquals(200, reassigned.getResponse().getStatus(), reassigned.getResponse().getContentAsString());
        assertEquals("ASSIGNED", body(reassigned).get("status").asString());

        List<Map<String, Object>> rows = history(id);
        assertEquals(2, rows.size());
        assertEquals("ASSIGN", rows.get(0).get("assignment_type"));
        assertNotNull(rows.get(0).get("ended_at"), "old active row must be closed");
        assertEquals("REASSIGN", rows.get(1).get("assignment_type"));
        assertEquals(agentBId, rows.get(1).get("assignee_id"));
        assertNull(rows.get(1).get("ended_at"));

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_assignment WHERE ticket_id = ? AND ended_at IS NULL",
                Integer.class, id);
        assertEquals(1, activeCount, "partial unique index invariant: one active row");

        // second cycle: start must NOT overwrite first_responded_at
        assertEquals(200, call(id, "start", versionBody(5)).getResponse().getStatus());
        OffsetDateTime stillFirst = jdbcTemplate.queryForObject(
                "SELECT first_responded_at FROM ticket WHERE id = ?", OffsetDateTime.class, id);
        assertEquals(firstResponded, stillFirst, "first response is stamped exactly once ever");

        assertEquals(200, call(id, "resolve", versionBody(6)).getResponse().getStatus());
        OffsetDateTime secondResolved = jdbcTemplate.queryForObject(
                "SELECT resolved_at FROM ticket WHERE id = ?", OffsetDateTime.class, id);
        assertNotNull(secondResolved);
        assertEquals(false, secondResolved.isBefore(firstResolved), "second resolve re-stamps resolved_at");

        // RESOLVED -close-> CLOSED stamps closed_at, history row stays active
        MvcResult closed = call(id, "close", versionBody(7));
        assertEquals(200, closed.getResponse().getStatus(), closed.getResponse().getContentAsString());
        assertEquals("CLOSED", body(closed).get("status").asString());
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT closed_at FROM ticket WHERE id = ?", OffsetDateTime.class, id));
        assertEquals(8, dbVersion(id));
        assertNull(history(id).get(1).get("ended_at"),
                "V1 close does not end the assignment history row");

        // CLOSED is terminal
        MvcResult afterClosed = call(id, "reopen", versionBody(8));
        assertEquals(409, afterClosed.getResponse().getStatus());
        assertEquals("IllegalTicketStateTransition", body(afterClosed).get("error").asString());
    }

    @Test
    void reassignToSameUserIsAllowedAndStartsNewCycle() throws Exception {
        long id = createTicket();
        assertEquals(200, call(id, "assign", assignBody(agentAId, requesterId, 0)).getResponse().getStatus());
        assertEquals(200, call(id, "start", versionBody(1)).getResponse().getStatus());
        assertEquals(200, call(id, "resolve", versionBody(2)).getResponse().getStatus());
        assertEquals(200, call(id, "reopen", versionBody(3)).getResponse().getStatus());

        MvcResult again = call(id, "assign", assignBody(agentAId, requesterId, 4));
        assertEquals(200, again.getResponse().getStatus(), again.getResponse().getContentAsString());
        assertEquals("ASSIGNED", body(again).get("status").asString());

        List<Map<String, Object>> rows = history(id);
        assertEquals(2, rows.size());
        assertNotNull(rows.get(0).get("ended_at"));
        assertEquals("REASSIGN", rows.get(1).get("assignment_type"));
        assertEquals(agentAId, rows.get(1).get("assignee_id"));
    }
}
