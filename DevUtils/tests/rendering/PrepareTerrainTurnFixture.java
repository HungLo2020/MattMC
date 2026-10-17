import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Save a camera in an owned copied world so observation needs no setup teleport. */
public final class PrepareTerrainTurnFixture {
    public static void main(String[] args) throws Exception {
        CompoundTag pose = requestedPose(args);
        Path run = Path.of(args[0]).toRealPath();
        Path captures = Path.of("artifacts/graphics-captures").toRealPath();
        if (!run.startsWith(captures) || !Files.isRegularFile(run.resolve(".terrain-turn-fixture-owned")))
            throw new IllegalArgumentException("requires an explicitly owned graphics fixture copy");
        Path path = run.resolve("saves/Origin/level.dat");
        if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(run) || Files.size(path) > 8 * 1024 * 1024)
            throw new IllegalArgumentException("requires bounded local copied level.dat");
        Path original = run.resolve(".terrain-turn-original-level.dat");
        if (Files.exists(original)) throw new IllegalArgumentException("fixture is already prepared");
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(8 * 1024 * 1024));
        CompoundTag data = root.getCompound("Data").orElseThrow();
        CompoundTag player = data.getCompound("Player").orElseThrow();
        String before = player.getListOrEmpty("Pos") + " " + player.getListOrEmpty("Rotation");
        player.put("Pos", pose.get("Pos"));
        player.put("Rotation", pose.get("Rotation"));
        player.put("Motion", pose.get("Motion"));
        player.putInt("playerGameType", 3);
        player.putString("Dimension", "minecraft:overworld");
        player.putBoolean("OnGround", false);
        CompoundTag abilities = player.getCompoundOrEmpty("abilities");
        abilities.putBoolean("flying", true);
        abilities.putBoolean("mayfly", true);
        abilities.putBoolean("invulnerable", true);
        player.put("abilities", abilities);
        data.putLong("DayTime", 6000);
        data.putBoolean("raining", false);
        data.putBoolean("thundering", false);
        Files.copy(path, original);
        Path temporary = path.resolveSibling("level.dat.terrain-turn-prepared");
        NbtIo.writeCompressed(root, temporary);
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        CompoundTag check = NbtIo.readCompressed(path, NbtAccounter.create(8 * 1024 * 1024))
            .getCompound("Data").orElseThrow().getCompound("Player").orElseThrow();
        if (!check.getListOrEmpty("Pos").equals(pose.getListOrEmpty("Pos"))
                || !check.getListOrEmpty("Rotation").equals(pose.getListOrEmpty("Rotation"))
                || check.getInt("playerGameType").orElse(-1) != 3)
            throw new IllegalStateException("saved camera did not persist");
        System.out.println("saved spectator; before=" + before + "; after="
            + check.getListOrEmpty("Pos") + " " + check.getListOrEmpty("Rotation"));
    }

    private static CompoundTag requestedPose(String[] args) {
        if (args.length != 1 && !(args.length == 3 && args[1].equals("--pose")))
            throw new IllegalArgumentException("OWNED_COPIED_RUN [--pose X,Y,Z,YAW,PITCH]");
        String[] fields = (args.length == 1 ? "150.5,95,530.5,285,25" : args[2]).split(",", -1);
        if (fields.length != 5) throw new IllegalArgumentException("pose requires five numbers");
        double x = Double.parseDouble(fields[0]), y = Double.parseDouble(fields[1]), z = Double.parseDouble(fields[2]);
        float yaw = Float.parseFloat(fields[3]), pitch = Float.parseFloat(fields[4]);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                || y < -2048 || y > 2048 || pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("pose must be finite and within world/pitch bounds");
        CompoundTag pose = new CompoundTag();
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(x));
        position.add(DoubleTag.valueOf(y));
        position.add(DoubleTag.valueOf(z));
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(yaw));
        rotation.add(FloatTag.valueOf(pitch));
        ListTag motion = new ListTag();
        for (int axis = 0; axis < 3; axis++) motion.add(DoubleTag.valueOf(0));
        pose.put("Pos", position);
        pose.put("Rotation", rotation);
        pose.put("Motion", motion);
        return pose;
    }
}
