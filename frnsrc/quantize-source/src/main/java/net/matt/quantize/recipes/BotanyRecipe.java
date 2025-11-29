package net.matt.quantize.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON shape:
 *
 * {
 *   "type": "quantize:botany_pot",
 *   "seed": { "item": "minecraft:oak_sapling" },      // Ingredient (item, tag…)
 *   "soil": { "item": "minecraft:grass_block" },       // Ingredient (item, tag…)
 *   "growth_ticks": 1200,                               // optional (default 1200)
 *   "drops": [
 *     { "output": { "item": "minecraft:apple" },   "chance": 0.5, "min": 1, "max": 1 },
 *     { "output": { "item": "minecraft:oak_log" }, "chance": 1.0, "min": 4, "max": 6 }
 *   ]
 * }
 */
public class BotanyRecipe implements Recipe<SimpleContainer> {

    /** The seed/sapling/etc required to grow this crop (slot 0). */
    private final Ingredient seed;

    /** The soil required for this crop (slot 1). */
    private final Ingredient soil;

    /** Base time to grow in ticks. */
    private final int growthTicks;

    /** Weighted/conditional output table rolled at harvest time. */
    private final List<Drop> drops;

    private final ResourceLocation id;

    public BotanyRecipe(ResourceLocation id, Ingredient seed, Ingredient soil, int growthTicks, List<Drop> drops) {
        this.id = id;
        this.seed = seed;
        this.soil = soil;
        this.growthTicks = Math.max(0, growthTicks);
        this.drops = List.copyOf(drops);
    }

    // ---------- getters used by your BlockEntity ----------
    public Ingredient getSeed() { return seed; }
    public Ingredient getSoil() { return soil; }
    public int getGrowthTicks() { return growthTicks; }
    public List<Drop> getDrops() { return drops; }

    // ---------- Recipe plumbing ----------

    @Override
    public boolean matches(SimpleContainer inv, Level level) {
        if (level.isClientSide()) return false;

        // Expect: slot 0 = seed, slot 1 = soil
        if (inv.getContainerSize() < 2) return false;
        return seed.test(inv.getItem(0)) && soil.test(inv.getItem(1));
    }

    @Override
    public ItemStack assemble(SimpleContainer inv, RegistryAccess regs) {
        // Not used; harvesting reads getDrops().
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) { return true; }

    @Override
    public ItemStack getResultItem(RegistryAccess regs) {
        // For JEI preview: show first drop if present.
        return drops.isEmpty() ? ItemStack.EMPTY : drops.get(0).stack().copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, seed, soil);
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return Serializer.INSTANCE; }
    @Override public RecipeType<?> getType() { return Type.INSTANCE; }

    // ---------- Types & Serializer ----------

    public static class Type implements RecipeType<BotanyRecipe> {
        public static final Type INSTANCE = new Type();
        public static final String ID = "botany_pot";
    }

    public static class Serializer implements RecipeSerializer<BotanyRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        public static final ResourceIdentifier ID = new ResourceIdentifier("botany_pot");

        @Override
        public BotanyRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient seed = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "seed"));
            Ingredient soil = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "soil")); // NEW

            int growth = GsonHelper.getAsInt(json, "growth_ticks", 1200);

            JsonArray arr = GsonHelper.getAsJsonArray(json, "drops");
            List<Drop> drops = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                JsonObject dj = arr.get(i).getAsJsonObject();

                ItemStack out = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(dj, "output"));
                float chance = (float) GsonHelper.getAsDouble(dj, "chance", 1.0D);
                int min = GsonHelper.getAsInt(dj, "min", 1);
                int max = GsonHelper.getAsInt(dj, "max", 1);

                if (chance < 0f) chance = 0f;
                if (chance > 1f) chance = 1f;
                if (min < 0) min = 0;
                if (max < min) max = min;

                drops.add(new Drop(out, chance, min, max));
            }

            return new BotanyRecipe(id, seed, soil, growth, drops);
        }

        @Override
        public @Nullable BotanyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient seed = Ingredient.fromNetwork(buf);
            Ingredient soil = Ingredient.fromNetwork(buf); // NEW
            int growth = buf.readVarInt();

            int n = buf.readVarInt();
            List<Drop> drops = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ItemStack stack = buf.readItem();
                float chance = buf.readFloat();
                int min = buf.readVarInt();
                int max = buf.readVarInt();
                drops.add(new Drop(stack, chance, min, max));
            }
            return new BotanyRecipe(id, seed, soil, growth, drops);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BotanyRecipe r) {
            r.seed.toNetwork(buf);
            r.soil.toNetwork(buf); // NEW
            buf.writeVarInt(r.growthTicks);

            buf.writeVarInt(r.drops.size());
            for (Drop d : r.drops) {
                buf.writeItem(d.stack());
                buf.writeFloat(d.chance());
                buf.writeVarInt(d.min());
                buf.writeVarInt(d.max());
            }
        }
    }

    // ---------- Drop descriptor ----------

    public static final class Drop {
        private final ItemStack stack;
        private final float chance; // 0..1
        private final int min;
        private final int max;

        public Drop(ItemStack stack, float chance, int min, int max) {
            this.stack = stack.copy();
            this.chance = chance;
            this.min = min;
            this.max = max;
        }

        public ItemStack stack() { return stack; }
        public float chance() { return chance; }
        public int min() { return min; }
        public int max() { return max; }
    }
}
