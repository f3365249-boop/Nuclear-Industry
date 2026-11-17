package nuclear.content;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.power.*;
import mindustry.world.draw.*;
import nuclear.world.blocks.drone.*;
import nuclear.world.blocks.drone.DroneFactory.*;
import nuclear.world.blocks.power.*;
import nuclear.world.blocks.production.*;

import static mindustry.type.ItemStack.with;

public class NuclearBlocks{
    public static Block oreUranium, uraniumCentrifuge, uraniumReactor, steamTurbine,
        droneStation, droneDepot, droneReceiver, droneFactory;

    public static void load(){
        oreUranium = new OreBlock("ore-uranium", NuclearItems.rawUranium);

        uraniumCentrifuge = new EnrichmentCentrifuge("uranium-centrifuge"){{
            requirements(Category.crafting, with(Items.lead, 100, Items.titanium, 80, Items.silicon, 80, Items.metaglass, 40));
            size = 2;
            health = 320;
            craftTime = 4f * 60f; //4 seconds per uranium
            itemCapacity = 10;

            consumeItem(NuclearItems.rawUranium, 1);
            consumePower(3f); //180 power units/second

            outputItem = new ItemStack(NuclearItems.uranium238, 1);
            enrichedItem = NuclearItems.uranium235;
            enrichChance = 0.1f;

            craftEffect = Fx.pulverizeMedium;
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-rotor"){{
                spinSprite = true;
                rotateSpeed = 6f;
            }});
        }};

        uraniumReactor = new UraniumReactor("uranium-reactor"){{
            requirements(Category.power, with(Items.lead, 400, Items.silicon, 300, Items.graphite, 300, Items.thorium, 200, Items.metaglass, 150));
            size = 4;
            health = 1400;
            itemCapacity = 25; //max uranium-235 inside, more = hotter
            liquidCapacity = 160f;
            fuelItem = NuclearItems.uranium235;
            outputLiquid = NuclearLiquids.steam;
        }};

        steamTurbine = new ConsumeGenerator("steam-turbine"){{
            requirements(Category.power, with(Items.copper, 120, Items.graphite, 80, Items.titanium, 100, Items.metaglass, 50, Items.silicon, 60));
            size = 3;
            health = 900;
            powerProduction = 2000f / 60f; //2000 power units/second
            consumeLiquid(NuclearLiquids.steam, 0.1f); //6 steam/second
            liquidCapacity = 30f;
            generateEffect = NuclearFx.steamVent;
            effectChance = 0.05f;
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-rotor"){{
                spinSprite = true;
                rotateSpeed = 3f;
            }});
        }};

        //---- drone network ----

        droneStation = new DroneStation("drone-station"){{
            requirements(Category.units, with(Items.copper, 80, Items.lead, 60, Items.silicon, 40));
            size = 2;
            health = 400;
        }};

        droneDepot = new DroneDepot("drone-depot"){{
            requirements(Category.units, with(Items.copper, 30, Items.lead, 20, Items.graphite, 10));
            size = 1;
            health = 120;
            itemCapacity = 50;
        }};

        droneReceiver = new DroneReceiver("drone-receiver"){{
            requirements(Category.units, with(Items.copper, 25, Items.lead, 15, Items.silicon, 10));
            size = 1;
            health = 120;
            itemCapacity = 50;
        }};

        droneFactory = new DroneFactory("drone-factory"){{
            requirements(Category.units, with(Items.copper, 150, Items.lead, 120, Items.silicon, 100, Items.titanium, 60));
            size = 3;
            health = 600;
            consumePower(1.5f); //90 power units/second while building

            plans.add(new DronePlan(NuclearUnits.workerDrone, 10f * 60f,
                with(Items.copper, 15, Items.lead, 10, Items.silicon, 10)));
            plans.add(new DronePlan(NuclearUnits.guardDrone, 18f * 60f,
                with(Items.copper, 20, Items.silicon, 25, Items.graphite, 15, Items.titanium, 10)));
            plans.add(new DronePlan(NuclearUnits.medicDrone, 18f * 60f,
                with(Items.lead, 20, Items.silicon, 25, Items.metaglass, 15, Items.titanium, 10)));
        }};
    }
}
