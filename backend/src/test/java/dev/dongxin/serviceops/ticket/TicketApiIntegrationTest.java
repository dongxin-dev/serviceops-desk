package dev.dongxin.serviceops.ticket;

import dev.dongxin.serviceops.ticket.application.CreateTicketCommand;
import dev.dongxin.serviceops.ticket.application.TicketApplicationService;
import dev.dongxin.serviceops.ticket.application.exception.SlaPolicyNotConfiguredException;
import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import dev.dongxin.serviceops.ticket.domain.TicketStatus;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.AppUserJpaEntity;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.SpringDataAppUserRepository;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.SlaPolicyJpaEntity;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.SpringDataSlaPolicyRepository;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.TicketJpaEntity;
import dev.dongxin.serviceops.ticket.infrastructure.persistence.SpringDataTicketRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.hamcrest.Matchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * First vertical slice: Create Ticket + Get Ticket Detail against the real
 * PostgreSQL schema (Flyway V1/V2). Test data is isolated under a dedicated
 * requester and removed after the class, so runs stay repeatable and order-free.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketApiIntegrationTest {

    private static final String TICKET_NO_PATTERN = "^TKT-\\d{8}-[0-9A-F]{12}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataAppUserRepository appUserRepository;

    @Autowired
    private SpringDataSlaPolicyRepository slaPolicyRepository;

    @Autowired
    private SpringDataTicketRepository ticketRepository;

    @Autowired
    private TicketApplicationService ticketService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long requesterId;
    private final String salt = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void createRequester() {
        OffsetDateTime now = OffsetDateTime.now();
        AppUserJpaEntity user = appUserRepository.save(new AppUserJpaEntity(
                "it-requester-" + salt, "no-password", "Integration Requester",
                "it-requester-" + salt + "@test.local", "REQUESTER", "ACTIVE", now, now));
        requesterId = user.getId();
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM ticket WHERE requester_id = ?", requesterId);
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", requesterId);
    }

    private String createJson(String title, String description, String category,
                              String priority, Long requester) {
        return """
                {"title":"%s","description":"%s","category":"%s","priority":"%s","requesterId":%s}"""
                .formatted(title, description, category, priority, requester);
    }

    private long readId(String json) {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(json).get("id").asLong();
    }

    // ---------------------------------------------------------------
    // B1 - happy path
    // ---------------------------------------------------------------

    @Test
    void createTicketSucceedsAndPersistsSnapshotToPostgres() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("Printer jam on 3F", "Floor 3 printer keeps jamming",
                                "HARDWARE", "P2_HIGH", requesterId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.matchesRegex(".*/api/v1/tickets/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ticketNo", Matchers.matchesRegex(TICKET_NO_PATTERN)))
                .andExpect(jsonPath("$.title").value("Printer jam on 3F"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("P2_HIGH"))
                .andExpect(jsonPath("$.requesterId").value(requesterId))
                .andExpect(jsonPath("$.currentAssigneeId").doesNotExist())
                .andExpect(jsonPath("$.firstRespondedAt").doesNotExist())
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.escalationLevel").value(0))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        long id = readId(result.getResponse().getContentAsString());

        // Data really landed in PostgreSQL, with a correct SLA snapshot (ADR-003/005).
        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        SlaPolicyJpaEntity policy = slaPolicyRepository
                .findByPriorityAndEnabled(TicketPriority.P2_HIGH, true).orElseThrow();

        assertEquals(TicketStatus.OPEN, stored.getStatus());
        assertEquals(requesterId, stored.getRequesterId());
        assertEquals(policy.getId(), stored.getSlaPolicyId());
        assertEquals(0, stored.getEscalationLevel());
        assertNotNull(stored.getCreatedAt());
        assertEquals(stored.getCreatedAt(), stored.getUpdatedAt());
        assertEquals(stored.getCreatedAt().plusMinutes(policy.getResponseMinutes()),
                stored.getResponseDueAt());
        assertEquals(stored.getCreatedAt().plusMinutes(policy.getResolutionMinutes()),
                stored.getResolutionDueAt());
        assertEquals("TKT-" + stored.getCreatedAt().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
                stored.getTicketNo().substring(0, 12));
    }

    // ---------------------------------------------------------------
    // B2 / B3 - validation failures
    // ---------------------------------------------------------------

    @Test
    void blankTitleIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("  ", "desc", "HARDWARE", "P2_HIGH", requesterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }

    @Test
    void oversizedTitleIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("T".repeat(201), "desc", "HARDWARE", "P2_HIGH", requesterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }

    @Test
    void blankCategoryIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("title", "desc", "", "P2_HIGH", requesterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }

    @Test
    void oversizedCategoryIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("title", "desc", "C".repeat(65), "P2_HIGH", requesterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }

    // ---------------------------------------------------------------
    // B4 - requester does not exist
    // ---------------------------------------------------------------

    @Test
    void unknownRequesterIsRejectedWith404() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("title", "desc", "HARDWARE", "P2_HIGH", 9_999_999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RequesterNotFound"));
    }

    // ---------------------------------------------------------------
    // B5 - no enabled SLA policy for the priority
    // Tested at service level inside a rolled-back transaction so the
    // seeded sla_policy data itself is never permanently modified.
    // ---------------------------------------------------------------

    @Test
    @Transactional
    void createFailsWhenSlaPolicyIsMissing() {
        SlaPolicyJpaEntity policy = slaPolicyRepository
                .findByPriorityAndEnabled(TicketPriority.P4_LOW, true).orElseThrow();
        slaPolicyRepository.delete(policy);
        slaPolicyRepository.flush();

        assertThrows(SlaPolicyNotConfiguredException.class, () -> ticketService.create(
                new CreateTicketCommand("title", "description", "CATEGORY",
                        TicketPriority.P4_LOW, requesterId)));
    }

    // ---------------------------------------------------------------
    // C - Get Ticket
    // ---------------------------------------------------------------

    @Test
    void getTicketReturnsCreatedResource() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson("VPN access lost", "Cannot reach corporate VPN",
                                "NETWORK", "P3_MEDIUM", requesterId)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = readId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.category").value("NETWORK"));
    }

    @Test
    void getMissingTicketReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", 9_999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TicketNotFound"))
                .andExpect(jsonPath("$.path").value("/api/v1/tickets/9999999999"));
    }
}
