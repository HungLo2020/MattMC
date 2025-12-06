package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;
import net.minecraft.world.level.GameRules.GameRuleTypeVisitor;
import net.minecraft.world.level.GameRules.IntegerValue;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.level.GameRules.Type;
import net.minecraft.world.level.GameRules.Value;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EditGameRulesScreen extends Screen {
	private static final Component TITLE = Component.translatable("editGamerule.title");
	private static final int SPACING = 8;
	final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final Consumer<Optional<GameRules>> exitCallback;
	private final Set<EditGameRulesScreen.RuleEntry> invalidEntries = Sets.<EditGameRulesScreen.RuleEntry>newHashSet();
	private final GameRules gameRules;
	@Nullable
	private EditGameRulesScreen.RuleList ruleList;
	@Nullable
	private Button doneButton;

	public EditGameRulesScreen(GameRules gameRules, Consumer<Optional<GameRules>> consumer) {
		super(TITLE);
		this.gameRules = gameRules;
		this.exitCallback = consumer;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(TITLE, this.font);
		this.ruleList = this.layout.addToContents(new EditGameRulesScreen.RuleList(this.gameRules));
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.doneButton = linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.exitCallback.accept(Optional.of(this.gameRules))).build());
		linearLayout.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.ruleList != null) {
			this.ruleList.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		this.exitCallback.accept(Optional.empty());
	}

	private void updateDoneButton() {
		if (this.doneButton != null) {
			this.doneButton.active = this.invalidEntries.isEmpty();
		}
	}

	void markInvalid(EditGameRulesScreen.RuleEntry ruleEntry) {
		this.invalidEntries.add(ruleEntry);
		this.updateDoneButton();
	}

	void clearInvalid(EditGameRulesScreen.RuleEntry ruleEntry) {
		this.invalidEntries.remove(ruleEntry);
		this.updateDoneButton();
	}

	@Environment(EnvType.CLIENT)
	public class BooleanRuleEntry extends EditGameRulesScreen.GameRuleEntry {
		private final CycleButton<Boolean> checkbox;

		public BooleanRuleEntry(final Component component, final List<FormattedCharSequence> list, final String string, final BooleanValue booleanValue) {
			super(list, component);
			this.checkbox = CycleButton.onOffBuilder(booleanValue.get())
				.displayOnlyValue()
				.withCustomNarration(cycleButton -> cycleButton.createDefaultNarrationMessage().append("\n").append(string))
				.create(10, 5, 44, 20, component, (cycleButton, boolean_) -> booleanValue.set(boolean_, null));
			this.children.add(this.checkbox);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.renderLabel(guiGraphics, this.getContentY(), this.getContentX());
			this.checkbox.setX(this.getContentRight() - 45);
			this.checkbox.setY(this.getContentY());
			this.checkbox.render(guiGraphics, i, j, f);
		}
	}

	@Environment(EnvType.CLIENT)
	public class CategoryRuleEntry extends EditGameRulesScreen.RuleEntry {
		final Component label;

		public CategoryRuleEntry(final Component component) {
			super(null);
			this.label = component;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.drawCenteredString(EditGameRulesScreen.this.minecraft.font, this.label, this.getContentXMiddle(), this.getContentY() + 5, -1);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return ImmutableList.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return ImmutableList.of(new NarratableEntry() {
				@Override
				public NarratableEntry.NarrationPriority narrationPriority() {
					return NarratableEntry.NarrationPriority.HOVERED;
				}

				@Override
				public void updateNarration(NarrationElementOutput narrationElementOutput) {
					narrationElementOutput.add(NarratedElementType.TITLE, CategoryRuleEntry.this.label);
				}
			});
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface EntryFactory<T extends Value<T>> {
		EditGameRulesScreen.RuleEntry create(Component component, List<FormattedCharSequence> list, String string, T value);
	}

	@Environment(EnvType.CLIENT)
	public abstract class GameRuleEntry extends EditGameRulesScreen.RuleEntry {
		private final List<FormattedCharSequence> label;
		protected final List<AbstractWidget> children = Lists.<AbstractWidget>newArrayList();

		public GameRuleEntry(@Nullable final List<FormattedCharSequence> list, final Component component) {
			super(list);
			this.label = EditGameRulesScreen.this.minecraft.font.split(component, 175);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.children;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.children;
		}

		protected void renderLabel(GuiGraphics guiGraphics, int i, int j) {
			if (this.label.size() == 1) {
				guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, (FormattedCharSequence)this.label.get(0), j, i + 5, -1);
			} else if (this.label.size() >= 2) {
				guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, (FormattedCharSequence)this.label.get(0), j, i, -1);
				guiGraphics.drawString(EditGameRulesScreen.this.minecraft.font, (FormattedCharSequence)this.label.get(1), j, i + 10, -1);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public class IntegerRuleEntry extends EditGameRulesScreen.GameRuleEntry {
		private final EditBox input;

		public IntegerRuleEntry(final Component component, final List<FormattedCharSequence> list, final String string, final IntegerValue integerValue) {
			super(list, component);
			this.input = new EditBox(EditGameRulesScreen.this.minecraft.font, 10, 5, 44, 20, component.copy().append("\n").append(string).append("\n"));
			this.input.setValue(Integer.toString(integerValue.get()));
			this.input.setResponder(stringx -> {
				if (integerValue.tryDeserialize(stringx)) {
					this.input.setTextColor(-2039584);
					EditGameRulesScreen.this.clearInvalid(this);
				} else {
					this.input.setTextColor(-65536);
					EditGameRulesScreen.this.markInvalid(this);
				}
			});
			this.children.add(this.input);
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.renderLabel(guiGraphics, this.getContentY(), this.getContentX());
			this.input.setX(this.getContentRight() - 45);
			this.input.setY(this.getContentY());
			this.input.render(guiGraphics, i, j, f);
		}
	}

	@Environment(EnvType.CLIENT)
	public abstract static class RuleEntry extends ContainerObjectSelectionList.Entry<EditGameRulesScreen.RuleEntry> {
		@Nullable
		final List<FormattedCharSequence> tooltip;

		public RuleEntry(@Nullable List<FormattedCharSequence> list) {
			this.tooltip = list;
		}
	}

	@Environment(EnvType.CLIENT)
	public class RuleList extends ContainerObjectSelectionList<EditGameRulesScreen.RuleEntry> {
		private static final int ITEM_HEIGHT = 24;

		public RuleList(final GameRules gameRules) {
			super(
				Minecraft.getInstance(),
				EditGameRulesScreen.this.width,
				EditGameRulesScreen.this.layout.getContentHeight(),
				EditGameRulesScreen.this.layout.getHeaderHeight(),
				24
			);
			final Map<Category, Map<Key<?>, EditGameRulesScreen.RuleEntry>> map = Maps.<Category, Map<Key<?>, EditGameRulesScreen.RuleEntry>>newHashMap();
			gameRules.visitGameRuleTypes(new GameRuleTypeVisitor() {
				public void visitBoolean(Key<BooleanValue> key, Type<BooleanValue> type) {
					this.addEntry(key, (component, list, string, booleanValue) -> EditGameRulesScreen.this.new BooleanRuleEntry(component, list, string, booleanValue));
				}

				public void visitInteger(Key<IntegerValue> key, Type<IntegerValue> type) {
					this.addEntry(key, (component, list, string, integerValue) -> EditGameRulesScreen.this.new IntegerRuleEntry(component, list, string, integerValue));
				}

				private <T extends Value<T>> void addEntry(Key<T> key, EditGameRulesScreen.EntryFactory<T> entryFactory) {
					Component component = Component.translatable(key.getDescriptionId());
					Component component2 = Component.literal(key.getId()).withStyle(ChatFormatting.YELLOW);
					T value = (T)gameRules.getRule(key);
					String string = value.serialize();
					Component component3 = Component.translatable("editGamerule.default", new Object[]{Component.literal(string)}).withStyle(ChatFormatting.GRAY);
					String string2 = key.getDescriptionId() + ".description";
					List<FormattedCharSequence> list;
					String string3;
					if (I18n.exists(string2)) {
						Builder<FormattedCharSequence> builder = ImmutableList.<FormattedCharSequence>builder().add(component2.getVisualOrderText());
						Component component4 = Component.translatable(string2);
						EditGameRulesScreen.this.font.split(component4, 150).forEach(builder::add);
						list = builder.add(component3.getVisualOrderText()).build();
						string3 = component4.getString() + "\n" + component3.getString();
					} else {
						list = ImmutableList.of(component2.getVisualOrderText(), component3.getVisualOrderText());
						string3 = component3.getString();
					}

					((Map)map.computeIfAbsent(key.getCategory(), category -> Maps.newHashMap())).put(key, entryFactory.create(component, list, string3, value));
				}
			});
			map.entrySet()
				.stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.forEach(
					entry -> {
						this.addEntry(
							EditGameRulesScreen.this.new CategoryRuleEntry(
								Component.translatable(((Category)entry.getKey()).getDescriptionId()).withStyle(new ChatFormatting[]{ChatFormatting.BOLD, ChatFormatting.YELLOW})
							)
						);
						((Map)entry.getValue())
							.entrySet()
							.stream()
							.sorted(java.util.Map.Entry.comparingByKey(Comparator.comparing((GameRules.Key<?> key) -> key.getId())))
							.forEach(entryx -> this.addEntry((EditGameRulesScreen.RuleEntry)((Entry<?, ?>)entryx).getValue()));
					}
				);
		}

		@Override
		public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			super.renderWidget(guiGraphics, i, j, f);
			EditGameRulesScreen.RuleEntry ruleEntry = this.getHovered();
			if (ruleEntry != null && ruleEntry.tooltip != null) {
				guiGraphics.setTooltipForNextFrame(ruleEntry.tooltip, i, j);
			}
		}
	}
}
