/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package org.redlance.dima_dencep.mods.online_emotes.forge;

import org.redlance.dima_dencep.mods.online_emotes.OnlineEmotes;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;

@Mod(OnlineEmotes.MOD_ID)
public class ForgeOnlineEmotes extends OnlineEmotes {
    public ForgeOnlineEmotes() {
        super.onInitializeClient();

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> proxy.connect());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> proxy.disconnect());
    }
}
