package net.minecraftforge.fml.common.network.handshake;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLMessage;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import net.minecraftforge.fml.common.registry.PersistentRegistryManager;
import net.minecraftforge.fml.relauncher.Side;

enum FMLHandshakeServerState implements IHandshakeState<FMLHandshakeServerState>
{
    START
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeServerState> cons)
        {
            cons.accept(HELLO);
            NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
            int overrideDim = dispatcher.serverInitiateHandshake();
            ctx.writeAndFlush(FMLHandshakeMessage.makeCustomChannelRegistration(NetworkRegistry.INSTANCE.channelNamesFor(Side.SERVER))).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            ctx.writeAndFlush(new FMLHandshakeMessage.ServerHello(overrideDim)).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    },
    HELLO
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeServerState> cons)
        {
            // Hello packet first
            if (msg instanceof FMLHandshakeMessage.ClientHello)
            {
                FMLLog.info("Client protocol version %x", ((FMLHandshakeMessage.ClientHello)msg).protocolVersion());
                return;
            }

            FMLHandshakeMessage.ModList client = (FMLHandshakeMessage.ModList)msg;
            NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
            dispatcher.setModList(client.modList());
            FMLLog.info("Client attempting to join with %d mods : %s", client.modListSize(), client.modListAsString());
            String modRejections = FMLNetworkHandler.checkModList(client, Side.CLIENT);
            if (modRejections != null)
            {
                cons.accept(ERROR);
                dispatcher.rejectHandshake(modRejections);
                return;
            }
            cons.accept(WAITINGCACK);
            ctx.writeAndFlush(new FMLHandshakeMessage.ModList(Loader.instance().getActiveModList()));
        }
    },
    WAITINGCACK
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeServerState> cons)
        {
            cons.accept(COMPLETE);
            if (!ctx.channel().attr(NetworkDispatcher.IS_LOCAL).get())
            {
                PersistentRegistryManager.GameDataSnapshot snapshot = PersistentRegistryManager.takeSnapshot();
                Iterator<Map.Entry<ResourceLocation, PersistentRegistryManager.GameDataSnapshot.Entry>> itr = snapshot.entries.entrySet().iterator();
                while (itr.hasNext())
                {
                    Entry<ResourceLocation, PersistentRegistryManager.GameDataSnapshot.Entry> e = itr.next();
                    ctx.writeAndFlush(new FMLHandshakeMessage.RegistryData(itr.hasNext(), e.getKey(), e.getValue())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
            }
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            NetworkRegistry.INSTANCE.fireNetworkHandshake(ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get(), Side.SERVER);
        }
    },
    COMPLETE
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeServerState> cons)
        {
            cons.accept(DONE);
            // Poke the client
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            FMLMessage.CompleteHandshake complete = new FMLMessage.CompleteHandshake(Side.SERVER);
            ctx.fireChannelRead(complete);
        }
    },
    DONE
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeServerState> cons)
        {
        }
    },
    ERROR
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeServerState> cons)
        {
        }
    };
}
