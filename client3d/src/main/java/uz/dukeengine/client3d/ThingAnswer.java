package uz.dukeengine.client3d;

import uz.dukeengine.core.view.CommandButton;

/**
 * How an order given on a thing is answered: the mark it wears there and the order the selection's voices answer.
 * Worked out apart from the drawing and the sounds, so that a click giving the game's word on a thing and a press of a
 * button the game answers as that word are answered alike.
 *
 * @param mark  what is laid at the thing — a ring round it, or a move's arrowheads — or null for nothing
 * @param flash whether the thing is flashed as it is when it is selected
 * @param voice the order the first of the selection answers ({@code ordered.<voice>.<template>}), or null for none
 */
record ThingAnswer(OrderMarkers.Kind mark, boolean flash, String voice) {

    /**
     * An order the game names by {@code word}, given on a thing: marked as {@link Visuals#wordMark} says — the ring of
     * the game's own orders, in its colour, or a move's arrowheads where its look rings none, the thing flashed, or
     * nothing — and voiced as {@link Visuals#orderAnswer} says.
     */
    static ThingAnswer toTheWord(String word, Visuals visuals) {
        var voice = visuals.orderAnswerFor(word);
        return switch (visuals.wordMarkFor(word)) {
            case MARK -> new ThingAnswer(visuals.getOrderMark().ringsContextOrders()
                    ? OrderMarkers.Kind.CONTEXT : OrderMarkers.Kind.MOVE, false, voice);
            case FLASH -> new ThingAnswer(null, true, voice);
            case NONE -> new ThingAnswer(null, false, voice);
        };
    }

    /**
     * A press of an armed button on a thing: answered as the game's word the button names ({@link
     * CommandButton#answeredAs()}), and a button naming none as an ability aimed at an enemy is — the attack's ring,
     * and the button's own name voiced.
     */
    static ThingAnswer toThePress(CommandButton button, Visuals visuals) {
        return button.answeredAs() == null
                ? new ThingAnswer(OrderMarkers.Kind.ATTACK, false, button.id())
                : toTheWord(button.answeredAs(), visuals);
    }
}
