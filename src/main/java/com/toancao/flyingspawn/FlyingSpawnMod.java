package com.toancao.flyingspawn;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FlyingSpawnMod implements ModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("FlyingSpawn");

    @Override
    public void onInitialize() {
        LOGGER.info("╔════════════════════════════════════╗");
        LOGGER.info("║   🚀 TC FLYINGSPAWN MOD LOADED! 🚀   ║");
        LOGGER.info("╚════════════════════════════════════╝");

        // Đăng ký Cloth Config — tự load file config/flyingspawn.toml
        FlyingSpawnConfig.register();

        // Đăng ký tick manager trước (phải sẵn sàng trước khi có entity)
        FlightTickManager.register();

        // Đăng ký spawn handler
        FlyingSpawnHandler.register();
    }
}
