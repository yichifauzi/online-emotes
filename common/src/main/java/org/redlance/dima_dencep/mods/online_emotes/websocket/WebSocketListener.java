package org.redlance.dima_dencep.mods.online_emotes.websocket;

import io.github.kosmx.emotes.PlatformTools;
import net.minecraft.network.chat.Component;
import org.redlance.dima_dencep.mods.online_emotes.ConfigExpectPlatform;
import org.redlance.dima_dencep.mods.online_emotes.OnlineEmotes;
import org.redlance.dima_dencep.mods.online_emotes.client.FancyToast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CompletionStage;

public class WebSocketListener implements WebSocket.Listener {
    private static final Component CONNECTED = Component.translatable("online_emotes.messages.connected");

    @Override
    public void onOpen(WebSocket webSocket) {
        if (ConfigExpectPlatform.debug()) {
            FancyToast.sendMessage(CONNECTED);
        }

        OnlineEmotes.proxy.stopReconnector();

        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (ConfigExpectPlatform.debug()) {
            FancyToast.sendMessage(
                    Component.translatable("online_emotes.messages.disconnected", reason)
            );
        }

        OnlineEmotes.proxy.startReconnector();

        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        OnlineEmotes.LOGGER.error("WebSocket exception!", error);

        FancyToast.sendMessage( // Needs debug?
                Component.translatable("online_emotes.messages.disconnected", error.getMessage())
        );

        OnlineEmotes.proxy.startReconnector();

        WebSocket.Listener.super.onError(webSocket, error);
    }

    private final StringBuilder stringBuilder = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        this.stringBuilder.append(data);
        if (last) {
            String message = this.stringBuilder.toString();
            this.stringBuilder.setLength(0);

            FancyToast.sendMessage(PlatformTools.fromJson(message));
        }

        return WebSocket.Listener.super.onText(webSocket, data, true);
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

            OnlineEmotes.proxy.receiveMessage(bytes);
        }

        return WebSocket.Listener.super.onBinary(webSocket, data, true);
    }
}
