package net.minecraft.network.chat;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.StringRepresentable;

public interface ClickEvent {
	/** Rust decodes, validates and encodes events (`content/data/text.rs`). */
	net.minecraft.resources.NativeValues.ValueCodec<ClickEvent> CODEC = net.minecraft.resources.NativeValues.codec(
		net.minecraft.resources.NativeValues.Kind.CLICK_EVENT, (value, context) -> NativeEvents.click((net.minecraft.resources.NativeData.Variant)value, context), NativeEvents::click
	);

	ClickEvent.Action action();

	public static enum Action implements StringRepresentable {
		OPEN_URL("open_url", true),
		OPEN_FILE("open_file", false),
		RUN_COMMAND("run_command", true),
		SUGGEST_COMMAND("suggest_command", true),
		SHOW_DIALOG("show_dialog", true),
		CHANGE_PAGE("change_page", true),
		COPY_TO_CLIPBOARD("copy_to_clipboard", true),
		CUSTOM("custom", true);

		private final boolean allowFromServer;
		private final String name;

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

		/** The action's fields, as the native codec reads them (dialog actions embed them). */
		public MapCodec<? extends ClickEvent> valueCodec() {
			return ClickEvent.CODEC.dispatchCase(this.name);
		}

		public static DataResult<ClickEvent.Action> filterForSerialization(ClickEvent.Action action) {
			return !action.isAllowedFromServer() ? DataResult.error(() -> "Click event type not allowed: " + action) : DataResult.success(action, Lifecycle.stable());
		}
	}

	public record ChangePage(int page) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.CHANGE_PAGE;
		}
	}

	public record CopyToClipboard(String value) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.COPY_TO_CLIPBOARD;
		}
	}

	public record Custom(ResourceLocation id, Optional<Tag> payload) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.CUSTOM;
		}
	}

	public record OpenFile(String path) implements ClickEvent {
		public OpenFile(File file) {
			this(file.toString());
		}

		public OpenFile(Path path) {
			this(path.toFile());
		}

		public File file() {
			return new File(this.path);
		}

		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.OPEN_FILE;
		}
	}

	public record OpenUrl(URI uri) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.OPEN_URL;
		}
	}

	public record RunCommand(String command) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.RUN_COMMAND;
		}
	}

	public record ShowDialog(Holder<Dialog> dialog) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.SHOW_DIALOG;
		}
	}

	public record SuggestCommand(String command) implements ClickEvent {
		@Override
		public ClickEvent.Action action() {
			return ClickEvent.Action.SUGGEST_COMMAND;
		}
	}
}
