package com.mystic.mam.client;

import com.mystic.mam.Mam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Mam.MODID, dist = Dist.CLIENT)
public final class MamClient {
    public MamClient(ModContainer container) {
        // The NeoForge config UI, reached from the Config button in the mod list.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));

        // The same options inside the Sodium options menu, when Sodium Options API is available.
        if (ModList.get().isLoaded("sodium") && ModList.get().isLoaded("sodiumoptionsapi")) {
            try {
                MamSodiumOptions.register();
            } catch (Throwable missingOrRenamed) {
                // Sodium and Sodium Options API disagree on the option class layout; the NeoForge
                // config screen above still works.
                Mam.LOGGER.warn("Could not add the MAM options to the Sodium menu.", missingOrRenamed);
            }
        }
    }
}
