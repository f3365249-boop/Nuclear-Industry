package nuclear.content;

import arc.graphics.*;
import mindustry.type.*;

public class NuclearItems{
    public static Item rawUranium, uranium235, uranium238;

    public static void load(){
        //hardness 4: only laser / impact drills can mine it
        rawUranium = new Item("uranium-raw", Color.valueOf("8a9a6c")){{
            hardness = 4;
            cost = 1.4f;
            radioactivity = 0.3f;
        }};

        uranium235 = new Item("uranium-235", Color.valueOf("8dff4a")){{
            cost = 1.8f;
            radioactivity = 1.5f;
        }};

        uranium238 = new Item("uranium-238", Color.valueOf("4d7a3a")){{
            cost = 1.2f;
            radioactivity = 0.5f;
        }};
    }
}
