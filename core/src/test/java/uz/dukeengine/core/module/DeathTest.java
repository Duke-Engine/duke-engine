package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A body keeps the blow that killed it, and its die modules and its death's event are told of it. */
class DeathTest {

    private static final DeathType EXPLODED = DeathType.of("EXPLODED");
    private static final ObjectId SHOOTER = new ObjectId(99);

    /** What a game's die module does: remember how it went. */
    static final class Witness extends Module implements DieModule {
        Death seen;

        Witness(GameObject owner) {
            super(owner);
        }

        @Override
        public void onDie(Death death) {
            seen = death;
        }
    }

    private static final class Logic extends GameLogic {
        Logic(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static Logic logic() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ThingTemplate.named("Soldier").module(new ActiveBody.Data(50f)).build());
        var logic = new Logic(factory);
        logic.init();
        return logic;
    }

    @Test
    void theKillingBlowIsKeptAndHandedToTheDieModuleAndTheEvent() {
        var logic = logic();
        var soldier = logic.createObject(logic.getThingFactory().findTemplate("Soldier"));
        var witness = new Witness(soldier);
        soldier.addModule(witness);

        soldier.getBody().damage(30f, DamageType.EXPLOSION, new Death(EXPLODED, SHOOTER));
        assertNull(soldier.getBody().getDeath(), "a blow that does not kill is not a death");
        soldier.getBody().damage(30f, DamageType.EXPLOSION, new Death(EXPLODED, SHOOTER));
        soldier.getBody().damage(30f, DamageType.NORMAL, new Death(DeathType.of("BURNED"), new ObjectId(7)));
        logic.update();

        assertSame(EXPLODED, witness.seen.type(), "the blow that killed it, not one that landed on the corpse");
        assertEquals(SHOOTER, witness.seen.killer());
        var died = logic.drainEvents().stream().filter(ObjectDied.class::isInstance).map(ObjectDied.class::cast)
                .findFirst().orElseThrow();
        assertSame(EXPLODED, died.deathType());
        assertEquals(SHOOTER, died.killer());
    }

    @Test
    void aDeathNobodyDealtIsANormalOneByNoOne() {
        var logic = logic();
        var soldier = logic.createObject(logic.getThingFactory().findTemplate("Soldier"));
        var witness = new Witness(soldier);
        soldier.addModule(witness);

        soldier.getBody().setHealth(0f);
        logic.update();

        assertSame(Death.NORMAL, witness.seen);
        assertNull(((ObjectDied) logic.drainEvents().getFirst()).killer());
    }

    @Test
    void aThingBroughtBackDiesOfWhatKilledItTheSecondTime() {
        var body = logic().createObject(ThingTemplate.named("Soldier").module(new ActiveBody.Data(50f)).build())
                .getBody();
        body.damage(60f, DamageType.NORMAL, new Death(EXPLODED, SHOOTER));
        body.heal(50f);
        assertNull(body.getDeath());

        body.damage(10f, DamageType.NORMAL, new Death(DeathType.of("BURNED"), SHOOTER));
        body.damage(60f, DamageType.NORMAL); // says nothing of how

        assertSame(Death.NORMAL, body.getDeath(), "not the explosion it came back from");
    }
}
