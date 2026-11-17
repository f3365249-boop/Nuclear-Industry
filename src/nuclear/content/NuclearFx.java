package nuclear.content;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.*;

public class NuclearFx{
    public static final Color radColor = Color.valueOf("7dff4a");

    /** Floating particle inside radiation zones. */
    public static final Effect radiationParticle = new Effect(90f, e -> {
        Draw.color(radColor);
        Draw.alpha(0.5f * e.fslope());
        Fill.circle(e.x + Mathf.sin(e.fin() * 6f, 3f), e.y + e.fin() * 10f, 1.4f);
    });

    /** Small spark on irradiated units. */
    public static final Effect radiationSpark = new Effect(35f, e -> {
        Draw.color(radColor, Color.white, e.fin());
        Draw.alpha(e.fout());
        Fill.circle(e.x, e.y, 1.5f * e.fout());
    });

    /** Steam puff. */
    public static final Effect steamVent = new Effect(45f, e -> {
        Draw.color(Color.lightGray, Color.white, e.fin());
        Draw.alpha(0.5f * e.fout());
        Angles.randLenVectors(e.id, 3, 8f * e.finpow(), (x, y) -> Fill.circle(e.x + x, e.y + y, 2.5f * e.fout() + 0.5f));
    });

    /** Expanding green flash when a zone appears. rotation = radius in world units. */
    public static final Effect radiationWave = new Effect(120f, 4000f, e -> {
        Draw.color(radColor);
        Draw.alpha(0.3f * e.fout());
        Fill.circle(e.x, e.y, e.rotation * e.finpow());
    });
}
