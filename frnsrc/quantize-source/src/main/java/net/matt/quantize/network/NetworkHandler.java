package net.matt.quantize.network;

import net.matt.quantize.Quantize;                       // your main‑mod class
import net.matt.quantize.network.packet.*;
import net.matt.quantize.utils.ResourceIdentifier;
import net.matt.quantize.utils.TeleportHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One‑stop place for every Quantize network packet.
 */
public final class NetworkHandler {

    /* ───────── channel boiler‑plate ───────── */

    private static final String PROTOCOL_VERSION = "1";
    public static final  SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceIdentifier("main_channel"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    private static int id = 0;          // running discriminator

    /* ───────── init  (call from FMLCommonSetup) ───────── */

    public static void init() {

        /* Example registrations ─ replace / extend with your own packets */
        register(MessageMosquitoMountPlayer.class,
                MessageMosquitoMountPlayer::write,
                MessageMosquitoMountPlayer::read,
                MessageMosquitoMountPlayer.Handler::handle);
        register(MessageMosquitoDismount.class,
                MessageMosquitoDismount::write,
                MessageMosquitoDismount::read,
                MessageMosquitoDismount.Handler::handle);
        register(MessageHurtMultipart.class,
                MessageHurtMultipart::write,   // or ::encode
                MessageHurtMultipart::read,    // or ctor(buf)
                MessageHurtMultipart.Handler::handle);
        register(MessageCrowDismount.class,
                MessageCrowDismount::write,
                MessageCrowDismount::read,
                MessageCrowDismount.Handler::handle);
        register(MessageCrowMountPlayer.class,
                MessageCrowMountPlayer::write,
                MessageCrowMountPlayer::read,
                MessageCrowMountPlayer.Handler::handle);
        register(MessageInteractMultipart.class,
                MessageInteractMultipart::write,
                MessageInteractMultipart::read,
                MessageInteractMultipart.Handler::handle);
        register(MessageKangarooEat.class,
                MessageKangarooEat::write,
                MessageKangarooEat::read,
                MessageKangarooEat.Handler::handle);
        register(MessageKangarooInventorySync.class,
                MessageKangarooInventorySync::write,
                MessageKangarooInventorySync::read,
                MessageKangarooInventorySync.Handler::handle);
        register(MessageSendVisualFlagFromServer.class,
                MessageSendVisualFlagFromServer::write,
                MessageSendVisualFlagFromServer::read,
                MessageSendVisualFlagFromServer.Handler::handle);
        register(MessageSetPupfishChunkOnClient.class,
                MessageSetPupfishChunkOnClient::write,
                MessageSetPupfishChunkOnClient::read,
                MessageSetPupfishChunkOnClient.Handler::handle);
        register(MessageStartDancing.class,
                MessageStartDancing::write,
                MessageStartDancing::read,
                MessageStartDancing.Handler::handle);
        register(MessageSwingArm.class,
                MessageSwingArm::write,
                MessageSwingArm::read,
                MessageSwingArm.Handler::handle);
        register(MessageSyncEntityPos.class,
                MessageSyncEntityPos::write,
                MessageSyncEntityPos::read,
                MessageSyncEntityPos.Handler::handle);
        register(MessageTarantulaHawkSting.class,
                MessageTarantulaHawkSting::write,
                MessageTarantulaHawkSting::read,
                MessageTarantulaHawkSting.Handler::handle);
        register(MessageUpdateEagleControls.class,
                MessageUpdateEagleControls::write,
                MessageUpdateEagleControls::read,
                MessageUpdateEagleControls.Handler::handle);
        register(MultipartEntityMessage.class,
                MultipartEntityMessage::write,
                MultipartEntityMessage::read,
                MultipartEntityMessage::handle);
        register(UpdateCaveBiomeMapTagMessage.class,
                UpdateCaveBiomeMapTagMessage::write,
                UpdateCaveBiomeMapTagMessage::read,
                UpdateCaveBiomeMapTagMessage::handle);
        register(MountedEntityKeyMessage.class,
                MountedEntityKeyMessage::write,
                MountedEntityKeyMessage::read,
                MountedEntityKeyMessage::handle);
        register(UpdateEffectVisualityEntityMessage.class,
                UpdateEffectVisualityEntityMessage::write,
                UpdateEffectVisualityEntityMessage::read,
                UpdateEffectVisualityEntityMessage::handle);
        register(DanceJukeboxMessage.class,
                DanceJukeboxMessage::write,
                DanceJukeboxMessage::read,
                DanceJukeboxMessage.Handler::handle);
        register(TeleportRequest.class,
                TeleportRequest::encode,
                TeleportRequest::decode,
                TeleportHandler::handle);
        register(AnimationMessage.class,
                AnimationMessage::write,
                AnimationMessage::read,
                AnimationMessage.Handler::handle);

        // …add the rest of your messages here …
    }

    /* ───────── helpers ───────── */

    private static <MSG> void register(Class<MSG> type,
                                       BiConsumer<MSG, FriendlyByteBuf> encoder,
                                       Function<FriendlyByteBuf, MSG> decoder,
                                       BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(id++, type, encoder, decoder, handler);
    }

    public static <MSG> void sendMSGToServer(MSG msg) {
        CHANNEL.sendToServer(msg);
    }

    public static <MSG> void sendMSGToPlayer(MSG msg, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static <MSG> void sendMSGToAll(MSG msg) {
        for (ServerPlayer p : ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayers()) {
            sendNonLocal(msg, p);
        }
    }

    /** helper used by sendToAll – avoids echoing back to sender for client‑only packets */
    public static <MSG> void sendNonLocal(MSG msg, ServerPlayer player) {
        CHANNEL.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    /* prevent instantiation */
    private NetworkHandler() {}
}
