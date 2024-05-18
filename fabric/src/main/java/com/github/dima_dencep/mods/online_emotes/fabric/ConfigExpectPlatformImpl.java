/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package com.github.dima_dencep.mods.online_emotes.fabric;

import com.github.dima_dencep.mods.online_emotes.OnlineEmotes;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.net.URI;

@SuppressWarnings("unused")
@Config(name = OnlineEmotes.MOD_ID)
public class ConfigExpectPlatformImpl implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public long reconnectionDelay = 15L;

    public boolean replaceMessages = true;

    public boolean debug = false;

    @ConfigEntry.Gui.Excluded
    @ConfigEntry.Category("websocket")
    @ConfigEntry.Gui.RequiresRestart
    public String address = "wss://api.constructlegacy.ru:443/websockets/online-emotes";

    @ConfigEntry.Category("websocket")
    @ConfigEntry.Gui.RequiresRestart
    public int threads = 1;

    public static long reconnectionDelay() {
        return FabricOnlineEmotes.MOD_CONFIG.reconnectionDelay;
    }

    public static boolean replaceMessages() {
        return FabricOnlineEmotes.MOD_CONFIG.replaceMessages;
    }

    public static boolean debug() {
        return FabricOnlineEmotes.MOD_CONFIG.debug;
    }

    public static URI address() {
        return URI.create(FabricOnlineEmotes.MOD_CONFIG.address);
    }

    public static int threads() {
        return FabricOnlineEmotes.MOD_CONFIG.threads;
    }
}
