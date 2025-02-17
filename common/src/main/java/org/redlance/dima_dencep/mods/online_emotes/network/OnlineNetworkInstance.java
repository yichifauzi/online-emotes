/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package org.redlance.dima_dencep.mods.online_emotes.network;

import org.redlance.dima_dencep.mods.online_emotes.ConfigExpectPlatform;
import org.redlance.dima_dencep.mods.online_emotes.OnlineEmotes;
import org.redlance.dima_dencep.mods.online_emotes.client.FancyToast;
import org.redlance.dima_dencep.mods.online_emotes.netty.HandshakeHandler;
import org.redlance.dima_dencep.mods.online_emotes.netty.WebsocketHandler;
import org.redlance.dima_dencep.mods.online_emotes.utils.EmotePacketWrapper;
import org.redlance.dima_dencep.mods.online_emotes.utils.NettyObjectFactory;
import io.github.kosmx.emotes.api.proxy.AbstractNetworkInstance;
import io.github.kosmx.emotes.common.network.EmotePacket;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.ScheduledFuture;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class OnlineNetworkInstance extends AbstractNetworkInstance {
    private static final URI URI_ADDRESS = URI.create("wss://api.redlance.org:443/websockets/online-emotes");

    public final Bootstrap bootstrap = new Bootstrap();
    private ScheduledFuture<?> reconnectingFuture;
    public HandshakeHandler handshakeHandler;
    public Channel ch;

    public OnlineNetworkInstance() {
        if (!"ws".equals(URI_ADDRESS.getScheme()) && !"wss".equals(URI_ADDRESS.getScheme())) {
            throw new IllegalArgumentException("Unsupported protocol: " + URI_ADDRESS.getScheme());
        }

        this.bootstrap.group(NettyObjectFactory.newEventLoopGroup());
        this.bootstrap.channel(NettyObjectFactory.getSocketChannel());
        this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(@NotNull SocketChannel ch) throws SSLException {
                ChannelPipeline pipeline = ch.pipeline();

                if ("wss".equals(URI_ADDRESS.getScheme())) {
                    pipeline.addLast(SslContextBuilder.forClient().build()
                            .newHandler(ch.alloc(), URI_ADDRESS.getHost(), URI_ADDRESS.getPort())
                    );
                }

                pipeline.addLast("http-codec", new HttpClientCodec());
                pipeline.addLast("aggregator", new HttpObjectAggregator(ConfigExpectPlatform.maxContentLength()));
                pipeline.addLast("handshaker", OnlineNetworkInstance.this.handshakeHandler);
                pipeline.addLast("ws-handler", new WebsocketHandler(OnlineNetworkInstance.this));
            }
        });
    }

    public void connect() {
        stopReconnecting();

        this.reconnectingFuture = bootstrap.config().group().scheduleAtFixedRate(() -> {

            if (!isActive()) {
                OnlineEmotes.LOGGER.info("Try (re)connecting...");

                connectInternal();
            }

        }, 0L, ConfigExpectPlatform.reconnectionDelay(), TimeUnit.SECONDS);
    }

    private void connectInternal() {
        this.handshakeHandler = new HandshakeHandler(WebSocketClientHandshakerFactory.newHandshaker(URI_ADDRESS,
                WebSocketVersion.V13,
                null,
                false,
                EmptyHttpHeaders.INSTANCE,
                12800000
        ));

        ChannelFuture channelFuture = this.bootstrap.connect(URI_ADDRESS.getHost(), URI_ADDRESS.getPort());
        channelFuture.addListener((l) -> {
            if (l.isSuccess()) {
                disconnectNetty();

                this.ch = channelFuture.channel();

                this.handshakeHandler.handshakeFuture.addListener((e) -> {
                    if (e.isSuccess()) {
                        sendOnlineEmotesConfig();
                    } else {
                        OnlineEmotes.LOGGER.error("Failed to connect!", e.cause());
                    }
                });
            } else {
                OnlineEmotes.LOGGER.error("Failed to connect!", l.cause());
            }
        });
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
                OnlineEmotes.LOGGER.fatal(e);
            }
        });
    }

    @Override
    public boolean isActive() {
        return this.ch != null && this.ch.isActive();
    }

    @Override
    public void sendMessage(EmotePacket.Builder builder, @Nullable UUID target) throws IOException {
        builder.setSizeLimit(ConfigExpectPlatform.maxContentLength());

        if (target != null) {
            builder.configureTarget(target);
        }

        EmotePacket writer = builder.build();

        this.ch.writeAndFlush(new EmotePacketWrapper(writer.write().array()).toWebSocketFrame(), this.ch.voidPromise());

        if (writer.data.emoteData != null && writer.data.emoteData.extraData.containsKey("song") && !writer.data.writeSong) {
            FancyToast.sendMessage(null, Component.translatable("emotecraft.song_too_big_to_send"));
        }
    }

    public void disconnectNetty() {
        if (isActive()) {
            this.ch.writeAndFlush(new CloseWebSocketFrame(), this.ch.voidPromise());

            try {
                this.ch.close().awaitUninterruptibly();
            } catch (Throwable th) {
                OnlineEmotes.LOGGER.error("Failed to disconnect WebSocket:", th);
            }
        }
    }

    @Override
    public void disconnect() {
        stopReconnecting();
        disconnectNetty();
        super.disconnect();
    }

    public void stopReconnecting() {
        try {
            if (this.reconnectingFuture != null && !this.reconnectingFuture.isCancelled()) {
                OnlineEmotes.LOGGER.warn("What happened to the reconnector?");

                this.reconnectingFuture.cancel(true);
                this.reconnectingFuture = null;
            }
        } catch (Throwable th) {
            OnlineEmotes.LOGGER.error("Failed to stop reconnector:", th);
        }
    }

    public boolean isReconnectorAlive() {
        return this.reconnectingFuture != null;
    }
}
