package uz.dukeengine.dungeon.stage;

import uz.dukeengine.dungeon.gen.GeneratedDungeon;

/**
 * A dungeon that has stopped changing.
 *
 * <p>Duke Dungeon draws a new floor from a seed every run, which makes every run
 * a first look at somewhere. A stage is the other kind of level: the same rooms,
 * the same monsters in the same corners, every time — so that losing teaches
 * something and the second attempt is better than the first. It is the Warcraft
 * custom map rather than the roguelike descent.
 *
 * <p>What is frozen is exactly what the generator produced. A stage is a
 * {@link GeneratedDungeon} that was written to a file instead of being thrown
 * away at the end of the run, plus the handful of things a generated floor never
 * needed: a name, who it is for, and how hard it is meant to be. Nothing about
 * laying it out is different — {@code Spawner} cannot tell a floor that was drawn
 * a moment ago from one drawn last month, which is the whole reason to freeze the
 * generator's own output rather than invent a second kind of level.
 *
 * @param id          the stage's machine name — the word the file's own block is
 *                    headed with, and what a command line asks for
 * @param name        what the player is told it is called
 * @param description one line about it, for whoever is choosing
 * @param difficulty  <b>the depth it is fought at</b>, and so the whole of how
 *                    hard it is. Not a label: the game plays a stage at this
 *                    depth, and everything that makes a floor dangerous is
 *                    already written against depth — how much health and damage
 *                    its monsters carry, how many of them there are, which kinds
 *                    have appeared by then, which boss waits. It was a number the
 *                    game showed and never acted on, which made it a guess; now
 *                    "difficulty 7" means what the seventh floor of the descent
 *                    means, and an author can go and check. It may be deeper than
 *                    the descent itself ever gets, which is one of the reasons to
 *                    build a stage at all
 * @param players     how many it was built for. One, today — there is no second
 *                    player in this game yet — but a stage outlives that and an
 *                    author who leaves it out is an author who guessed
 * @param seed        the seed the floor was cut from. Not decoration: the loot
 *                    table and the floor's own theme are both drawn from a
 *                    run's seed, so a stage that did not carry one
 *                    would be the same rooms with different everything else
 * @param floor       the dungeon itself, exactly as the generator handed it over
 */
public record Stage(String id, String name, String description, int difficulty, int players,
        long seed, GeneratedDungeon floor) {
}
