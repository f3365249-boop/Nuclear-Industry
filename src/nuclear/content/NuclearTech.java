package nuclear.content;

import arc.util.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.Objectives.*;

public class NuclearTech{

    public static void load(){
        add(Blocks.laserDrill, NuclearBlocks.uraniumCentrifuge, new Produce(NuclearItems.rawUranium));
        add(Blocks.steamGenerator, NuclearBlocks.steamTurbine);
        add(Blocks.thoriumReactor, NuclearBlocks.uraniumReactor,
            new Research(NuclearBlocks.uraniumCentrifuge), new Research(NuclearBlocks.steamTurbine), new Produce(NuclearItems.uranium235));

        add(Blocks.container, NuclearBlocks.droneDepot);
        add(NuclearBlocks.droneDepot, NuclearBlocks.droneReceiver);
        add(NuclearBlocks.droneDepot, NuclearBlocks.droneStation);
        add(NuclearBlocks.droneStation, NuclearBlocks.droneFactory);
    }

    private static void add(UnlockableContent parent, UnlockableContent child, Objective... objectives){
        TechNode parentNode = parent.techNode;
        if(parentNode == null){
            Log.warn("[Nuclear Industry] No tech node for @, @ will not be in the tech tree.", parent.name, child.name);
            return;
        }
        TechNode node = new TechNode(parentNode, child, child.researchRequirements());
        node.objectives.addAll(objectives);
    }
}
