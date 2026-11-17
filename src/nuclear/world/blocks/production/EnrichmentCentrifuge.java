package nuclear.world.blocks.production;

import arc.math.*;
import mindustry.type.*;
import mindustry.world.blocks.production.*;

/**
 * Crafter with two possible outputs. {@code outputItem} (the common one) is the regular output;
 * with probability {@link #enrichChance} the {@link #enrichedItem} is produced instead.
 */
public class EnrichmentCentrifuge extends GenericCrafter{
    public Item enrichedItem;
    public float enrichChance = 0.1f;

    public EnrichmentCentrifuge(String name){
        super(name);
        buildType = EnrichmentCentrifugeBuild::new;
    }

    public class EnrichmentCentrifugeBuild extends GenericCrafterBuild{

        @Override
        public boolean shouldConsume(){
            return super.shouldConsume() && items.get(enrichedItem) < itemCapacity;
        }

        @Override
        public void craft(){
            consume();

            Item result = Mathf.chance(enrichChance) ? enrichedItem : outputItem.item;
            offload(result);

            craftEffect.at(x, y);
            progress %= 1f;
        }

        @Override
        public void dumpOutputs(){
            super.dumpOutputs();
            if(items.has(enrichedItem)){
                dump(enrichedItem);
            }
        }
    }
}
