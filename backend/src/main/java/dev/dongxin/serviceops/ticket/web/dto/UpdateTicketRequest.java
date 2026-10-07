package dev.dongxin.serviceops.ticket.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.dongxin.serviceops.ticket.application.UpdateTicketCommand;
import dev.dongxin.serviceops.ticket.web.exception.InvalidTicketPatchRequestException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict allow-list PATCH body. Deliberately a mutable class (not a record) so
 * the rejection mechanisms can live ON THIS DTO only - the global ObjectMapper
 * configuration is never touched.
 *
 * <p>Semantics:
 * <ul>
 *   <li>field absent            -> setter not called, value stays null = "keep";</li>
 *   <li>field explicit null     -> {@code Nulls.FAIL} aborts deserialization
 *       (surfaced as HttpMessageNotReadableException -> 400), never silently ignored;</li>
 *   <li>unknown field (typo or forbidden, e.g. status) -> captured by
 *       {@link JsonAnySetter} and rejected via {@link #toCommand(Long)} -> 400
 *       InvalidPatchRequest (a web contract violation, not a domain invariant);</li>
 *   <li>{} or version-only body -> UpdateTicketCommand constructor rejects the
 *       empty patch -> 400.</li>
 * </ul>
 * version is the optimistic-lock token, not a business field: @NotNull + @Min(0),
 * validated before the command is even built.
 */
public class UpdateTicketRequest {

    private String title;
    private String description;
    private String category;

    @NotNull(message = "version is required (optimistic-lock token)")
    @Min(value = 0, message = "version must be >= 0")
    private Integer version;

    private final List<String> unknownFields = new ArrayList<>();

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCategory(String category) {
        this.category = category;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonAnySetter
    public void captureUnknownField(String name, Object value) {
        unknownFields.add(name);
    }

    /** Builds the application command, rejecting any non-allow-listed field. */
    public UpdateTicketCommand toCommand(Long id) {
        if (!unknownFields.isEmpty()) {
            throw new InvalidTicketPatchRequestException(
                    "Unknown or forbidden field(s) in PATCH body: " + String.join(", ", unknownFields));
        }
        return new UpdateTicketCommand(id, title, description, category, version);
    }
}
