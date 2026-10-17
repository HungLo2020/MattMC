package net.minecraft.network.chat;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.arguments.selector.SelectorPattern;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.data.BlockDataSource;
import net.minecraft.network.chat.contents.data.DataSource;
import net.minecraft.network.chat.contents.data.EntityDataSource;
import net.minecraft.network.chat.contents.data.StorageDataSource;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.resources.NativeData;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Text components as projections of Rust's decoded values
 * (`content/data/text.rs`): {@code Record[contents, extra, style]}, contents a
 * variant per type in {@code CONTENTS} order.
 */
public final class NativeText {
	private NativeText() {
	}

	public static Component project(Object value, NativeData.Context context) {
		Object[] parts = NativeData.record(value);
		List<Component> siblings = new ArrayList<>();
		for (Object sibling : NativeData.list(parts[1])) {
			siblings.add(project(sibling, context));
		}
		return new MutableComponent(contents((NativeData.Variant)parts[0], context), siblings, Style.project(NativeData.record(parts[2]), context));
	}

	public static Object unproject(Component component, NativeData.Builder builder) {
		List<Object> siblings = new ArrayList<>();
		for (Component sibling : component.getSiblings()) {
			siblings.add(unproject(sibling, builder));
		}
		return new Object[]{contents(component.getContents(), builder), siblings, Style.unproject(component.getStyle(), builder)};
	}

	static Optional<Component> optional(Object value, NativeData.Context context) {
		return value == null ? Optional.empty() : Optional.of(project(value, context));
	}

	static Object optional(Optional<Component> component, NativeData.Builder builder) {
		return component.map(c -> unproject(c, builder)).orElse(null);
	}

	private static ComponentContents contents(NativeData.Variant variant, NativeData.Context context) {
		Object[] f = variant.fields();
		return switch (variant.index()) {
			case 0 -> PlainTextContents.create((String)f[0]);
			case 1 -> {
				List<Object> args = NativeData.list(f[2]);
				Object[] array = args.isEmpty() ? TranslatableContents.NO_ARGS : new Object[args.size()];
				for (int i = 0; i < args.size(); i++) {
					NativeData.Variant arg = (NativeData.Variant)args.get(i);
					Object a = arg.fields()[0];
					array[i] = arg.index() == 1 ? project(a, context) : a instanceof NativeData.JavaNumber number ? number.value() : a;
				}
				yield new TranslatableContents((String)f[0], (String)f[1], array);
			}
			case 2 -> new KeybindContents((String)f[0]);
			case 3 -> {
				Object[] score = NativeData.record(f[0]);
				NativeData.Variant name = (NativeData.Variant)score[0];
				String text = (String)name.fields()[0];
				yield new ScoreContents(name.index() == 0 ? Either.left(selector(text)) : Either.right(text), (String)score[1]);
			}
			case 4 -> new SelectorContents(selector((String)f[0]), optional(f[1], context));
			case 5 -> new NbtContents((String)f[0], (Boolean)f[1], optional(f[2], context), dataSource((NativeData.Variant)f[3]));
			case 6 -> new ObjectContents(objectInfo((NativeData.Variant)f[0], context));
			default -> throw new IllegalStateException("Unknown component contents " + variant.index());
		};
	}

	private static Object contents(ComponentContents contents, NativeData.Builder builder) {
		return switch (contents) {
			case PlainTextContents text -> new NativeData.Variant(0, new Object[]{text.text()});
			case TranslatableContents translatable -> {
				List<Object> args = new ArrayList<>();
				for (Object arg : translatable.getArgs()) {
					args.add(switch (arg) {
						case Component c -> new NativeData.Variant(1, new Object[]{unproject(c, builder)});
						case String s -> new NativeData.Variant(0, new Object[]{s});
						case Boolean b -> new NativeData.Variant(0, new Object[]{b});
						case Number n -> new NativeData.Variant(0, new Object[]{new NativeData.JavaNumber(n)});
						default -> throw new IllegalArgumentException("Unsupported translation argument " + arg);
					});
				}
				yield new NativeData.Variant(1, new Object[]{translatable.getKey(), translatable.getFallback(), args});
			}
			case KeybindContents keybind -> new NativeData.Variant(2, new Object[]{keybind.getName()});
			case ScoreContents score -> new NativeData.Variant(3, new Object[]{new Object[]{
				score.name().map(p -> new NativeData.Variant(0, new Object[]{p.pattern()}), s -> new NativeData.Variant(1, new Object[]{s})), score.objective()
			}});
			case SelectorContents selector -> new NativeData.Variant(4, new Object[]{selector.selector().pattern(), optional(selector.separator(), builder)});
			case NbtContents nbt -> new NativeData.Variant(5, new Object[]{
				nbt.getNbtPath(), nbt.isInterpreting(), optional(nbt.getSeparator(), builder), dataSource(nbt.getDataSource())
			});
			case ObjectContents object -> new NativeData.Variant(6, new Object[]{objectInfo(object.contents(), builder)});
			default -> throw new IllegalArgumentException("Unsupported component contents " + contents);
		};
	}

	private static DataSource dataSource(NativeData.Variant variant) {
		Object value = variant.fields()[0];
		return switch (variant.index()) {
			case 0 -> new EntityDataSource((String)value);
			case 1 -> new BlockDataSource((String)value);
			default -> new StorageDataSource(NativeData.location(value));
		};
	}

	private static Object dataSource(DataSource source) {
		return switch (source) {
			case EntityDataSource entity -> new NativeData.Variant(0, new Object[]{entity.selectorPattern()});
			case BlockDataSource block -> new NativeData.Variant(1, new Object[]{block.posPattern()});
			case StorageDataSource storage -> new NativeData.Variant(2, new Object[]{storage.id().toString()});
			default -> throw new IllegalArgumentException("Unsupported data source " + source);
		};
	}

	private static ObjectInfo objectInfo(NativeData.Variant variant, NativeData.Context context) {
		Object[] f = variant.fields();
		return variant.index() == 0
			? new AtlasSprite(NativeData.location(f[0]), NativeData.location(f[1]))
			: new PlayerSprite(context.slot(f[0], ResolvableProfile.CODEC), (Boolean)f[1]);
	}

	private static Object objectInfo(ObjectInfo info, NativeData.Builder builder) {
		return switch (info) {
			case AtlasSprite atlas -> new NativeData.Variant(0, new Object[]{atlas.atlas().toString(), atlas.sprite().toString()});
			case PlayerSprite player -> new NativeData.Variant(1, new Object[]{builder.slot(player.player(), ResolvableProfile.CODEC), player.hat()});
			default -> throw new IllegalArgumentException("Unsupported object info " + info);
		};
	}

	/** A selector pattern Rust already checked with the same parser. */
	private static SelectorPattern selector(String pattern) {
		return SelectorPattern.parse(pattern).getOrThrow();
	}
}
