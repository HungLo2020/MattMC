import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Value codec costs in one fresh JVM, compiled unchanged against a reference
 * and Current: item stacks (every item; every fourth named, with lore and
 * damage) decoded from and encoded to NBT, and text components decoded from
 * JSON. Reports, in milliseconds on one VALUE_TIMING line: the first
 * (cold) decode passes; the first encode pass (`stack_encode_first`, which
 * builds the tags) and the second, still pre-JIT, one
 * (`stack_encode_second`); and the warm per-pass averages.
 */
public final class ValueTiming {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Codec<ItemStack> stacks = (Codec<ItemStack>) ItemStack.class.getField("CODEC").get(null);
        Codec<Component> components = (Codec<Component>) net.minecraft.network.chat.ComponentSerialization.class.getField("CODEC").get(null);
        List<ItemStack> source = new ArrayList<>();
        int n = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            ItemStack stack = new ItemStack(item, 1);
            if (n++ % 4 == 0) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Item " + n).withStyle(net.minecraft.ChatFormatting.GOLD));
                stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("line"), Component.translatable("item.minecraft.stick"))));
                if (stack.isDamageableItem()) stack.set(DataComponents.DAMAGE, 3);
            }
            source.add(stack);
        }
        List<Tag> tags = new ArrayList<>();
        long first = System.nanoTime();
        for (ItemStack stack : source) tags.add(stacks.encodeStart(NbtOps.INSTANCE, stack).getOrThrow());
        first = System.nanoTime() - first;
        List<com.google.gson.JsonElement> texts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            texts.add(JsonParser.parseString("{\"text\":\"t" + i + "\",\"color\":\"red\",\"extra\":[\"a\",{\"translate\":\"k\",\"with\":[" + i + ",\"x\"]}],\"bold\":true}"));
        }
        long a = System.nanoTime();
        for (Tag tag : tags) stacks.parse(NbtOps.INSTANCE, tag).getOrThrow();
        long b = System.nanoTime();
        for (ItemStack stack : source) stacks.encodeStart(NbtOps.INSTANCE, stack).getOrThrow();
        long c = System.nanoTime();
        for (var text : texts) components.parse(JsonOps.INSTANCE, text).getOrThrow();
        long d = System.nanoTime();
        long decode = 0, encode = 0, text = 0;
        for (int r = 0; r < rounds; r++) {
            long s0 = System.nanoTime();
            for (Tag tag : tags) stacks.parse(NbtOps.INSTANCE, tag).getOrThrow();
            long s1 = System.nanoTime();
            for (ItemStack stack : source) stacks.encodeStart(NbtOps.INSTANCE, stack).getOrThrow();
            long s2 = System.nanoTime();
            for (var t : texts) components.parse(JsonOps.INSTANCE, t).getOrThrow();
            long s3 = System.nanoTime();
            decode += s1 - s0;
            encode += s2 - s1;
            text += s3 - s2;
        }
        System.out.printf("VALUE_TIMING stacks=%d stack_decode_cold=%.2f stack_encode_first=%.2f stack_encode_second=%.2f text_decode_cold=%.2f stack_decode=%.3f stack_encode=%.3f text_decode=%.3f%n",
            tags.size(), (b - a) / 1e6, first / 1e6, (c - b) / 1e6, (d - c) / 1e6, decode / 1e6 / rounds, encode / 1e6 / rounds, text / 1e6 / rounds);
    }
}
