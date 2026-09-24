package uz.dukeengine.client3d;

/**
 * Which button selects and which commands — see {@link Duke3D#mouse}.
 *
 * <p>The client's own controls, not the simulation's: two players in one match may each have their own.
 */
public enum Mouse {

    /**
     * The left button selects and drags a box, the right commands: Warcraft's and StarCraft's, and what every game
     * here had before it could choose.
     */
    RIGHT_COMMANDS,

    /**
     * The reference's own default ({@code m_useAlternateMouse = FALSE}): the left button selects the player's own
     * things and commands what is selected everywhere else — a move on open ground, an attack on an enemy, the
     * context order on a container or a dock — and the right button lets the selection go. A left drag still boxes.
     */
    LEFT_COMMANDS;

    /**
     * Whether a left click — a click, not a drag — is an order for what is selected rather than a selection of what
     * it landed on.
     *
     * @param onOwnSelectable whether it landed on one of the player's own things that may be selected
     * @param ownSelected     whether any of the player's own things is selected
     */
    boolean leftClickOrders(boolean onOwnSelectable, boolean ownSelected) {
        return this == LEFT_COMMANDS && ownSelected && !onOwnSelectable;
    }

    /** Whether a right click, with nothing armed, lets the selection go rather than ordering it. */
    boolean rightClickLetsGo() {
        return this == LEFT_COMMANDS;
    }
}
