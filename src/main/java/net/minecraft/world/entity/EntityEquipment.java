package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;

public class EntityEquipment {
	public static final Codec<EntityEquipment> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).xmap(map -> {
		EnumMap<EquipmentSlot, ItemStack> enumMap = new EnumMap(EquipmentSlot.class);
		enumMap.putAll(map);
		return new EntityEquipment(enumMap);
	}, entityEquipment -> {
		Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
		map.putAll(entityEquipment.items);
		map.values().removeIf(ItemStack::isEmpty);
		return map;
	});
	private final Map<EquipmentSlot, ItemStack> items;

	private EntityEquipment(EnumMap<EquipmentSlot, ItemStack> enumMap) {
		this.items = net.minecraft.world.item.NativeItemState.newEquipmentMap(enumMap);
	}

	public EntityEquipment() {
		this(new EnumMap(EquipmentSlot.class));
	}

	/** Exact actual store only; equipment subclasses keep their original virtual calls. */
	public final net.minecraft.world.item.NativeItemState.EquipmentMap nativeEquipmentForInventory(){return this.getClass()==EntityEquipment.class?(net.minecraft.world.item.NativeItemState.EquipmentMap)this.items:null;}

	public ItemStack set(EquipmentSlot equipmentSlot, ItemStack itemStack) {
		return (ItemStack)Objects.requireNonNullElse((ItemStack)this.items.put(equipmentSlot, itemStack), ItemStack.EMPTY);
	}

	public ItemStack get(EquipmentSlot equipmentSlot) {
		return (ItemStack)this.items.getOrDefault(equipmentSlot, ItemStack.EMPTY);
	}

	public boolean isEmpty() {
		for (ItemStack itemStack : this.items.values()) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	public void tick(Entity entity) {
		for (Entry<EquipmentSlot, ItemStack> entry : this.items.entrySet()) {
			ItemStack itemStack = (ItemStack)entry.getValue();
			if (!itemStack.isEmpty()) {
				itemStack.inventoryTick(entity.level(), entity, (EquipmentSlot)entry.getKey());
			}
		}
	}

	public void setAll(EntityEquipment entityEquipment) {
		this.items.clear();
		this.items.putAll(entityEquipment.items);
	}

	public void dropAll(LivingEntity livingEntity) {
		for (ItemStack itemStack : this.items.values()) {
			livingEntity.drop(itemStack, true, false);
		}

		this.clear();
	}

	public void clear() {
		if (this.items instanceof net.minecraft.world.item.NativeItemState.EquipmentMap nativeItems) nativeItems.clearValues();
		else this.items.replaceAll((equipmentSlot, itemStack) -> ItemStack.EMPTY);
	}
}
