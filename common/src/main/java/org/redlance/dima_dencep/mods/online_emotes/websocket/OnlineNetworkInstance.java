/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package org.redlance.dima_dencep.mods.online_emotes.websocket;

import org.redlance.dima_dencep.mods.online_emotes.ConfigExpectPlatform;
import org.redlance.dima_dencep.mods.online_emotes.OnlineEmotes;
import org.redlance.dima_dencep.mods.online_emotes.client.FancyToast;
import io.github.kosmx.emotes.api.proxy.AbstractNetworkInstance;
import io.github.kosmx.emotes.common.network.EmotePacket;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class OnlineNetworkInstance extends AbstractNetworkInstance {
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
            Math.max(ConfigExpectPlatform.threads(), 1),

            Thread.ofVirtual() // Requires java 21
                    .name(OnlineEmotes.MOD_ID)
                    .factory()
    );

    private static final URI URI_ADDRESS = URI.create("wss://api.redlance.org:443/websockets/online-emotes");
    private static final WebSocketListener LISTENER = new WebSocketListener();

    private final WebSocket.Builder webSocketBuilder;
    private final HttpClient httpClient;

    private ScheduledFuture<?> reconnectingFuture;
    private WebSocket webSocket;

    public OnlineNetworkInstance() {
        if (!"ws".equals(URI_ADDRESS.getScheme()) && !"wss".equals(URI_ADDRESS.getScheme())) {
            throw new IllegalArgumentException("Unsupported protocol: " + URI_ADDRESS.getScheme());
        }

        this.httpClient = HttpClient.newBuilder()
                .executor(EXECUTOR)
                // .sslContext()
                .build();

        this.webSocketBuilder = this.httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(
                        ConfigExpectPlatform.reconnectionDelay()
                ));
    }

    public void connect() {
        if (isActive()) {
            OnlineEmotes.LOGGER.warn("WebSocket already connected!");
            sendOnlineEmotesConfig(); // Reconfigure server
            return;
        }

        this.webSocketBuilder.buildAsync(URI_ADDRESS, LISTENER).thenAccept((e) ->
                this.webSocket = e
        ).whenCompleteAsync((unused, throwable) -> {
            if (throwable != null) {
                OnlineEmotes.LOGGER.error("Failed to connect!", throwable);
                startReconnector();
                return;
            }

            sendOnlineEmotesConfig(); // Configure server
            stopReconnector();
        }, EXECUTOR);
    }

    @Override
    public boolean sendPlayerID() {
        return true;
    }

    public void sendOnlineEmotesConfig() {
        sendC2SConfig(builder -> {
            try {
                sendMessage(builder, null);
            } catch (IOException e) {
                OnlineEmotes.LOGGER.fatal("Failed to send config", e);
            }
        });
    }

    @Override
    public boolean isActive() {
        return this.webSocket != null && !this.webSocket.isInputClosed() && !this.webSocket.isOutputClosed();
    }

    @Override
    public void sendMessage(EmotePacket.Builder builder, @Nullable UUID target) throws IOException {
        if (target != null) {
            builder.configureTarget(target);
        }

        EmotePacket writer = builder.build();

        this.webSocket.sendText(new EmotePacketWrapper(writer.write().array()).serializeToJson(), true);

        if (writer.data.emoteData != null && writer.data.emoteData.extraData.containsKey("song") && !writer.data.writeSong) {
            FancyToast.sendMessage(null, Component.translatable("emotecraft.song_too_big_to_send"));
        }
    }

    public void disconnectWebSocket() {
        this.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "")
                .thenAcceptAsync(WebSocket::abort);

        this.webSocket = null;
    }

    @Override
    public void disconnect() {
        disconnectWebSocket();
        super.disconnect();
    }

    protected void stopReconnector() {
        if (this.reconnectingFuture == null) {
            return;
        }

        OnlineEmotes.LOGGER.info("Reconnector stopped!");

        this.reconnectingFuture.cancel(true);
        this.reconnectingFuture = null;
    }

    protected void startReconnector() {
        if (this.reconnectingFuture != null) {
            return;
        }

        OnlineEmotes.LOGGER.info("Reconnector started!");

        this.reconnectingFuture = EXECUTOR.scheduleAtFixedRate(
                this::connect, 0L, ConfigExpectPlatform.reconnectionDelay(), TimeUnit.SECONDS
        );
    }
}
