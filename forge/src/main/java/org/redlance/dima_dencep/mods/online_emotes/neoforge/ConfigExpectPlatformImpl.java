/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package org.redlance.dima_dencep.mods.online_emotes.neoforge;

import org.redlance.dima_dencep.mods.online_emotes.OnlineEmotes;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigFileTypeHandler;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

@SuppressWarnings("unused")
public class ConfigExpectPlatformImpl {
    public static final Pair<ConfigExpectPlatformImpl, ModConfigSpec> CONFIG_SPEC_PAIR = new ModConfigSpec.Builder()
            .configure(ConfigExpectPlatformImpl::new);
    public final ModConfigSpec.ConfigValue<Long> reconnectionDelay;
    public final ModConfigSpec.BooleanValue replaceMessages;
    public final ModConfigSpec.BooleanValue debug;
    public final ModConfigSpec.IntValue threads;

    public ConfigExpectPlatformImpl(ModConfigSpec.Builder builder) {
        reconnectionDelay = builder
                .translation("text.autoconfig.online_emotes.option.reconnectionDelay")
                .comment("text.autoconfig.online_emotes.option.reconnectionDelay.@Tooltip")
                .define("reconnectionDelay", 15L);

        replaceMessages = builder
                .translation("text.autoconfig.online_emotes.option.replaceMessages")
                .define("replaceMessages", false);

        debug = builder
                .translation("text.autoconfig.online_emotes.option.debug")
                .define("debug", false);

        threads = builder
                .translation("text.autoconfig.online_emotes.option.threads")
                .worldRestart()
                .defineInRange("threads", 2, 1, Integer.MAX_VALUE);
    }

    static { // Early loading for config
        ModContainer activeContainer = ModList.get().getModContainerById(OnlineEmotes.MOD_ID).orElseThrow();
        ModConfigSpec configSpec = ConfigExpectPlatformImpl.CONFIG_SPEC_PAIR.getValue();

        ModConfig modConfig = new ModConfig(ModConfig.Type.CLIENT, configSpec, activeContainer, "online_emotes.toml");
        activeContainer.addConfig(modConfig);

        if (!configSpec.isLoaded()) {
            OnlineEmotes.LOGGER.warn("Config is not loaded?");

            configSpec.acceptConfig(
                    ConfigFileTypeHandler.TOML.reader(FMLPaths.CONFIGDIR.get())
                            .apply(modConfig)
            );
        }
    }

    public static long reconnectionDelay() {
        return CONFIG_SPEC_PAIR.getKey().reconnectionDelay.get();
    }

    public static boolean replaceMessages() {
        return CONFIG_SPEC_PAIR.getKey().replaceMessages.get();
    }

    public static boolean debug() {
        return CONFIG_SPEC_PAIR.getKey().debug.get();
    }

    public static int threads() {
        return CONFIG_SPEC_PAIR.getKey().threads.get();
    }
}
