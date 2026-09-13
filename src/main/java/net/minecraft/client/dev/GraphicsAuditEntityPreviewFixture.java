package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ProblemReporter;
import org.jetbrains.annotations.Nullable;

/** Ordinary server-opened entity-preview fixtures; never changes renderer state. */
public final class GraphicsAuditEntityPreviewFixture {
    private static final String PROPERTY = "mattmc.dev.graphicsAuditEntityPreview";
    private static final int HORSE_DYE_RGB = 0x3366CC;
    private static CompletableFuture<Applied> applied;
    private static CompletableFuture<Opened> opened;
    private static double previousMouseX, previousMouseY;
    private record Applied(ServerPlayer player, @Nullable AbstractHorse horse) {}
    private record Opened(boolean menuOpened, boolean recipePopulated) {}

    static String mode() {
        String value = System.getProperty(PROPERTY, "");
        if (!value.isEmpty() && !value.equals("horse") && !value.equals("horse-black") && !value.equals("horse-brown") && !value.equals("horse-creamy") && !value.equals("horse-chestnut") && !value.equals("horse-gray") && !value.equals("horse-dark-brown") && !value.equals("horse-marking-white") && !value.equals("horse-marking-white-field") && !value.equals("horse-marking-black-dots") && !value.equals("horse-equipped") && !value.equals("horse-iron-equipped") && !value.equals("horse-gold-equipped") && !value.equals("horse-copper-equipped") && !value.equals("horse-leather-equipped") && !value.equals("horse-dyed-leather-equipped") && !value.equals("horse-baby") && !value.equals("horse-baby-equipped") && !value.equals("horse-baby-iron-equipped") && !value.equals("horse-baby-gold-equipped") && !value.equals("horse-baby-copper-equipped") && !value.equals("horse-baby-leather-equipped") && !value.equals("horse-baby-dyed-leather-equipped") && !value.equals("horse-marked") && !value.equals("horse-baby-marked") && !value.equals("horse-marked-equipped") && !value.equals("horse-baby-marked-equipped") && !value.equals("skeleton-horse-saddled") && !value.equals("skeleton-horse-baby-saddled") && !value.equals("zombie-horse-saddled") && !value.equals("zombie-horse-baby-saddled") && !value.equals("donkey-chested-equipped") && !value.equals("donkey-baby-chested-equipped") && !value.equals("mule-chested-equipped") && !value.equals("mule-baby-chested-equipped") && !value.equals("llama-chested-blue-carpet") && !value.equals("llama-baby-chested-blue-carpet") && !value.equals("smithing")
                && !value.equals("smithing-netherite-chestplate"))
            throw new IllegalArgumentException("unsupported entity preview mode");
        return value;
    }

    static boolean requested() { return !mode().isEmpty(); }
    static boolean horseMode() { return mode().startsWith("horse") || mode().startsWith("skeleton-horse-") || mode().startsWith("zombie-horse-") || mode().startsWith("donkey-") || mode().startsWith("mule-") || mode().startsWith("llama-"); }
    static boolean smithingMode() { return mode().equals("smithing") || mode().equals("smithing-netherite-chestplate"); }

