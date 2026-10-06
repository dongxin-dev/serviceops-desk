# ServiceOps Desk Domain Model

## Overview

ServiceOps Desk is centered around the lifecycle of enterprise service tickets.

The V1 domain model contains six core domain entities:

- User
- Ticket
- TicketAssignment
- SlaPolicy
- Escalation
- AuditLog

The primary business flow is:

Requester creates a ticket
→ Ticket is assigned or claimed
→ Agent starts processing
→ SLA is monitored
→ Ticket may be escalated
→ Ticket is resolved
→ Ticket is closed
→ All important actions are audited

---

## 1. User

Represents a user participating in the ServiceOps Desk platform.

### Responsibilities

- Create tickets
- Handle tickets
- Assign tickets
- Trigger administrative operations
- Perform audited business actions

### Roles

- REQUESTER — 请求人
- AGENT — 处理工程师
- MANAGER — 服务经理
- ADMIN — 管理员

### Status

- ACTIVE — 启用
- DISABLED — 禁用

---

## 2. Ticket

Ticket is the aggregate root of the core service management domain.

### Responsibilities

- Maintain ticket lifecycle
- Maintain current priority
- Maintain current assignee
- Track SLA deadlines
- Track response and resolution timestamps
- Track escalation level
- Prevent illegal status transitions

### Priority

- P1_CRITICAL — 紧急
- P2_HIGH — 高
- P3_MEDIUM — 中
- P4_LOW — 低

### Status

- OPEN — 待处理
- ASSIGNED — 已分配
- IN_PROGRESS — 处理中
- RESOLVED — 已解决
- CLOSED — 已关闭
- REOPENED — 重新打开

---

## 3. TicketAssignment

Represents ticket assignment history.

Ticket stores the current assignee, while TicketAssignment preserves historical assignment records.

### Assignment Types

- ASSIGN — 管理员分配
- CLAIM — 工程师主动领取
- REASSIGN — 重新分配

---

## 4. SlaPolicy

Defines SLA rules based on ticket priority.

### Responsibilities

- Define response deadline duration
- Define resolution deadline duration
- Enable or disable SLA policies

Ticket stores calculated SLA deadlines so that later policy changes do not modify historical ticket expectations.

---

## 5. Escalation

Represents an escalation triggered by SLA timeout or manual intervention.

### Trigger Types

- SLA_RESPONSE_TIMEOUT — 响应超时
- SLA_RESOLUTION_TIMEOUT — 解决超时
- MANUAL — 人工升级

---

## 6. AuditLog

Records important business actions for traceability and compliance.

### Example Actions

- TICKET_CREATED
- TICKET_ASSIGNED
- TICKET_CLAIMED
- TICKET_REASSIGNED
- STATUS_CHANGED
- PRIORITY_CHANGED
- TICKET_ESCALATED
- TICKET_REOPENED
- TICKET_CLOSED

---

## Domain Relationships

- User 1:N Ticket
- Ticket N:1 SlaPolicy
- Ticket 1:N TicketAssignment
- Ticket 1:N Escalation
- Ticket 1:N AuditLog

---

## V1 Design Decisions

The first version intentionally avoids:

- Multi-role permission mapping
- Organization and department hierarchy
- Multi-tenancy
- Ticket comments
- Attachments
- Notification center
- Workflow engine
- Business calendar

These capabilities can be introduced when required by actual business complexity.