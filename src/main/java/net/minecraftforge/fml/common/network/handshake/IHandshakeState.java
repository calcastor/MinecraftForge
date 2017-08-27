package net.minecraftforge.fml.common.network.handshake;

import io.netty.channel.ChannelHandlerContext;

import java.util.function.Consumer;

import javax.annotation.Nullable;

public interface IHandshakeState<S> {
    /**
     * Accepts FML handshake message for this state, and if needed - switches to another handshake state
     * using the provided consumer.
     *
     * The consumer allows to set new state before sending any messages to avoid race conditions.
     */
    void accept(ChannelHandlerContext ctx, @Nullable FMLHandshakeMessage msg, Consumer<? super S> cons);
}