    static boolean prepare(Minecraft minecraft) {
        if (!requested()) return true;
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) return false;
        if (applied == null) {
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            previousMouseX = minecraft.mouseHandler.xpos();
            previousMouseY = minecraft.mouseHandler.ypos();
            GraphicsAuditInventoryPreviewInputs.resetReadiness();
            applied = server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("entity-preview player disappeared");
                if (smithingMode()) return new Applied(player, null);
                AbstractHorse horse = mode().startsWith("skeleton-horse-")
                    ? new SkeletonHorse(EntityType.SKELETON_HORSE, player.level())
                    : mode().startsWith("zombie-horse-")
                    ? new ZombieHorse(EntityType.ZOMBIE_HORSE, player.level())
                    : mode().startsWith("donkey-")
                    ? new Donkey(EntityType.DONKEY, player.level())
                    : mode().startsWith("mule-")
                        ? new Mule(EntityType.MULE, player.level())
                    : mode().startsWith("llama-")
                        ? new Llama(EntityType.LLAMA, player.level()) : new Horse(EntityType.HORSE, player.level());
                Vec3 position = player.position().add(2.0, -1.5, 0.0);
                horse.setPos(position.x, position.y, position.z);
                player.level().getChunkAt(horse.blockPosition());
                horse.setYRot(0.0F);
                horse.setYHeadRot(0.0F);
                horse.setNoAi(true);
                horse.setNoGravity(true);
                horse.setDeltaMovement(Vec3.ZERO);
                horse.setOwner(player);
                horse.setTamed(true);
                if (mode().equals("horse-black")) setVariantAndMarkings((Horse)horse, Variant.BLACK, Markings.NONE);
                if (mode().equals("horse-brown")) setVariantAndMarkings((Horse)horse, Variant.BROWN, Markings.NONE);
                if (mode().equals("horse-creamy")) setVariantAndMarkings((Horse)horse, Variant.CREAMY, Markings.NONE);
                if (mode().equals("horse-chestnut")) setVariantAndMarkings((Horse)horse, Variant.CHESTNUT, Markings.NONE);
                if (mode().equals("horse-gray")) setVariantAndMarkings((Horse)horse, Variant.GRAY, Markings.NONE);
                if (mode().equals("horse-dark-brown")) setVariantAndMarkings((Horse)horse, Variant.DARK_BROWN, Markings.NONE);
                if (mode().equals("horse-marking-white")) setVariantAndMarkings((Horse)horse, Variant.WHITE, Markings.WHITE);
                if (mode().equals("horse-marking-white-field")) setVariantAndMarkings((Horse)horse, Variant.WHITE, Markings.WHITE_FIELD);
                if (mode().equals("horse-marking-black-dots")) setVariantAndMarkings((Horse)horse, Variant.WHITE, Markings.BLACK_DOTS);
                if (mode().contains("marked")) setVariantAndMarkings((Horse)horse, Variant.WHITE, Markings.WHITE_DOTS);
                if (mode().contains("baby")) horse.setBaby(true);
                if (mode().equals("skeleton-horse-saddled") || mode().equals("skeleton-horse-baby-saddled") || mode().equals("zombie-horse-saddled") || mode().equals("zombie-horse-baby-saddled"))
                    horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                if (mode().startsWith("donkey-") || mode().startsWith("mule-")) {
                    if (!horse.getSlot(499).set(new ItemStack(Items.CHEST)))
                        throw new IllegalStateException("chested equine slot rejected fixture");
                    horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                }
                if (mode().startsWith("llama-")) {
                    setLlamaStrength((Llama)horse, 5);
                    if (!horse.getSlot(499).set(new ItemStack(Items.CHEST)))
                        throw new IllegalStateException("llama chest slot rejected fixture");
                    horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.BLUE_CARPET));
                }
                if (mode().contains("equipped")) {
                    if (!mode().startsWith("donkey-") && !mode().startsWith("mule-")) {
                        ItemStack bodyArmor = new ItemStack(expectedHorseBodyArmor());
                        if (mode().contains("dyed-leather"))
                            bodyArmor.set(DataComponents.DYED_COLOR, new DyedItemColor(HORSE_DYE_RGB));
                        horse.setItemSlot(EquipmentSlot.BODY, bodyArmor);
                        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                    }
                }
                player.level().addFreshEntity(horse);
                return new Applied(player, horse);
            });
        }
        if (!applied.isDone() || applied.isCompletedExceptionally()) return false;
        Applied fixture = applied.join();
        if (fixture.horse() != null) {
            if (!(minecraft.level.getEntity(fixture.horse().getId()) instanceof AbstractHorse clientHorse)) return false;
            // tailCounter is an unsynchronized random client animation. Keep the deterministic
            // preview fixture in its ordinary non-wagging state before every captured frame.
            clientHorse.tailCounter = 0;
            if (!clientHorse.isTamed() || !expectedHorseState(clientHorse)) return false;
        }
        if (opened == null) {
            opened = minecraft.getSingleplayerServer().submit(() -> {
                if (fixture.horse() != null) {
                    if (!fixture.horse().isAlive()) return new Opened(false, false);
                    fixture.horse().openCustomInventoryScreen(fixture.player());
                    return new Opened(true, false);
                } else {
                    fixture.player().openMenu(new SimpleMenuProvider(
                        (containerId, inventory, player) -> new SmithingMenu(containerId, inventory),
                        Component.translatable("container.upgrade")));
                    boolean populate = mode().equals("smithing-netherite-chestplate");
                    if (populate) {
                        if (!(fixture.player().containerMenu instanceof SmithingMenu menu))
                            return new Opened(false, false);
                        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
                        menu.getSlot(1).set(new ItemStack(Items.DIAMOND_CHESTPLATE));
                        menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
                        menu.broadcastChanges();
                    }
                    return new Opened(true, populate);
                }
            });
            return false;
        }
        if (!opened.isDone() || opened.isCompletedExceptionally() || !opened.join().menuOpened()) return false;
        if (!expectedScreen(minecraft)) return false;
        if (!expectedResult(minecraft)) return false;
        if (!mouseCentered(minecraft)) {
            var window = minecraft.getWindow();
            net.blaze3d.platform.InputConstants.grabOrReleaseMouse(window, 212993,
                window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
            return false;
        }
        return GraphicsAuditInventoryPreviewInputs.ready();
    }

    private static boolean expectedScreen(Minecraft minecraft) {
        return horseMode() ? minecraft.screen instanceof HorseInventoryScreen
            : minecraft.screen instanceof SmithingScreen;
    }

    private static boolean expectedHorseState(AbstractHorse horse) {
        if (mode().equals("skeleton-horse-saddled") || mode().equals("skeleton-horse-baby-saddled"))
            return horse instanceof SkeletonHorse && horse.isBaby() == mode().contains("baby")
                && horse.getBodyArmorItem().isEmpty()
                && horse.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
        if (mode().equals("zombie-horse-saddled") || mode().equals("zombie-horse-baby-saddled"))
            return horse instanceof ZombieHorse && horse.isBaby() == mode().contains("baby")
                && horse.getBodyArmorItem().isEmpty()
                && horse.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
        if (mode().startsWith("donkey-"))
            return horse instanceof Donkey donkey && donkey.hasChest()
                && donkey.getInventoryColumns() == 5
                && donkey.isBaby() == mode().contains("baby")
                && donkey.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
        if (mode().startsWith("mule-"))
            return horse instanceof Mule mule && mule.hasChest()
                && mule.getInventoryColumns() == 5
                && mule.isBaby() == mode().contains("baby")
                && mule.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
        if (mode().startsWith("llama-"))
            return horse instanceof Llama llama && llama.hasChest()
                && llama.getInventoryColumns() == 5
                && llama.isBaby() == mode().contains("baby")
                && llama.getBodyArmorItem().is(Items.BLUE_CARPET)
                && llama.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
        boolean equipped = mode().contains("equipped");
        if (equipped && !(horse.getBodyArmorItem().is(expectedHorseBodyArmor())
                && horse.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE))) return false;
        if (mode().contains("dyed-leather")
                && (horse.getBodyArmorItem().get(DataComponents.DYED_COLOR) == null
                    || horse.getBodyArmorItem().get(DataComponents.DYED_COLOR).rgb() != HORSE_DYE_RGB)) return false;
        if (mode().contains("baby") && !horse.isBaby()) return false;
        if (mode().equals("horse-black"))
            return horse instanceof Horse black && black.getVariant() == Variant.BLACK
                && black.getMarkings() == Markings.NONE && !black.isBaby();
        if (mode().equals("horse-brown"))
            return horse instanceof Horse brown && brown.getVariant() == Variant.BROWN
                && brown.getMarkings() == Markings.NONE && !brown.isBaby();
        if (mode().equals("horse-creamy"))
            return horse instanceof Horse creamy && creamy.getVariant() == Variant.CREAMY
                && creamy.getMarkings() == Markings.NONE && !creamy.isBaby();
        if (mode().equals("horse-chestnut"))
            return horse instanceof Horse chestnut && chestnut.getVariant() == Variant.CHESTNUT
                && chestnut.getMarkings() == Markings.NONE && !chestnut.isBaby();
        if (mode().equals("horse-gray"))
            return horse instanceof Horse gray && gray.getVariant() == Variant.GRAY
                && gray.getMarkings() == Markings.NONE && !gray.isBaby();
        if (mode().equals("horse-dark-brown"))
            return horse instanceof Horse darkBrown && darkBrown.getVariant() == Variant.DARK_BROWN
                && darkBrown.getMarkings() == Markings.NONE && !darkBrown.isBaby();
        if (mode().equals("horse-marking-white"))
            return horse instanceof Horse whiteMarked && whiteMarked.getVariant() == Variant.WHITE
                && whiteMarked.getMarkings() == Markings.WHITE && !whiteMarked.isBaby();
        if (mode().equals("horse-marking-white-field"))
            return horse instanceof Horse whiteField && whiteField.getVariant() == Variant.WHITE
                && whiteField.getMarkings() == Markings.WHITE_FIELD && !whiteField.isBaby();
        if (mode().equals("horse-marking-black-dots"))
            return horse instanceof Horse blackDots && blackDots.getVariant() == Variant.WHITE
                && blackDots.getMarkings() == Markings.BLACK_DOTS && !blackDots.isBaby();
        return !mode().contains("marked") || horse instanceof Horse marked
            && marked.getVariant() == Variant.WHITE && marked.getMarkings() == Markings.WHITE_DOTS;
    }

    private static Item expectedHorseBodyArmor() {
        return switch (mode()) {
            case "horse-iron-equipped", "horse-baby-iron-equipped" -> Items.IRON_HORSE_ARMOR;
            case "horse-gold-equipped", "horse-baby-gold-equipped" -> Items.GOLDEN_HORSE_ARMOR;
            case "horse-copper-equipped", "horse-baby-copper-equipped" -> Items.COPPER_HORSE_ARMOR;
            case "horse-leather-equipped", "horse-dyed-leather-equipped", "horse-baby-leather-equipped", "horse-baby-dyed-leather-equipped" -> Items.LEATHER_HORSE_ARMOR;
            default -> Items.DIAMOND_HORSE_ARMOR;
        };
    }

    private static void setVariantAndMarkings(Horse horse, Variant variant, Markings markings) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, horse.registryAccess());
        horse.saveWithoutId(output);
        var tag = output.buildResult();
        tag.putInt("Variant", variant.getId() | markings.getId() << 8);
        horse.load(TagValueInput.create(ProblemReporter.DISCARDING, horse.registryAccess(), tag));
    }

    private static void setLlamaStrength(Llama llama, int strength) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, llama.registryAccess());
        llama.saveWithoutId(output);
        var tag = output.buildResult();
        tag.putInt("Strength", strength);
        llama.load(TagValueInput.create(ProblemReporter.DISCARDING, llama.registryAccess(), tag));
    }

    private static boolean expectedResult(Minecraft minecraft) {
        if (!mode().equals("smithing-netherite-chestplate")) return true;
        return minecraft.player != null && minecraft.player.containerMenu instanceof SmithingMenu menu
            && menu.getSlot(3).getItem().is(Items.NETHERITE_CHESTPLATE);
    }

    private static boolean mouseCentered(Minecraft minecraft) {
        var window = minecraft.getWindow();
        return Math.abs(minecraft.mouseHandler.xpos() - window.getScreenWidth() / 2.0) < 0.5
            && Math.abs(minecraft.mouseHandler.ypos() - window.getScreenHeight() / 2.0) < 0.5;
    }

    static JsonObject receipt(Minecraft minecraft) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "entity-preview-fixture-v1");
        result.addProperty("requested", requested());
        result.addProperty("mode", mode());
        boolean prepared = applied != null && applied.isDone() && !applied.isCompletedExceptionally();
        boolean serverSpawned = prepared && applied.join().horse() != null;
        result.addProperty("serverSpawned", serverSpawned);
        int entityId = serverSpawned ? applied.join().horse().getId() : -1;
        result.addProperty("entityId", entityId);
        boolean replicated = minecraft.level != null && minecraft.level.getEntity(entityId) instanceof AbstractHorse;
        result.addProperty("clientReplicated", replicated);
        AbstractHorse clientHorse = replicated ? (AbstractHorse)minecraft.level.getEntity(entityId) : null;
        boolean horseStateReady = clientHorse != null && expectedHorseState(clientHorse);
        boolean horseEquipmentPopulated = clientHorse != null
            && mode().contains("equipped")
            && clientHorse.getBodyArmorItem().is(expectedHorseBodyArmor())
            && clientHorse.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
        result.addProperty("horseStateReady", horseStateReady);
        result.addProperty("horseEquipmentPopulated", horseEquipmentPopulated);
        result.addProperty("horseBaby", clientHorse != null && clientHorse.isBaby());
        result.addProperty("horseVariant", clientHorse instanceof Horse horse ? horse.getVariant().getSerializedName() : "");
        result.addProperty("horseMarkings", clientHorse instanceof Horse horse ? horse.getMarkings().name().toLowerCase(java.util.Locale.ROOT) : "");
        result.addProperty("horseBodyItem", clientHorse == null || clientHorse.getBodyArmorItem().isEmpty() ? ""
            : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(clientHorse.getBodyArmorItem().getItem()).toString());
        DyedItemColor horseDye = clientHorse == null ? null : clientHorse.getBodyArmorItem().get(DataComponents.DYED_COLOR);
        result.addProperty("horseBodyDyeRgb", horseDye == null ? -1 : horseDye.rgb());
        result.addProperty("horseSaddleItem", clientHorse == null || clientHorse.getItemBySlot(EquipmentSlot.SADDLE).isEmpty() ? ""
            : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(clientHorse.getItemBySlot(EquipmentSlot.SADDLE).getItem()).toString());
        result.addProperty("equineType", clientHorse == null ? ""
            : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(clientHorse.getType()).toString());
        result.addProperty("horseHasChest", clientHorse instanceof AbstractChestedHorse chested && chested.hasChest());
        result.addProperty("horseInventoryColumns", clientHorse == null ? 0 : clientHorse.getInventoryColumns());
        result.addProperty("llamaVariant", clientHorse instanceof Llama llama ? llama.getVariant().getSerializedName() : "");
        result.addProperty("llamaStrength", clientHorse instanceof Llama llama ? llama.getStrength() : 0);
        result.addProperty("llamaDecorPopulated", clientHorse instanceof Llama llama
            && llama.getBodyArmorItem().is(Items.BLUE_CARPET));
        boolean menuOpened = opened != null && opened.isDone() && !opened.isCompletedExceptionally()
            && opened.join().menuOpened();
        result.addProperty("serverMenuOpened", menuOpened);
        boolean recipePopulated = menuOpened && opened.join().recipePopulated();
        result.addProperty("recipePopulated", recipePopulated);
        String resultItem = "";
        if (minecraft.player != null && minecraft.player.containerMenu instanceof SmithingMenu menu
                && !menu.getSlot(3).getItem().isEmpty())
            resultItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(menu.getSlot(3).getItem().getItem()).toString();
        result.addProperty("resultItem", resultItem);
        result.addProperty("screenOpen", requested() && expectedScreen(minecraft));
        result.addProperty("mouseCentered", mouseCentered(minecraft));
        result.addProperty("previewReady", GraphicsAuditInventoryPreviewInputs.ready());
        boolean sourceReady = horseMode() ? serverSpawned && replicated && horseStateReady
            : prepared && menuOpened && expectedResult(minecraft);
        result.addProperty("complete", requested() && sourceReady && expectedScreen(minecraft)
            && mouseCentered(minecraft) && GraphicsAuditInventoryPreviewInputs.ready());
        return result;
    }

    static void restore(Minecraft minecraft) {
        if (!requested() && applied == null) return;
        if (minecraft.player != null && (minecraft.screen instanceof HorseInventoryScreen
                || minecraft.screen instanceof SmithingScreen)) {
            net.blaze3d.platform.InputConstants.grabOrReleaseMouse(minecraft.getWindow(), 212993, previousMouseX, previousMouseY);
            minecraft.player.closeContainer();
        }
        if (applied != null && minecraft.getSingleplayerServer() != null)
            applied.thenAccept(fixture -> {
                if (fixture.horse() != null)
                    minecraft.getSingleplayerServer().execute(fixture.horse()::discard);
            });
        applied = null;
        opened = null;
        GraphicsAuditInventoryPreviewInputs.resetReadiness();
    }

    private GraphicsAuditEntityPreviewFixture() {}
}
