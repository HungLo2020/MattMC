import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ItemStack.isSameItemSameComponents and matches with genuine custom component
 * equality: Component values (CUSTOM_NAME) whose equals records every call with
 * its operand order, answers asymmetrically, throws a sentinel or re-enters the
 * comparison. Each row records the result, every equals call in order and the
 * thrown object's identity, so a native fast path must keep the original
 * dispatch prefix, not only the final boolean. Also covers identity, empty and
 * count fast paths, copy-on-write aliases, damage patches, removals, size and
 * key mismatches, mixed native/foreign patches and different items. Probes are
 * armed only during the measured calls, since setting a component also calls
 * equals against the prototype value.
 */
public final class ComponentEqualityReference {
    static final List<String> rows = new ArrayList<>();
    static final List<String> events = new ArrayList<>();
    static final RuntimeException SENTINEL = new RuntimeException("original custom equals failure");
    static ItemStack reenterLeft, reenterRight;
    /** Probes act only inside a measured comparison; building a stack also calls equals (against the prototype value). */
    static boolean armed;

    /** A custom Component whose equals is observable. */
    static final class Probe implements Component {
        final String tag, mode;
        Probe(String tag, String mode) { this.tag = tag; this.mode = mode; }
        @Override public Style getStyle() { return Style.EMPTY; }
        @Override public ComponentContents getContents() { return net.minecraft.network.chat.contents.PlainTextContents.EMPTY; }
        @Override public List<Component> getSiblings() { return List.of(); }
        @Override public FormattedCharSequence getVisualOrderText() { return FormattedCharSequence.EMPTY; }
        @Override public int hashCode() { return 7; }
        @Override public boolean equals(Object other) {
            if (!armed) return this == other;
            events.add("equals " + tag + "->" + (other instanceof Probe probe ? probe.tag : String.valueOf(other)));
            switch (mode) {
                case "throw": throw SENTINEL;
                case "reenter": events.add("reentered " + ItemStack.isSameItemSameComponents(reenterLeft, reenterRight)); return true;
                case "asym": return other instanceof Probe probe && tag.compareTo(probe.tag) < 0;
                case "false": return false;
                default: return other instanceof Probe;
            }
        }
        @Override public String toString() { return "Probe[" + tag + "]"; }
    }
    static ItemStack named(String tag, String mode) { ItemStack stack = new ItemStack(Items.STICK, 7); stack.set(DataComponents.CUSTOM_NAME, new Probe(tag, mode)); return stack; }
    static ItemStack damaged(int damage) { ItemStack stack = new ItemStack(Items.IRON_SWORD); stack.setDamageValue(damage); return stack; }

    static void compare(String name, ItemStack a, ItemStack b) {
        events.clear(); String same, matches; armed = true;
        try { same = String.valueOf(ItemStack.isSameItemSameComponents(a, b)); }
        catch (Throwable failure) { same = failure.getClass().getName() + ":" + failure.getMessage() + "/sentinel=" + (failure == SENTINEL); }
        List<String> sameEvents = new ArrayList<>(events); events.clear();
        try { matches = String.valueOf(ItemStack.matches(a, b)); }
        catch (Throwable failure) { matches = failure.getClass().getName() + ":" + failure.getMessage() + "/sentinel=" + (failure == SENTINEL); }
        armed = false;
        rows.add(name + " same=" + same + " events=" + sameEvents + " matches=" + matches + " events=" + events);
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        ItemStack stick = new ItemStack(Items.STICK, 7);
        compare("identity", stick, stick);
        compare("both-empty", ItemStack.EMPTY, new ItemStack(Items.STICK, 0));
        compare("one-empty", ItemStack.EMPTY, stick);
        compare("count-differs", stick, new ItemStack(Items.STICK, 8));
        compare("cow-copy", stick, stick.copy());
        compare("different-items", stick, new ItemStack(Items.DIRT, 7));
        ItemStack sword = damaged(3);
        compare("damage-copy", sword, sword.copy());
        compare("damage-equal", damaged(3), damaged(3));
        compare("damage-differs", damaged(3), damaged(4));
        compare("damage-vs-default", damaged(3), new ItemStack(Items.IRON_SWORD));
        ItemStack removedA = new ItemStack(Items.STICK, 7); removedA.remove(DataComponents.MAX_STACK_SIZE);
        ItemStack removedB = new ItemStack(Items.STICK, 7); removedB.remove(DataComponents.MAX_STACK_SIZE);
        compare("removal-equal", removedA, removedB);
        compare("removal-vs-default", removedA, stick);
        ItemStack mutated = sword.copy(); mutated.setDamageValue(5);
        compare("mutated-copy", sword, mutated);
        compare("custom-equal", named("a", "true"), named("b", "true"));
        compare("custom-unequal", named("a", "false"), named("b", "false"));
        compare("custom-asym-ab", named("a", "asym"), named("b", "asym"));
        compare("custom-asym-ba", named("b", "asym"), named("a", "asym"));
        compare("custom-throw", named("a", "throw"), named("b", "throw"));
        reenterLeft = damaged(3); reenterRight = damaged(3);
        compare("custom-reenter", named("a", "reenter"), named("b", "reenter"));
        compare("custom-vs-none", named("a", "true"), new ItemStack(Items.STICK, 7));
        ItemStack loreOnly = new ItemStack(Items.STICK, 7); loreOnly.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(new Probe("lore", "true"))));
        compare("key-mismatch", named("a", "true"), loreOnly);
        ItemStack mixedA = damaged(3); mixedA.set(DataComponents.CUSTOM_NAME, new Probe("a", "true"));
        ItemStack mixedB = damaged(3); mixedB.set(DataComponents.CUSTOM_NAME, new Probe("b", "true"));
        compare("mixed-native-foreign", mixedA, mixedB);
        ItemStack mixedC = damaged(4); mixedC.set(DataComponents.CUSTOM_NAME, new Probe("c", "true"));
        compare("mixed-damage-differs", mixedA, mixedC);
        ItemStack aliased = named("a", "true"); ItemStack alias = aliased.copy();
        compare("custom-cow-copy", aliased, alias);
        Files.write(Path.of(args[0]), rows); System.out.println("COMPONENT_EQUALITY rows=" + rows.size());
    }
}
