package dev.dongxin.serviceops.ticket.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generates ticket numbers in the approved V1 format:
 * {@code TKT-yyyyMMdd-XXXXXXXXXXXX} (25 chars, within VARCHAR(32)).
 *
 * <p>The random part is the first 12 hex characters of a UUID, upper-cased.
 * The database UNIQUE constraint on ticket_no remains the final guard.
 */
class TicketNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int RANDOM_PART_LENGTH = 12;

    private TicketNumberGenerator() {
    }

    static String next(OffsetDateTime createdAt) {
        String random = UUID.randomUUID().toString().replace("-", "")
                .substring(0, RANDOM_PART_LENGTH).toUpperCase();
        return "TKT-" + createdAt.format(DATE) + "-" + random;
    }
}
