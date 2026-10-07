package dev.dongxin.serviceops.ticket.application.exception;

/** The acting user of an assignment does not exist in app_user. */
public class ActorNotFoundException extends TicketApplicationException {

    public ActorNotFoundException(Long actorId) {
        super("Actor " + actorId + " does not exist");
    }
}
