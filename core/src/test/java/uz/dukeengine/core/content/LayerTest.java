package uz.dukeengine.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.Binder;
import uz.dukeengine.core.data.DukeText;

/** A layer that renews says so in its block and hands it on to the client; one that does not say it hands on nothing. */
class LayerTest {

    @Test
    void aLayerThatRenewsSaysSoAndOneThatDoesNotSayItChangesNothing() {
        var effect = new Binder().bind(DukeText.parse("""
                Effect
                  Name = Stunned
                  Layers = [
                    Layer
                      Type = Aura
                      Renews = true
                    End,
                    Layer
                      Type = Aura
                      Renews = No
                    End,
                    Layer
                      Type = Aura
                      Follows = Yes
                    End
                  ]
                End
                """, "effects.duke").getFirst(), Effect.class);

        var renewing = effect.layers().get(0);
        assertEquals(Boolean.TRUE, renewing.renews());
        assertEquals("true", renewing.fields().get("renews"));
        assertEquals("false", effect.layers().get(1).fields().get("renews"));
        var unsaid = effect.layers().get(2);
        assertNull(unsaid.renews());
        assertFalse(unsaid.fields().containsKey("renews"), "unsaid, the client's own default");
        assertEquals("true", unsaid.fields().get("follows"));
    }
}
