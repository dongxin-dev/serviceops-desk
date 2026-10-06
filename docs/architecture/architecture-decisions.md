# Architecture Decisions

## ADR-001: Modular Monolith

ServiceOps Desk V1 uses a modular monolith architecture instead of microservices.

### Reason

The current domain scale does not justify the operational complexity of microservices.

The system maintains clear module boundaries so that individual modules can be extracted later if required.

---

## ADR-002: Simplified RBAC

V1 uses a single primary role per user.

### Reason

The current access model is small and stable.

A full user-role-permission mapping model would introduce unnecessary complexity in the first version.

---

## ADR-003: SLA Deadline Snapshot

SLA deadlines are calculated and stored when a ticket is created.

### Reason

Historical tickets must not change when an SLA policy is modified later.

---

## ADR-004: Current Assignment and Assignment History

Ticket stores the current assignee.

TicketAssignment stores assignment history.

### Reason

This provides efficient current-state queries while preserving complete assignment history.

---

## ADR-005: Natural Time SLA

V1 calculates SLA using 24x7 natural time.

### Reason

Business calendars, holidays, and working-hour calculations are intentionally deferred to a future version.