package leashedcowfixture;

import java.util.List;

/** Bootstrap-visible semantic fixture hook; no game imports or GPU state. */
public final class LeashedCowFixtureHook {
    public static void attach(Object entity) {
        if (!"cow".equals(System.getProperty("mattmc.dev.rustGalWorldEntityLeash.scenario"))
                || !entity.getClass().getName().equals("net.minecraft.world.entity.animal.Cow")) return;
        try {
            Object level = entity.getClass().getMethod("level").invoke(entity);
            Object server = level.getClass().getMethod("getServer").invoke(level);
            Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
            List<?> players = (List<?>)playerList.getClass().getMethod("getPlayers").invoke(playerList);
            if (players.size() != 1) throw new IllegalStateException("Copied leash fixture requires exactly one server player");
            Object holder = players.getFirst();
            // Current's fixed-camera helper moves the integrated-server player
            // before spawning. Frozen moves only its client camera. Supply the
            // same disposable server-player pose, otherwise normal leash
            // physics snaps against the saved position before capture.
            String prefix = "mattmc.dev.deterministicCameraCapture.";
            double x = Double.parseDouble(System.getProperty(prefix + "fixedX"));
            double y = Double.parseDouble(System.getProperty(prefix + "fixedY"));
            double z = Double.parseDouble(System.getProperty(prefix + "fixedZ"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalStateException("Copied leash fixture requires a finite fixed camera pose");
            holder.getClass().getMethod("setPos", double.class, double.class, double.class).invoke(holder, x, y, z);
            holder.getClass().getMethod("setDeltaMovement", double.class, double.class, double.class).invoke(holder, 0.0, 0.0, 0.0);
            Class<?> entityType = entity.getClass().getClassLoader().loadClass("net.minecraft.world.entity.Entity");
            entity.getClass().getMethod("setLeashedTo", entityType, boolean.class).invoke(entity, holder, true);
            System.out.println("[Cow leash fixture] server_player_pose=" + x + "," + y + "," + z + " attached before spawn cow_uuid="
                    + entity.getClass().getMethod("getUUID").invoke(entity) + " holder_uuid="
                    + holder.getClass().getMethod("getUUID").invoke(holder));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Copied cow leash fixture failed", failure);
        }
    }
}
