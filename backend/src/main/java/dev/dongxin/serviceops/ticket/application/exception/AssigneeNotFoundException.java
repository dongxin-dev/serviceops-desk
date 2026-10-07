package dev.dongxin.serviceops.ticket.application.exception;

/** The requested assignee does not exist in app_user. */
public class AssigneeNotFoundException extends TicketApplicationException {

    public AssigneeNotFoundException(Long assigneeId) {
        super("Assignee " + assigneeId + " does not exist");
    }
}
