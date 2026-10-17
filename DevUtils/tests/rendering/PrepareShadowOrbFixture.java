import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/**
 * Add ordinary persistent item/orb entities to an explicitly marked copied run.
 * Compile with Current's main runtime classpath; run with the native storage
 * library directory. This uses CPU NBT/storage APIs and no renderer or presenter.
 */
public final class PrepareShadowOrbFixture {
    private static final int[] ITEM_UUID = {0x53484144,5,0,1};
    private static final int[] ORB_UUID = {0x53484144,5,0,2};

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("COPIED_RUN X Y Z");
        Path run = Path.of(args[0]).toRealPath();
        if (!Files.isRegularFile(run.resolve(".shadow-orb-fixture-owned")))
            throw new IllegalArgumentException("requires an explicitly marked isolated copied run");
        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -64 || y > 319)
            throw new IllegalArgumentException("invalid bounded entity placement");
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var chunk = new ChunkPos(Math.floorDiv((int)Math.floor(x),16),Math.floorDiv((int)Math.floor(z),16));
        Path directory = run.resolve("saves/Origin/entities").toRealPath();
        Path regionPath = directory.resolve("r." + chunk.getRegionX() + "." + chunk.getRegionZ() + ".mca");
        var info = new RegionStorageInfo("goal5-shadow-orb-fixture",Level.OVERWORLD,"entities");
        int originalCount;
        try (var region = new RegionFile(info,regionPath,directory,true)) {
            CompoundTag root;
            try (var input = region.getChunkDataInputStream(chunk)) {
                root = input == null ? NbtUtils.addCurrentDataVersion(new CompoundTag()) : NbtIo.read(input);
            }
            var entities = root.getListOrEmpty("Entities");
            originalCount = entities.size();
            if (originalCount > 4094) throw new IllegalStateException("fixture entity bound exceeded");
            for (var entry : entities) {
                if (entry instanceof CompoundTag entity) {
                    var uuid = entity.getIntArray("UUID").orElse(new int[0]);
                    if (Arrays.equals(uuid,ITEM_UUID) || Arrays.equals(uuid,ORB_UUID))
                        throw new IllegalStateException("fixture already installed; refusing duplicate entities");
                }
            }
            String placement = String.format(Locale.ROOT,
                "Pos:[%.8fd,%.8fd,%.8fd],Motion:[0d,0d,0d],Rotation:[0f,0f],NoGravity:1b,Invulnerable:1b,",x,y,z);
            entities.add(TagParser.parseCompoundFully("{id:\"minecraft:item\",UUID:[I;1397244228,5,0,1],"
                + placement + "Age:0s,PickupDelay:32767s,Item:{id:\"minecraft:diamond_sword\",count:1,"
                + "components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":1}}}}"));
            entities.add(TagParser.parseCompoundFully("{id:\"minecraft:experience_orb\",UUID:[I;1397244228,5,0,2],"
                + placement + "Age:0s,Value:5s,Count:1}"));
            root.put("Entities",entities);
            root.store("Position",ChunkPos.CODEC,chunk);
            try (var output = region.getChunkDataOutputStream(chunk)) { NbtIo.write(root,output); }
        }
        // Reopen the completed file so the receipt proves persisted data rather
        // than merely the tag objects constructed above.
        try (var region = new RegionFile(info,regionPath,directory,true);
             var input = region.getChunkDataInputStream(chunk)) {
            if (input == null) throw new IllegalStateException("fixture entity chunk missing after write");
            var entities = NbtIo.read(input).getListOrEmpty("Entities");
            if (entities.size() != originalCount+2) throw new IllegalStateException("fixture entity count changed");
            var item = (CompoundTag)entities.get(originalCount);
            var orb = (CompoundTag)entities.get(originalCount+1);
            if (!Arrays.equals(item.getIntArray("UUID").orElseThrow(),ITEM_UUID)
                || !Arrays.equals(orb.getIntArray("UUID").orElseThrow(),ORB_UUID))
                throw new IllegalStateException("fixture entity ordering/identity changed");
            System.out.println("persisted item-before-orb chunk=" + chunk + " original_entities=" + originalCount
                + " position=" + x + "," + y + "," + z + " region=" + regionPath);
        }
    }
}
