package net.minecraftforge.fml.common.network.handshake;

import net.minecraftforge.fml.common.FMLLog;

import org.apache.logging.log4j.Level;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

public class HandshakeMessageHandler<S extends Enum<S> & IHandshakeState<S>> extends SimpleChannelInboundHandler<FMLHandshakeMessage> {
    private static final AttributeKey<IHandshakeState<?>> STATE = AttributeKey.valueOf("fml:handshake-state");
    private final AttributeKey<S> fmlHandshakeState;
    private final S initialState;
    private final S errorState;
    private final Class<S> stateType;

    @SuppressWarnings("unchecked")
    public HandshakeMessageHandler(Class<S> stateType)
    {
        fmlHandshakeState = (AttributeKey<S>) ((Object)STATE);
        initialState = Enum.valueOf(stateType, "START");
        errorState = Enum.valueOf(stateType, "ERROR");
        this.stateType = stateType;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FMLHandshakeMessage msg) throws Exception
    {
        S state = ctx.attr(fmlHandshakeState).get();
        FMLLog.fine(stateType.getSimpleName() + ": " + msg.toString(stateType) + "->" + state.getClass().getName().substring(state.getClass().getName().lastIndexOf('.')+1)+":"+state);
        state.accept(ctx, msg, s ->
        {
            FMLLog.fine("  Next: " + s.name());
            ctx.attr(fmlHandshakeState).set(s);
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        ctx.attr(fmlHandshakeState).set(initialState);
    }
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        S state = ctx.attr(fmlHandshakeState).get();
        FMLLog.fine(stateType.getSimpleName() + ": null->" + state.getClass().getName().substring(state.getClass().getName().lastIndexOf('.')+1)+":"+state);
        state.accept(ctx, null, s ->
        {
            FMLLog.fine("  Next: " + s.name());
            ctx.attr(fmlHandshakeState).set(s);
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        FMLLog.log(Level.ERROR, cause, "HandshakeMessageHandler exception");
        ctx.attr(fmlHandshakeState).set(errorState);
        super.exceptionCaught(ctx, cause);
    }
}
