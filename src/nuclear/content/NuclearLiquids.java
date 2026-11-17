package nuclear.content;

import arc.graphics.*;
import mindustry.type.*;

public class NuclearLiquids{
    public static Liquid steam;

    public static void load(){
        steam = new Liquid("steam", Color.valueOf("d9e6ec")){{
            gas = true;
            temperature = 0.9f;
            viscosity = 0.2f;
            heatCapacity = 0.3f;
        }};
    }
}
