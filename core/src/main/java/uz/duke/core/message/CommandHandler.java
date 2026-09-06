package uz.duke.core.message;

/**
 * Consumes commands drained from a {@link MessageStream}, ported in spirit from
 * SAGE's {@code GameMessageTranslator}/dispatch sites.
 *
 * <p>Implementations narrow {@link Command} to their own game's sealed command
 * hierarchy and then pattern-match on it, so the compiler still guarantees every
 * command type is handled.
 */
@FunctionalInterface
public interface CommandHandler {

    void handle(Command command);
}
