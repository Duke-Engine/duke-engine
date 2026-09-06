package uz.duke.core.network;

/**
 * Turns a {@link CommandPacket} into one wire line and back.
 *
 * <p>Lock-step ships only commands, so this is the engine's entire serialization
 * surface — and the one part of networking that must know the game's command
 * set. The engine therefore takes it as a plug: a game supplies the codec for
 * its own {@link uz.duke.core.message.Command} types (see
 * {@code uz.duke.rts.network.CommandCodec}).
 *
 * <p>Encodings must round-trip exactly. Floats in particular should go through
 * {@link Float#toString}/{@link Float#parseFloat}, which preserve every bit —
 * anything lossy desynchronises the peers it is meant to keep in step.
 */
public interface PacketCodec {

    /** Encode a packet as a single line, free of {@code \n}. */
    String encode(CommandPacket packet);

    /** Decode a line produced by {@link #encode}. */
    CommandPacket decode(String line);
}
