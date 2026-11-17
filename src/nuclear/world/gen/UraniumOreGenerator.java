package nuclear.world.gen;

import arc.*;
import arc.math.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;
import nuclear.content.*;
import mindustry.type.*;

/**
 * Adds uranium ore to procedurally generated Serpulo sectors the first time they are loaded.
 * Uses its own value noise, so it does not depend on planet generator internals.
 */
public class UraniumOreGenerator{
    private static final String tag = "nuclear-industry-ores";

    public static float scale = 22f;
    /** Higher = rarer. ~0.82 covers roughly 1.8% of the map. */
    public static float threshold = 0.82f;
    public static float minCoreDistanceTiles = 28f;

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> generate());
    }

    private static void generate(){
        if(Vars.net.client() || !Vars.state.isCampaign()) return;
        Sector sector = Vars.state.rules.sector;
        if(sector == null || sector.preset != null || sector.planet != Planets.serpulo) return;
        if(Vars.state.rules.tags.containsKey(tag)) return;
        Vars.state.rules.tags.put(tag, "1");

        int seed = sector.id * 31 + 7;
        Building core = Vars.state.rules.defaultTeam.core();

        for(int x = 0; x < Vars.world.width(); x++){
            for(int y = 0; y < Vars.world.height(); y++){
                Tile t = Vars.world.rawTile(x, y);
                if(t.block() != Blocks.air || t.overlay() != Blocks.air || t.floor().isLiquid) continue;
                if(core != null && Mathf.dst(x, y, core.tileX(), core.tileY()) < minCoreDistanceTiles) continue;

                if(noise(x, y, seed) > threshold){
                    t.setOverlay(NuclearBlocks.oreUranium);
                }
            }
        }
    }

    private static float noise(int x, int y, int seed){
        return 0.7f * valueNoise(x / scale, y / scale, seed) + 0.3f * valueNoise(x / (scale * 0.4f), y / (scale * 0.4f), seed + 101);
    }

    private static float hash(int x, int y, int seed){
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535f;
    }

    private static float smooth(float t){
        return t * t * (3f - 2f * t);
    }

    private static float valueNoise(float x, float y, int seed){
        int x0 = (int)Math.floor(x), y0 = (int)Math.floor(y);
        float fx = smooth(x - x0), fy = smooth(y - y0);
        float a = Mathf.lerp(hash(x0, y0, seed), hash(x0 + 1, y0, seed), fx);
        float b = Mathf.lerp(hash(x0, y0 + 1, seed), hash(x0 + 1, y0 + 1, seed), fx);
        return Mathf.lerp(a, b, fy);
    }
}
