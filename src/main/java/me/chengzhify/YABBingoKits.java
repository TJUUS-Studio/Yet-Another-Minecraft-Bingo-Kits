package me.chengzhify;

import me.chengzhify.kit.KitService;
import net.fabricmc.api.ModInitializer;

public class YABBingoKits implements ModInitializer {
    private final KitService kitService = new KitService();

    @Override
    public void onInitialize() {
        kitService.initialize();
    }
}
