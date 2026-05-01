package is.pig.minecraft.inventory.integration;
import is.pig.minecraft.api.*;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import is.pig.minecraft.inventory.config.PiggyConfigScreenFactory;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PiggyConfigScreenFactory::create;
    }
}