package net.minecraft.network.chat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.NativeData;
import net.minecraft.resources.NativeValueTypes;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.ExtraCodecs;

/**
 * Click and hover events as projections of Rust's decoded values
 * (`content/data/text.rs`), a variant per action in enum order.
 */
final class NativeEvents {
	private NativeEvents() {
	}

	static ClickEvent click(NativeData.Variant variant, NativeData.Context context) {
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> new ClickEvent.OpenUrl(java.net.URI.create((String)f[0]));
			case 1 -> new ClickEvent.OpenFile((String)f[0]);
			case 2 -> new ClickEvent.RunCommand((String)f[0]);
			case 3 -> new ClickEvent.SuggestCommand((String)f[0]);
			case 4 -> new ClickEvent.ShowDialog(context.slot(f[0], Dialog.CODEC));
			case 5 -> new ClickEvent.ChangePage((Integer)f[0]);
			case 6 -> new ClickEvent.CopyToClipboard((String)f[0]);
			default -> new ClickEvent.Custom(NativeData.location(f[0]), context.optionalSlot(f[1], ExtraCodecs.NBT));
		};
	}

	static Object click(ClickEvent event, NativeData.Builder builder) {
		return switch (event) {
			case ClickEvent.OpenUrl e -> new NativeData.Variant(0, new Object[]{e.uri().toString()});
			case ClickEvent.OpenFile e -> new NativeData.Variant(1, new Object[]{e.path()});
			case ClickEvent.RunCommand e -> new NativeData.Variant(2, new Object[]{e.command()});
			case ClickEvent.SuggestCommand e -> new NativeData.Variant(3, new Object[]{e.command()});
			case ClickEvent.ShowDialog e -> new NativeData.Variant(4, new Object[]{builder.slot(e.dialog(), Dialog.CODEC)});
			case ClickEvent.ChangePage e -> new NativeData.Variant(5, new Object[]{e.page()});
			case ClickEvent.CopyToClipboard e -> new NativeData.Variant(6, new Object[]{e.value()});
			case ClickEvent.Custom e -> new NativeData.Variant(7, new Object[]{e.id().toString(), builder.optionalSlot(e.payload(), ExtraCodecs.NBT)});
			default -> throw new IllegalArgumentException("Unsupported click event " + event);
		};
	}

	static HoverEvent hover(NativeData.Variant variant, NativeData.Context context) {
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> new HoverEvent.ShowText(NativeText.project(f[0], context));
			case 1 -> new HoverEvent.ShowItem(net.minecraft.world.item.NativeItemStacks.project(f[0], context));
			default -> new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(
				BuiltInRegistries.ENTITY_TYPE.getValue(NativeData.location(f[0])),
				NativeValueTypes.uuid(((NativeData.Variant)f[1]).fields()[0]),
				NativeText.optional(f[2], context)
			));
		};
	}

	static Object hover(HoverEvent event, NativeData.Builder builder) {
		return switch (event) {
			case HoverEvent.ShowText e -> new NativeData.Variant(0, new Object[]{NativeText.unproject(e.value(), builder)});
			case HoverEvent.ShowItem e -> new NativeData.Variant(1, new Object[]{net.minecraft.world.item.NativeItemStacks.unproject(e.item(), builder)});
			case HoverEvent.ShowEntity e -> new NativeData.Variant(2, new Object[]{
				BuiltInRegistries.ENTITY_TYPE.getKey(e.entity().type).toString(),
				new NativeData.Variant(0, new Object[]{NativeValueTypes.uuid(e.entity().uuid)}),
				NativeText.optional(e.entity().name, builder)
			});
			default -> throw new IllegalArgumentException("Unsupported hover event " + event);
		};
	}

}
