package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V1458 extends NamespacedSchema {
	public V1458(int i, Schema schema) {
		super(i, schema);
	}

	@Override
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> map, Map<String, Supplier<TypeTemplate>> map2) {
		super.registerTypes(schema, map, map2);
		schema.registerType(
			true,
			References.ENTITY,
			() -> DSL.and(
				References.ENTITY_EQUIPMENT.in(schema),
				DSL.optionalFields("CustomName", References.TEXT_COMPONENT.in(schema), DSL.taggedChoiceLazy("id", namespacedString(), map))
			)
		);
	}

	@Override
	public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
		Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(schema);
		schema.register(map, "minecraft:beacon", (Supplier<TypeTemplate>)(() -> nameable(schema)));
		schema.register(map, "minecraft:banner", (Supplier<TypeTemplate>)(() -> nameable(schema)));
		schema.register(map, "minecraft:brewing_stand", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:chest", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:trapped_chest", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:dispenser", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:dropper", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:enchanting_table", (Supplier<TypeTemplate>)(() -> nameable(schema)));
		schema.register(map, "minecraft:furnace", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:hopper", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		schema.register(map, "minecraft:shulker_box", (Supplier<TypeTemplate>)(() -> nameableInventory(schema)));
		return map;
	}

	public static TypeTemplate nameableInventory(Schema schema) {
		return DSL.optionalFields("Items", DSL.list(References.ITEM_STACK.in(schema)), "CustomName", References.TEXT_COMPONENT.in(schema));
	}

	public static TypeTemplate nameable(Schema schema) {
		return DSL.optionalFields("CustomName", References.TEXT_COMPONENT.in(schema));
	}
}
