package uz.duke.dungeon.stage;

import uz.duke.dungeon.gen.GeneratedDungeon;

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
 * @param difficulty  how hard the author thinks it is, on the author's own scale;
 *                    the game reads it and shows it but never acts on it
 * @param players     how many it was built for. One, today — there is no second
 *                    player in this game yet — but a stage outlives that and an
 *                    author who leaves it out is an author who guessed
 * @param seed        the seed the floor was cut from. Not decoration: the loot
 *                    table, the level-up cards and the floor's own theme are all
 *                    drawn from a run's seed, so a stage that did not carry one
 *                    would be the same rooms with different everything else
 * @param floor       the dungeon itself, exactly as the generator handed it over
 */
public record Stage(String id, String name, String description, int difficulty, int players,
        long seed, GeneratedDungeon floor) {
}
