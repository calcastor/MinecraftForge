package net.minecraftforge.fml.common.network.handshake;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage.ServerHello;
import net.minecraftforge.fml.common.network.internal.FMLMessage;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import net.minecraftforge.fml.common.registry.PersistentRegistryManager;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Packet handshake sequence manager- client side (responding to remote server)
 *
 * Flow:
 * 1. Wait for server hello. (START). Move to HELLO state.
 * 2. Receive Server Hello. Send customchannel registration. Send Client Hello. Send our modlist. Move to WAITINGFORSERVERDATA state.
 * 3. Receive server modlist. Send ack if acceptable, else send nack and exit error. Receive server IDs. Move to COMPLETE state. Send ack.
 *
 * @author cpw
 *
 */
enum FMLHandshakeClientState implements IHandshakeState<FMLHandshakeClientState>
{
    START
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            cons.accept(HELLO);
            NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
            dispatcher.clientListenForServerHandshake();
        }
    },
    HELLO
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            boolean isVanilla = msg == null;
            if (isVanilla)
            {
                cons.accept(DONE);
            }
            else
            {
                cons.accept(WAITINGSERVERDATA);
            }
            // write our custom packet registration, always
            ctx.writeAndFlush(FMLHandshakeMessage.makeCustomChannelRegistration(NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)));
            if (isVanilla)
            {
                NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                dispatcher.abortClientHandshake("VANILLA");
                // VANILLA login
                return;
            }

            ServerHello serverHelloPacket = (FMLHandshakeMessage.ServerHello)msg;
            FMLLog.info("Server protocol version %x", serverHelloPacket.protocolVersion());
            if (serverHelloPacket.protocolVersion() > 1)
            {
                // Server sent us an extra dimension for the logging in player - stash it for retrieval later
                NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                dispatcher.setOverrideDimension(serverHelloPacket.overrideDim());
            }
            ctx.writeAndFlush(new FMLHandshakeMessage.ClientHello()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            ctx.writeAndFlush(new FMLHandshakeMessage.ModList(Loader.instance().getActiveModList())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    },

    WAITINGSERVERDATA
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            String result = FMLNetworkHandler.checkModList((FMLHandshakeMessage.ModList) msg, Side.SERVER);
            if (result != null)
            {
                cons.accept(ERROR);
                NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                dispatcher.rejectHandshake(result);
                return;
            }
            if (!ctx.channel().attr(NetworkDispatcher.IS_LOCAL).get())
            {
                cons.accept(WAITINGSERVERCOMPLETE);
            }
            else
            {
                cons.accept(PENDINGCOMPLETE);
            }
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

        }
    },
    WAITINGSERVERCOMPLETE
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            FMLHandshakeMessage.RegistryData pkt = (FMLHandshakeMessage.RegistryData)msg;
            PersistentRegistryManager.GameDataSnapshot snap = ctx.channel().attr(NetworkDispatcher.FML_GAMEDATA_SNAPSHOT).get();
            if (snap == null)
            {
                snap = new PersistentRegistryManager.GameDataSnapshot();
                ctx.channel().attr(NetworkDispatcher.FML_GAMEDATA_SNAPSHOT).set(snap);
            }

            PersistentRegistryManager.GameDataSnapshot.Entry entry = new PersistentRegistryManager.GameDataSnapshot.Entry();
            entry.ids.putAll(pkt.getIdMap());
            entry.substitutions.addAll(pkt.getSubstitutions());
            entry.dummied.addAll(pkt.getDummied());
            snap.entries.put(pkt.getName(), entry);

            if (pkt.hasMore())
            {
                cons.accept(WAITINGSERVERCOMPLETE);
                FMLLog.fine("Received Mod Registry mapping for %s: %d IDs %d subs %d dummied", pkt.getName(), entry.ids.size(), entry.substitutions.size(), entry.dummied.size());
                return;
            }

            ctx.channel().attr(NetworkDispatcher.FML_GAMEDATA_SNAPSHOT).remove();

            //Do the remapping on the Client's thread in case things are reset while the client is running. We stall the network thread until this is finished which can cause the IO thread to time out... Not sure if we can do anything about that.
            final PersistentRegistryManager.GameDataSnapshot snap_f = snap;
            List<String> locallyMissing = Futures.getUnchecked(Minecraft.getMinecraft().addScheduledTask(() -> PersistentRegistryManager.injectSnapshot(snap_f, false, false)));
            if (!locallyMissing.isEmpty())
            {
                cons.accept(ERROR);
                NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                dispatcher.rejectHandshake("Fatally missing registry entries");
                FMLLog.severe("Failed to connect to server: there are %d missing blocks and items", locallyMissing.size());
                FMLLog.fine("Missing list: %s", locallyMissing);
                return;
            }
            cons.accept(PENDINGCOMPLETE);
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    },
    PENDINGCOMPLETE
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            cons.accept(COMPLETE);
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    },
    COMPLETE
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            cons.accept(DONE);
            NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
            dispatcher.completeClientHandshake();
            FMLMessage.CompleteHandshake complete = new FMLMessage.CompleteHandshake(Side.CLIENT);
            ctx.fireChannelRead(complete);
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    },
    DONE
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
            if (msg instanceof FMLHandshakeMessage.HandshakeReset)
            {
                cons.accept(HELLO);
                //Run the revert on the client thread in case things are currently running to prevent race conditions while rebuilding the registries.
                Minecraft.getMinecraft().addScheduledTask(PersistentRegistryManager::revertToFrozen);
            }
        }
    },
    ERROR
    {
        @Override
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super FMLHandshakeClientState> cons)
        {
        }
    };
}
