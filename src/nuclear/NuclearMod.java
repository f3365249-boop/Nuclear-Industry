package nuclear;

import arc.util.*;
import mindustry.mod.*;
import nuclear.content.*;
import nuclear.drone.*;
import nuclear.radiation.*;
import nuclear.world.gen.*;

public class NuclearMod extends Mod{
    /** Internal mod name, must match "name" in mod.hjson. */
    public static final String modName = "nuclear-industry";

    /** Full content name (mod-prefixed). */
    public static String id(String name){
        return modName + "-" + name;
    }

    @Override
    public void loadContent(){
        Log.info("[Nuclear Industry] Loading content...");
        NuclearItems.load();
        NuclearLiquids.load();
        NuclearStatusEffects.load();
        NuclearUnits.load();
        NuclearBlocks.load();
        NuclearTech.load();
    }

    @Override
    public void init(){
        RadiationSystem.init();
        UraniumOreGenerator.init();
        DroneNet.init();
    }
}
