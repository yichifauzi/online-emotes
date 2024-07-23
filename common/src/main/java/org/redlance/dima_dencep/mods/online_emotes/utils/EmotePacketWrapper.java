/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package org.redlance.dima_dencep.mods.online_emotes.utils;

import io.github.kosmx.emotes.main.config.ClientSerializer;
import io.github.kosmx.emotes.server.config.Serializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public class EmotePacketWrapper {
    public final byte[] emotePacket;

    @Nullable
    public String playerName;
    @Nullable
    public UUID playerUUID;
    @Nullable
    public String serverAddress;

    public EmotePacketWrapper(byte[] emotePacket) {
        this.emotePacket = emotePacket;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.playerName = player.getScoreboardName();
            this.playerUUID = player.getUUID();

            Connection connection = player.connection.getConnection();
            if (!connection.isMemoryConnection()) {
                this.serverAddress = getIP(connection.getRemoteAddress());
            }
        }

        if (Serializer.serializer == null) {
            new ClientSerializer().initializeSerializer();
        }
    }

    public String serializeToJson() {
        return Serializer.serializer.toJson(this);
    }

    private static String getIP(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getAddress().getHostAddress();
        }

        return address.toString();
    }
}
