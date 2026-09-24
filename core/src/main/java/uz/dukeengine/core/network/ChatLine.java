package uz.dukeengine.core.network;

import java.util.List;

/**
 * A line one player typed to others — everyone, allies, a chosen few — carried beside the match and never in it: not
 * a command, not replayed, not in any checksum, and no frame waits for one. The sender hears its own line too, as the
 * reference echoes it.
 *
 * @param sender     the player who said it
 * @param recipients the players it is for, in index order
 * @param text       what was said, any script
 */
public record ChatLine(int sender, List<Integer> recipients, String text) implements NetMessage {

    public ChatLine {
        recipients = recipients == null ? List.of() : recipients.stream().distinct().sorted().toList();
        text = text == null ? "" : text;
    }

    /** Whether {@code player} hears it: one it is for, or the one who said it. */
    public boolean reaches(int player) {
        return player == sender || recipients.contains(player);
    }
}
