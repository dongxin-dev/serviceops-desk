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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    // Tested at service level inside a rolled-back transaction. The seeded
    // policy is disabled, never deleted: legitimate tickets reference it via
    // fk_ticket_sla_policy, and the business precondition of the use case is
    // "no ENABLED policy", which a disabled row satisfies exactly.
    // ---------------------------------------------------------------

    @Test
    @Transactional
    void createFailsWhenSlaPolicyIsMissing() {
        // Raw SQL, and deliberately no repository load of the policy first: the
        // service lookup then reads the disabled row from the database instead of
        // a managed entity cached in this transaction's persistence context.
        int disabled = jdbcTemplate.update(
                "UPDATE sla_policy SET enabled = FALSE WHERE priority = ?", "P4_LOW");
        assertEquals(1, disabled, "seeded P4_LOW SLA policy is required for this test");

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

    // ---------------------------------------------------------------
    // D - List Tickets (pagination + filters)
    // ---------------------------------------------------------------

    private long createTicketViaApi(String title, String category, String priority) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tickets")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(title, "description", category, priority, requesterId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(created.getResponse().getContentAsString());
    }

    @Test
    void listDefaultsToPageZeroSizeTwenty() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void listHonoursCustomPageSizeAndIsDeterministic() throws Exception {
        createTicketViaApi("deterministic-1", "MISC", "P3_MEDIUM");
        long newest = createTicketViaApi("deterministic-2", "MISC", "P3_MEDIUM");

        // Fixed order createdAt DESC, id DESC -> the just-created ticket is first.
        mockMvc.perform(get("/api/v1/tickets").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(get("/api/v1/tickets").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(newest));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        createTicketViaApi("filter-status", "MISC", "P3_MEDIUM");
        mockMvc.perform(get("/api/v1/tickets").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status",
                        Matchers.everyItem(Matchers.is("OPEN"))));
    }

    @Test
    void listFiltersByPriority() throws Exception {
        createTicketViaApi("filter-prio", "MISC", "P1_CRITICAL");
        mockMvc.perform(get("/api/v1/tickets").param("priority", "P1_CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].priority",
                        Matchers.everyItem(Matchers.is("P1_CRITICAL"))));
    }

    @Test
    void listFiltersByStatusAndPriorityTogether() throws Exception {
        createTicketViaApi("filter-both", "MISC", "P4_LOW");
        mockMvc.perform(get("/api/v1/tickets")
                        .param("status", "OPEN").param("priority", "P4_LOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status",
                        Matchers.everyItem(Matchers.is("OPEN"))))
                .andExpect(jsonPath("$.content[*].priority",
                        Matchers.everyItem(Matchers.is("P4_LOW"))));
    }

    @Test
    void listRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TicketValidation"));
    }

    @Test
    void listRejectsZeroSize() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TicketValidation"));
    }

    @Test
    void listRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TicketValidation"));
    }

    @Test
    void listRejectsIllegalEnumValue() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidParameter"));
    }

    private long readTotalElements(MvcResult result) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("totalElements").asLong();
    }

    @Test
    void listReturnsEmptyContentArrayWhenNothingMatches() throws Exception {
        // REOPENED is a legitimate lifecycle state now, so no status/priority
        // filter may be assumed empty. The unrequestable page is therefore derived
        // from what the database reports for that very filter: with size=1 the last
        // valid index is totalElements-1, so page=totalElements is one past the end
        // and must answer 200 with an empty content array. If the filter matches
        // nothing, totalElements is 0 and this is the plain empty-result case.
        long matchingElements = readTotalElements(mockMvc.perform(
                        get("/api/v1/tickets")
                                .param("status", "REOPENED").param("priority", "P1_CRITICAL")
                                .param("page", "0").param("size", "1"))
                        .andExpect(status().isOk())
                        .andReturn());

        MvcResult beyondLastPage = mockMvc.perform(
                        get("/api/v1/tickets")
                                .param("status", "REOPENED").param("priority", "P1_CRITICAL")
                                .param("page", String.valueOf(matchingElements)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.size").value(1))
                .andReturn();

        assertEquals(matchingElements, readTotalElements(beyondLastPage));
    }

    // ---------------------------------------------------------------
    // E - PATCH basic info (title / description / category only)
    // ---------------------------------------------------------------

    private MvcResult patchTicket(long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/tickets/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void patchTitleOnlySucceedsAndBumpsVersion() throws Exception {
        long id = createTicketViaApi("patch-title", "MISC", "P3_MEDIUM");

        MvcResult result = patchTicket(id, "{\"title\":\"Patched title\",\"version\":0}");

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String body = result.getResponse().getContentAsString();
        assertNotNull(readId(body));
        assertEquals("Patched title",
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(body).get("title").asString());
        assertEquals(1,
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(body).get("version").asInt());

        // Response version equals the database-incremented version.
        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals(1, stored.getVersion());
        assertEquals("Patched title", stored.getTitle());
        assertEquals("MISC", stored.getCategory());

        // PATCH then GET verifies persistence.
        mockMvc.perform(get("/api/v1/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Patched title"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchDescriptionOnlyKeepsOtherFields() throws Exception {
        long id = createTicketViaApi("patch-desc", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"description\":\"Patched description\",\"version\":0}");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals("Patched description", stored.getDescription());
        assertEquals("patch-desc", stored.getTitle());
        assertEquals("MISC", stored.getCategory());
    }

    @Test
    void patchCategoryOnlyKeepsOtherFields() throws Exception {
        long id = createTicketViaApi("patch-cat", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"category\":\"HARDWARE\",\"version\":0}");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals("HARDWARE", stored.getCategory());
        assertEquals("patch-cat", stored.getTitle());
    }

    @Test
    void patchMultipleFieldsAtOnce() throws Exception {
        long id = createTicketViaApi("patch-multi", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id,
                "{\"title\":\"T9\",\"description\":\"D9\",\"category\":\"NETWORK\",\"version\":0}");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals("T9", stored.getTitle());
        assertEquals("D9", stored.getDescription());
        assertEquals("NETWORK", stored.getCategory());
        assertEquals(1, stored.getVersion());
    }

    @Test
    void patchExplicitNullTitleIsRejected() throws Exception {
        long id = createTicketViaApi("patch-null-t", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"title\":null,\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void patchExplicitNullDescriptionIsRejected() throws Exception {
        long id = createTicketViaApi("patch-null-d", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"description\":null,\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void patchExplicitNullCategoryIsRejected() throws Exception {
        long id = createTicketViaApi("patch-null-c", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"category\":null,\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void patchEmptyBodyIsRejected() throws Exception {
        long id = createTicketViaApi("patch-empty", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{}");
        // {} lacks the required version token -> bean validation 400.
        // The "version present but no business field" case is the empty-patch
        // test below (TicketValidation from UpdateTicketCommand).
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("ValidationError",
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(result.getResponse().getContentAsString()).get("error").asString());
    }

    @Test
    void patchVersionOnlyIsStillEmptyPatch() throws Exception {
        long id = createTicketViaApi("patch-vonly", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("TicketValidation",
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(result.getResponse().getContentAsString()).get("error").asString());
    }

    @Test
    void patchBlankTitleIsRejected() throws Exception {
        long id = createTicketViaApi("patch-blank", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"title\":\"   \",\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void patchOversizedTitleIsRejected() throws Exception {
        long id = createTicketViaApi("patch-long", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"title\":\"" + "T".repeat(201) + "\",\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void patchMissingTicketReturns404() throws Exception {
        MvcResult result = patchTicket(9_999_999_999L, "{\"title\":\"x\",\"version\":0}");
        assertEquals(404, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void patchWithStaleVersionReturns409AndKeepsSecondWriteOut() throws Exception {
        long id = createTicketViaApi("patch-stale", "MISC", "P3_MEDIUM");

        MvcResult first = patchTicket(id, "{\"title\":\"first write\",\"version\":0}");
        assertEquals(200, first.getResponse().getStatus(), first.getResponse().getContentAsString());

        MvcResult second = patchTicket(id, "{\"title\":\"stale write\",\"version\":0}");
        assertEquals(409, second.getResponse().getStatus(), second.getResponse().getContentAsString());
        assertEquals("TicketStaleRevision",
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(second.getResponse().getContentAsString()).get("error").asString());

        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals("first write", stored.getTitle());
        assertEquals(1, stored.getVersion());
    }

    @Test
    void patchWithForbiddenStatusFieldIsRejectedAndStateUnchanged() throws Exception {
        long id = createTicketViaApi("patch-forbidden", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"status\":\"CLOSED\",\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("InvalidPatchRequest",
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(result.getResponse().getContentAsString()).get("error").asString());

        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals(TicketStatus.OPEN, stored.getStatus());
        assertEquals(0, stored.getVersion());
    }

    @Test
    void patchUnknownTypoFieldIsRejected() throws Exception {
        long id = createTicketViaApi("patch-typo", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"titlle\":\"typo\",\"version\":0}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        // Unknown-field rejection is a WEB contract concern, not a domain one.
        assertEquals("InvalidPatchRequest",
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(result.getResponse().getContentAsString()).get("error").asString());

        TicketJpaEntity stored = ticketRepository.findById(id).orElseThrow();
        assertEquals("patch-typo", stored.getTitle());
    }

    @Test
    void patchWithoutVersionIsRejected() throws Exception {
        long id = createTicketViaApi("patch-nover", "MISC", "P3_MEDIUM");
        MvcResult result = patchTicket(id, "{\"title\":\"no version\"}");
        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }
}
