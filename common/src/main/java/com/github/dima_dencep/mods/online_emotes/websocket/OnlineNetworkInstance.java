/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package com.github.dima_dencep.mods.online_emotes.websocket;

import com.github.dima_dencep.mods.online_emotes.ConfigExpectPlatform;
import com.github.dima_dencep.mods.online_emotes.OnlineEmotes;
import com.github.dima_dencep.mods.online_emotes.client.FancyToast;
import com.github.dima_dencep.mods.online_emotes.utils.EmotePacketWrapper;
import com.github.dima_dencep.mods.online_emotes.utils.NettyObjectFactory;
import io.github.kosmx.emotes.PlatformTools;
import io.github.kosmx.emotes.api.proxy.AbstractNetworkInstance;
import io.github.kosmx.emotes.common.network.EmotePacket;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class OnlineNetworkInstance extends AbstractNetworkInstance implements WebSocket.Listener {
    public static final URI URI_ADDRESS = ConfigExpectPlatform.address();
    private ScheduledFuture<?> reconnectingFuture;
    private final WebSocket.Builder webSocketBuilder;
    protected HttpClient httpClient;
    protected WebSocket webSocket;

    public OnlineNetworkInstance() {
        if (!"ws".equals(URI_ADDRESS.getScheme()) && !"wss".equals(URI_ADDRESS.getScheme())) {
            throw new IllegalArgumentException("Unsupported protocol: " + URI_ADDRESS.getScheme());
        }

        this.httpClient = HttpClient.newBuilder()
                .executor(NettyObjectFactory.EXECUTOR_SERVICE)
                // .sslContext()
                .build();

        this.webSocketBuilder = this.httpClient
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(ConfigExpectPlatform.reconnectionDelay()));
    }

    public void connect() {
        if (isActive()) {
            OnlineEmotes.LOGGER.info("Aready connected!");

            sendOnlineEmotesConfig();

            return;
        }

        this.webSocketBuilder.buildAsync(URI_ADDRESS, this).thenAccept((e) -> {
            disconnectWebSocket();

            this.webSocket = e;

            startReconnecting();
            sendOnlineEmotesConfig();
        }).exceptionally((ex) -> {
            OnlineEmotes.LOGGER.error("Failed to connect", ex);

            startReconnecting();

            return null;
        });
    }

    @Override
    public boolean sendPlayerID() {
        return false;
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
        try {
            if (this.reconnectingFuture != null && !this.reconnectingFuture.isCancelled()) {
                OnlineEmotes.LOGGER.warn("What happened to the reconnector?");

                this.reconnectingFuture.cancel(true);
                this.reconnectingFuture = null;
            }
        } catch (Throwable th) {
            OnlineEmotes.LOGGER.error("Failed to stop reconnector:", th);
        }

        onClose(this.webSocket, WebSocket.NORMAL_CLOSURE, ""); // Display debug message

        if (this.webSocket == null)
            return;

        this.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "");
        this.webSocket.abort();
    }

    @Override
    public void disconnect() {
        disconnectWebSocket();
        super.disconnect();
    }

    public void startReconnecting() {
        if (this.reconnectingFuture != null)
            return;

        this.reconnectingFuture = NettyObjectFactory.EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {

            if (!isActive()) {
                OnlineEmotes.LOGGER.info("Try (re)connecting...");
                connect();
            }

        }, 0L, ConfigExpectPlatform.reconnectionDelay(), TimeUnit.SECONDS);
    }

    private static final Component DISCONNECTED = Component.translatable("online_emotes.messages.disconnected");
    private static final Component CONNECTED = Component.translatable("online_emotes.messages.connected");

    @Override
    public void onOpen(WebSocket webSocket) {
        FancyToast.sendMessage(true, false, this.reconnectingFuture != null, null, CONNECTED);

        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        FancyToast.sendMessage(true, this.reconnectingFuture != null, false, null, DISCONNECTED);

        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        OnlineEmotes.LOGGER.error("WebSocket exception:", error);

        WebSocket.Listener.super.onError(webSocket, error);
    }

    private final StringBuilder stringBuilder = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        this.stringBuilder.append(data);

        if (last) {
            String message = this.stringBuilder.toString();
            this.stringBuilder.setLength(0);

            FancyToast.sendMessage(null, PlatformTools.fromJson(message));
        }

        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final WritableByteChannel byteChannel = Channels.newChannel(this.byteArrayOutputStream);

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            this.byteChannel.write(data);
        } catch (IOException e) {
            OnlineEmotes.LOGGER.error("Failed to write!", e);
        }

        if (last) {
            byte[] bytes = this.byteArrayOutputStream.toByteArray();
            this.byteArrayOutputStream.reset();

            receiveMessage(bytes);
        }

        return WebSocket.Listener.super.onBinary(webSocket, data, last);
    }
}
