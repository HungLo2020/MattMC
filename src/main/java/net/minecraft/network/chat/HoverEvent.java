package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface HoverEvent {
	/** Rust decodes, validates and encodes events (`content/data/text.rs`). */
	net.minecraft.resources.NativeValues.ValueCodec<HoverEvent> CODEC = net.minecraft.resources.NativeValues.codec(
		net.minecraft.resources.NativeValues.Kind.HOVER_EVENT, (value, context) -> NativeEvents.hover((net.minecraft.resources.NativeData.Variant)value, context), NativeEvents::hover
	);

	HoverEvent.Action action();

	public static enum Action implements StringRepresentable {
		SHOW_TEXT("show_text", true),
		SHOW_ITEM("show_item", true),
		SHOW_ENTITY("show_entity", true);

		private final String name;
		private final boolean allowFromServer;

		private Action(final String string2, final boolean bl) {
			this.name = string2;
			this.allowFromServer = bl;
		}

		public boolean isAllowedFromServer() {
			return this.allowFromServer;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public String toString() {
			return "<action " + this.name + ">";
		}
	}

	public static class EntityTooltipInfo {
		public final EntityType<?> type;
		public final UUID uuid;
		public final Optional<Component> name;
		@Nullable
		private List<Component> linesCache;

		public EntityTooltipInfo(EntityType<?> entityType, UUID uUID, @Nullable Component component) {
			this(entityType, uUID, Optional.ofNullable(component));
		}

		public EntityTooltipInfo(EntityType<?> entityType, UUID uUID, Optional<Component> optional) {
			this.type = entityType;
			this.uuid = uUID;
			this.name = optional;
		}

		public List<Component> getTooltipLines() {
			if (this.linesCache == null) {
				this.linesCache = new ArrayList();
				this.name.ifPresent(this.linesCache::add);
				this.linesCache.add(Component.translatable("gui.entity_tooltip.type", this.type.getDescription()));
				this.linesCache.add(Component.literal(this.uuid.toString()));
			}

			return this.linesCache;
		}

		public boolean equals(Object object) {
			if (this == object) {
				return true;
			} else if (object != null && this.getClass() == object.getClass()) {
				HoverEvent.EntityTooltipInfo entityTooltipInfo = (HoverEvent.EntityTooltipInfo)object;
				return this.type.equals(entityTooltipInfo.type) && this.uuid.equals(entityTooltipInfo.uuid) && this.name.equals(entityTooltipInfo.name);
			} else {
				return false;
			}
		}

		public int hashCode() {
			int i = this.type.hashCode();
			i = 31 * i + this.uuid.hashCode();
			return 31 * i + this.name.hashCode();
		}
	}

	public record ShowEntity(HoverEvent.EntityTooltipInfo entity) implements HoverEvent {
		@Override
		public HoverEvent.Action action() {
			return HoverEvent.Action.SHOW_ENTITY;
		}
	}

	public record ShowItem(ItemStack item) implements HoverEvent {
		public ShowItem(ItemStack item) {
			item = item.copy();
			this.item = item;
		}

		@Override
		public HoverEvent.Action action() {
			return HoverEvent.Action.SHOW_ITEM;
		}

		public boolean equals(Object object) {
			return object instanceof HoverEvent.ShowItem showItem && ItemStack.matches(this.item, showItem.item);
		}

		public int hashCode() {
			return ItemStack.hashItemAndComponents(this.item);
		}
	}

	public record ShowText(Component value) implements HoverEvent {
		@Override
		public HoverEvent.Action action() {
			return HoverEvent.Action.SHOW_TEXT;
		}
	}
}
