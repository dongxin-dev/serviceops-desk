-- =============================================================================
-- V2__seed_sla_policies.sql
-- Seeds the 4 SLA policies required by the frozen Domain Model.
-- created_at / updated_at use standard PostgreSQL now().
-- =============================================================================

INSERT INTO sla_policy (
    name, priority, response_minutes, resolution_minutes, enabled, created_at, updated_at
) VALUES
    ('Critical SLA', 'P1_CRITICAL',  15,  120, TRUE, now(), now()),
    ('High SLA',     'P2_HIGH',      30,  240, TRUE, now(), now()),
    ('Medium SLA',   'P3_MEDIUM',   120,  480, TRUE, now(), now()),
    ('Low SLA',      'P4_LOW',      240, 1440, TRUE, now(), now());
