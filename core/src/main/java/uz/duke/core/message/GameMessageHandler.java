package uz.duke.core.message;

/**
 * Consumes commands drained from a {@link MessageStream}, ported in spirit from
 * SAGE's {@code GameMessageTranslator}/dispatch sites.
 *
 * <p>Implementations typically pattern-match on the sealed {@link GameMessage}
 * hierarchy, so the compiler guarantees every command type is handled.
 */
@FunctionalInterface
public interface GameMessageHandler {

    void handle(GameMessage message);
}
