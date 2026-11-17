package nuclear.content;

import mindustry.type.*;

public class NuclearStatusEffects{
    public static StatusEffect radiation, droneCrippled;

    /**
     * Marker effect. The actual damage (2% max health per 2 seconds)
     * is applied by {@link nuclear.radiation.RadiationSystem}.
     */
    public static void load(){
        radiation = new StatusEffect("radiation"){{
            color = NuclearFx.radColor;
            effect = NuclearFx.radiationSpark;
            effectChance = 0.1f;
        }};

        //applied by the drone AI to drones below 6 hp
        droneCrippled = new StatusEffect("drone-crippled"){{
            speedMultiplier = 0.2f;
            show = false;
        }};
    }
}
