package net.distanthorizons.common.wrappers.gui;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import net.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;
import net.distanthorizons.common.wrappers.gui.config.ConfigGuiInfo;
import net.distanthorizons.core.config.Config;
import net.distanthorizons.core.config.ConfigHandler;
import net.distanthorizons.core.config.types.*;
import net.distanthorizons.common.wrappers.gui.updater.ChangelogScreen;

import net.distanthorizons.core.config.types.*;
import net.distanthorizons.core.config.types.enums.EConfigCommentTextPosition;
import net.distanthorizons.core.config.types.enums.EConfigValidity;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.jar.updater.SelfUpdater;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.util.AnnotationUtil;
import net.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import net.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import net.distanthorizons.coreapi.ModInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.narration.NarratableEntry;

import net.minecraft.resources.ResourceLocation;

import static net.distanthorizons.common.wrappers.gui.GuiHelper.*;
import static net.distanthorizons.common.wrappers.gui.GuiHelper.Translatable;


/*
 * Based upon TinyConfig but is highly modified
 * https://github.com/Minenash/TinyConfig
 *
 * Note: floats don't work with this system, use doubles.
 *
 * @author coolGi
 * @author Motschen
 * @author James Seibel
 * @version 5-21-2022
 */
@SuppressWarnings("unchecked")
public class ClassicConfigGUI
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	public static final DhLogger RATE_LIMITED_LOGGER = new DhLoggerBuilder()
			.maxCountPerSecond(1)
			.build();
	
	public static final ConfigCoreInterface CONFIG_CORE_INTERFACE = new ConfigCoreInterface();
	
	
	
	//==============//
	// Initializers //
	//==============//
	
	// Some regexes to check if an input is valid
	private static final Pattern INTEGER_ONLY_REGEX = Pattern.compile("(-?[0-9]*)");
	private static final Pattern DECIMAL_ONLY_REGEX = Pattern.compile("-?([\\d]+\\.?[\\d]*|[\\d]*\\.?[\\d]+|\\.)");
	
	private static class ConfigScreenConfigs
	{
		// This contains all the configs for the configs
		public static final int SPACE_FROM_RIGHT_SCREEN = 10;
		public static final int SPACE_BETWEEN_TEXT_AND_OPTION_FIELD = 8;
		public static final int BUTTON_WIDTH_SPACING = 5;
		public static final int RESET_BUTTON_WIDTH = 60;
		public static final int RESET_BUTTON_HEIGHT = 20;
		public static final int OPTION_FIELD_WIDTH = 150;
		public static final int OPTION_FIELD_HEIGHT = 20;
		public static final int CATEGORY_BUTTON_WIDTH = 200;
		public static final int CATEGORY_BUTTON_HEIGHT = 20;
		
	}
	
	
	
	//==============//
	// GUI handling //
	//==============//
	
	/** if you want to get this config gui's screen call this */
	public static Screen getScreen(Screen parent, String category)
	{ return new ConfigScreen(parent, category); }
	
	private static class ConfigScreen extends DhScreen
	{
		private static final ILangWrapper LANG_WRAPPER = SingletonInjector.INSTANCE.get(ILangWrapper.class);
		
		private static final String TRANSLATION_PREFIX = ModInfo.ID + ".config.";
		
		
		private final Screen parent;
		private final String category;
		private ConfigListWidget configListWidget;
		private boolean reload = false;
		
		private Button doneButton;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		protected ConfigScreen(Screen parent, String category)
		{
			super(Translatable(
					LANG_WRAPPER.langExists(ModInfo.ID + ".config" + (category.isEmpty() ? "." + category : "") + ".title") ?
							ModInfo.ID + ".config.title" :
							ModInfo.ID + ".config" + (category.isEmpty() ? "" : "." + category) + ".title")
			);
			this.parent = parent;
			this.category = category;
		}
		
		
		@Override
		public void tick() { super.tick(); }
		
		
		
		//==================//
		// menu UI creation //
		//==================//
		
		@Override
		protected void init()
		{
			super.init();
			if (!this.reload)
			{
				ConfigHandler.INSTANCE.configFileHandler.loadFromFile();
			}
			
			// Changelog button
			if (Config.Client.Advanced.AutoUpdater.enableAutoUpdater.get()
				// we only have changelogs for stable builds		
				&& !ModInfo.IS_DEV_BUILD)
			{
				this.addBtn(new TexturedButtonWidget(
						// Where the button is on the screen
						this.width - 28, this.height - 28,
						// Width and height of the button
						20, 20,
						// texture UV Offset
						0, 0,
						// Some texture stuff
						0, 
						ResourceLocation.fromNamespaceAndPath(ModInfo.ID, "textures/gui/changelog.png"),
						20, 20,
						// Create the button and tell it where to go
						(buttonWidget) -> {
							ChangelogScreen changelogScreen = new ChangelogScreen(this);
							if (changelogScreen.usable)
							{
								Objects.requireNonNull(this.minecraft).setScreen(changelogScreen);
							}
							else
							{
								LOGGER.warn("Changelog was not able to open");
							}
						},
						// Add a title to the button
						Translatable(ModInfo.ID + ".updater.title")
				));
			}
			
			
			// back button
			this.addBtn(MakeBtn(Translatable("distanthorizons.general.back"),
					(this.width / 2) - 154, this.height - 28,
					ConfigScreenConfigs.OPTION_FIELD_WIDTH, ConfigScreenConfigs.OPTION_FIELD_HEIGHT,
					(button) -> 
					{
						ConfigHandler.INSTANCE.configFileHandler.loadFromFile();
						Objects.requireNonNull(this.minecraft).setScreen(this.parent);
					}));
			
			// done/close button
			this.doneButton = this.addBtn(
					MakeBtn(Translatable("distanthorizons.general.done"),
							(this.width / 2) + 4, this.height - 28,
							ConfigScreenConfigs.OPTION_FIELD_WIDTH, ConfigScreenConfigs.OPTION_FIELD_HEIGHT, 
					(button) -> 
					{
						ConfigHandler.INSTANCE.configFileHandler.saveToFile();
						Objects.requireNonNull(this.minecraft).setScreen(this.parent);
					}));
			
			this.configListWidget = new ConfigListWidget(this.minecraft, this.width * 2, this.height, 32, 32, 25);
			
			
			this.addWidget(this.configListWidget);
			
			for (AbstractConfigBase<?> configEntry : ConfigHandler.INSTANCE.configBaseList)
			{
				try
				{
					if (configEntry.getCategory().matches(this.category) 
						&& configEntry.getAppearance().showInGui)
					{
						this.addMenuItem(configEntry);
					}
				}
				catch (Exception e)
				{
					String message = "ERROR: Failed to show [" + configEntry.getNameAndCategory() + "], error: ["+e.getMessage()+"]";
					if (configEntry.get() != null)
					{
						message += " with the value [" + configEntry.get() + "] with type [" + configEntry.getType() + "]";
					}
					
					LOGGER.error(message, e);
				}
			}
			
			CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());
		}
		private void addMenuItem(AbstractConfigBase<?> configEntry)
		{
			trySetupConfigEntry(configEntry);
			
			if (this.tryCreateInputField(configEntry)) return;
			if (this.tryCreateCategoryButton(configEntry)) return;
			if (this.tryCreateButton(configEntry)) return;
			if (this.tryCreateComment(configEntry)) return;
			if (this.tryCreateSpacer(configEntry)) return;
			if (this.tryCreateLinkedEntry(configEntry)) return;
			
			LOGGER.warn("Config [" + configEntry.getNameAndCategory() + "] failed to show. Please try something like changing its type.");
		}
		
		private static void trySetupConfigEntry(AbstractConfigBase<?> configMenuOption)
		{
			configMenuOption.guiValue = new ConfigGuiInfo();
			Class<?> configValueClass = configMenuOption.getType();
			
			if (configMenuOption instanceof ConfigEntry)
			{
				ConfigEntry<?> configEntry = (ConfigEntry<?>) configMenuOption;
				
				if (configValueClass == Integer.class)
				{
					setupTextMenuOption(configEntry, Integer::parseInt, INTEGER_ONLY_REGEX, true);
				}
				else if (configValueClass == Double.class)
				{
					setupTextMenuOption(configEntry, Double::parseDouble, DECIMAL_ONLY_REGEX, false);
				}
				else if (configValueClass == String.class || configValueClass == List.class)
				{
					// For string or list
					setupTextMenuOption(configEntry, String::length, null, true);
				}
				else if (configValueClass == Boolean.class)
				{
					ConfigEntry<Boolean> booleanConfigEntry = (ConfigEntry<Boolean>) configEntry;
					setupBooleanMenuOption(booleanConfigEntry);
				}
				else if (configValueClass.isEnum())
				{
					ConfigEntry<Enum<?>> enumConfigEntry = (ConfigEntry<Enum<?>>) configEntry;
					Class<? extends Enum<?>> configEnumClass = (Class<? extends Enum<?>>) configValueClass;
					setupEnumMenuOption(enumConfigEntry, configEnumClass);
				}
			}
			
		}
		private static void setupTextMenuOption(AbstractConfigBase<?> configMenuOption, Function<String, Number> parsingFunc, @Nullable Pattern pattern, boolean cast)
		{
			final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) configMenuOption.guiValue);
			
			configGuiInfo.tooltipFunction =  
					(editBox, button) -> 
					(stringValue) ->
			{
				boolean isNumber = (pattern != null);
				
				stringValue = stringValue.trim();
				if (!(stringValue.isEmpty() || !isNumber || pattern.matcher(stringValue).matches()))
				{
					return false;
				}
				
				
				Number numberValue = configMenuOption.typeIsFloatingPointNumber() ? 0.0 : 0; // different default values are needed so implicit casting works correctly (if not done casting from 0 (an int) to a double will cause an exception)
				configGuiInfo.errorMessage = null;
				if (isNumber 
					&& !stringValue.isEmpty() 
					&& !stringValue.equals("-") 
					&& !stringValue.equals("."))
				{
					ConfigEntry<Number> numberConfigEntry = (ConfigEntry<Number>) configMenuOption;
					
					try
					{
						numberValue = parsingFunc.apply(stringValue);
					}
					catch (Exception e)
					{
						numberValue = null;
					}
					
					EConfigValidity validity = numberConfigEntry.getValidity(numberValue);
					switch (validity)
					{
						case VALID:
							configGuiInfo.errorMessage = null;
							break;
						case NUMBER_TOO_LOW:
							configGuiInfo.errorMessage = TextOrTranslatable("§cMinimum length is " + numberConfigEntry.getMin());
							break;
						case NUMBER_TOO_HIGH:
							configGuiInfo.errorMessage = TextOrTranslatable("§cMaximum length is " + numberConfigEntry.getMax());
							break;
						case INVALID:
							configGuiInfo.errorMessage = TextOrTranslatable("§cValue is invalid");
							break;
					}
				}
				
				editBox.setTextColor(((ConfigEntry<Number>) configMenuOption).getValidity(numberValue) == EConfigValidity.VALID ? 0xFFFFFFFF : 0xFFFF7777); // white and red
				
				
				if (configMenuOption.getType() == String.class
					|| configMenuOption.getType() == List.class)
				{
					((ConfigEntry<String>) configMenuOption).uiSetWithoutSaving(stringValue);
				}
				else if (((ConfigEntry<Number>) configMenuOption).getValidity(numberValue) == EConfigValidity.VALID)
				{
					if (!cast)
					{
						((ConfigEntry<Number>) configMenuOption).uiSetWithoutSaving(numberValue);
					}
					else
					{
						((ConfigEntry<Number>) configMenuOption).uiSetWithoutSaving(numberValue != null ? numberValue.intValue() : 0);
					}
				}
				
				return true;
			};
		}
		private static void setupBooleanMenuOption(ConfigEntry<Boolean> booleanConfigEntry)
		{
			// For boolean
			Function<Object, Component> func = value -> Translatable("distanthorizons.general."+((Boolean) value ? "true" : "false")).withStyle((Boolean) value ? ChatFormatting.GREEN : ChatFormatting.RED);
			
			final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) booleanConfigEntry.guiValue);
			
			configGuiInfo.buttonOptionMap =
					new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(
							(button) ->
							{
								button.active = !booleanConfigEntry.apiIsOverriding();
								
								booleanConfigEntry.uiSetWithoutSaving(!booleanConfigEntry.get());
								button.setMessage(func.apply(booleanConfigEntry.get()));
							}, func);
		}
		private static void setupEnumMenuOption(ConfigEntry<Enum<?>> enumConfigEntry, Class<? extends Enum<?>> enumClass)
		{
			List<Enum<?>> enumList = Arrays.asList(enumClass.getEnumConstants());
			
			final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) enumConfigEntry.guiValue);
			
			Function<Object, Component> getEnumTranslatableFunc = (value) -> Translatable(TRANSLATION_PREFIX + "enum." + enumClass.getSimpleName() + "." + enumConfigEntry.get().toString());
			configGuiInfo.buttonOptionMap = 
					new AbstractMap.SimpleEntry<Button.OnPress, Function<Object, Component>>(
					(button) ->
			{
				// get the currently selected enum and enum index
				int startingIndex = enumList.indexOf(enumConfigEntry.get());
				Enum<?> enumValue = enumList.get(startingIndex);
				
				// search for the next enum that is selectable
				int index = startingIndex + 1;
				index = (index >= enumList.size()) ? 0 : index;
				while (index != startingIndex)
				{
					enumValue = enumList.get(index);
					if (!AnnotationUtil.doesEnumHaveAnnotation(enumValue, DisallowSelectingViaConfigGui.class))
					{
						// this enum shouldn't be selectable via the UI,
						// skip it
						break;
					}
					
					index++;
					index = (index >= enumList.size()) ? 0 : index;
				}
				
				if (index == startingIndex)
				{
					// none of the enums should be selectable, this is a programmer error
					enumValue = enumList.get(startingIndex);
					LOGGER.warn("Enum [" + enumValue.getClass() + "] doesn't contain any values that should be selectable via the UI, sticking to the currently selected value [" + enumValue + "].");
				}
				
				
				enumConfigEntry.uiSetWithoutSaving(enumValue);
				
				button.active = !enumConfigEntry.apiIsOverriding();
				
				button.setMessage(getEnumTranslatableFunc.apply(enumConfigEntry.get()));
			}, getEnumTranslatableFunc);
		}
		
		private boolean tryCreateInputField(AbstractConfigBase<?> configBase)
		{
			final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) configBase.guiValue);
			
			if (configBase instanceof ConfigEntry)
			{
				ConfigEntry configEntry = (ConfigEntry) configBase;
				
				
				//==============//
				// reset button //
				//==============//
				
				Button.OnPress btnAction = (button) ->
				{
					configEntry.uiSetWithoutSaving(configEntry.getDefaultValue());
					this.reload = true;
					Objects.requireNonNull(this.minecraft).setScreen(this);
				};
				
				int resetButtonPosX = this.width
						- ConfigScreenConfigs.RESET_BUTTON_WIDTH
						- ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
				int resetButtonPosZ = 0;
				
				Button resetButton = MakeBtn(
						Translatable("distanthorizons.general.reset").withStyle(ChatFormatting.RED),
						resetButtonPosX, resetButtonPosZ,
						ConfigScreenConfigs.RESET_BUTTON_WIDTH, ConfigScreenConfigs.RESET_BUTTON_HEIGHT,
						btnAction);
				
				if (configEntry.apiIsOverriding())
				{
					resetButton.active = false;
					resetButton.setMessage(Translatable("distanthorizons.general.apiOverride").withStyle(ChatFormatting.DARK_GRAY));
				}
				else
				{
					resetButton.active = true;
				}
				
				
				
				//==============//
				// option field //
				//==============//
				
				Component textComponent = this.GetTranslatableTextComponentForConfig(configEntry);
				
				int optionFieldPosX = this.width
						- ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN
						- ConfigScreenConfigs.RESET_BUTTON_WIDTH
						- ConfigScreenConfigs.BUTTON_WIDTH_SPACING
						- ConfigScreenConfigs.OPTION_FIELD_WIDTH;
				int optionFieldPosZ = 0;
				
				if (configGuiInfo.buttonOptionMap != null)
				{
					// enum/multi option input button
					
					Map.Entry<Button.OnPress, Function<Object, Component>> widget = configGuiInfo.buttonOptionMap;
					if (configEntry.getType().isEnum())
					{
						widget.setValue((value) -> Translatable(TRANSLATION_PREFIX + "enum." + configEntry.getType().getSimpleName() + "." + configEntry.get().toString()));
					}
					
					Button button = MakeBtn(
							widget.getValue().apply(configEntry.get()),
							optionFieldPosX, optionFieldPosZ,
							ConfigScreenConfigs.OPTION_FIELD_WIDTH, ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
							widget.getKey());
					
					// deactivate the button if the API is overriding it
					button.active = !configEntry.apiIsOverriding();
					
					
					this.configListWidget.addButton(this, configEntry,
							button,
							resetButton,
							null,
							textComponent);
					
					return true;
				}
				else
				{
					// text box input
					
					EditBox widget = new EditBox(this.font,
							optionFieldPosX, optionFieldPosZ,
							ConfigScreenConfigs.OPTION_FIELD_WIDTH - 4, ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
							Translatable(""));
					widget.setMaxLength(ConfigScreenConfigs.OPTION_FIELD_WIDTH);
					widget.insertText(String.valueOf(configEntry.get()));
					
					Predicate<String> processor = configGuiInfo.tooltipFunction.apply(widget, this.doneButton);
					widget.setFilter(processor);
					
					this.configListWidget.addButton(this, configEntry, widget, resetButton, null, textComponent);
					
					return true;
				}
			}
			
			return false;
		}
		private boolean tryCreateCategoryButton(AbstractConfigBase<?> configType)
		{
			if (configType instanceof ConfigCategory)
			{
				ConfigCategory configCategory = (ConfigCategory) configType;
				
				Component textComponent = this.GetTranslatableTextComponentForConfig(configCategory);
				
				int categoryPosX = this.width - ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH - ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
				int categoryPosZ = this.height - ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT; // Note: the posZ value here seems to be ignored
				
				Button widget = MakeBtn(textComponent,
						categoryPosX, categoryPosZ,
						ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH, ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
						((button) ->
						{
							ConfigHandler.INSTANCE.configFileHandler.saveToFile();
							Objects.requireNonNull(this.minecraft).setScreen(ClassicConfigGUI.getScreen(this, configCategory.getDestination()));
						}));
				this.configListWidget.addButton(this, configType, widget, null, null, null);
				
				return true;
			}
			
			return false;
		}
		private boolean tryCreateButton(AbstractConfigBase<?> configType)
		{
			if (configType instanceof ConfigUIButton)
			{
				ConfigUIButton configUiButton = (ConfigUIButton) configType;
				
				Component textComponent = this.GetTranslatableTextComponentForConfig(configUiButton);
				
				int buttonPosX = this.width - ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH - ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
				
				Button widget = MakeBtn(textComponent,
						buttonPosX, this.height - 28,
						ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH, ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
						(button) -> ((ConfigUIButton) configType).runAction());
				this.configListWidget.addButton(this, configType, widget, null, null, null);
				
				return true;
			}
			
			return false;
		}
		private boolean tryCreateComment(AbstractConfigBase<?> configType)
		{
			if (configType instanceof ConfigUIComment)
			{
				ConfigUIComment configUiComment = (ConfigUIComment) configType;
			
				Component textComponent = this.GetTranslatableTextComponentForConfig(configUiComment);
				if (configUiComment.parentConfigPath != null)
				{
					textComponent = Translatable(TRANSLATION_PREFIX + configUiComment.parentConfigPath);
				}
				
				this.configListWidget.addButton(this, configType, null, null, null, textComponent);
				
				return true;
			}
			
			return false;
		}
		private boolean tryCreateSpacer(AbstractConfigBase<?> configType)
		{
			if (configType instanceof ConfigUISpacer)
			{
				Button spacerButton = MakeBtn(Translatable("distanthorizons.general.spacer"),
						10, 10, // having too small of a size causes division by 0 errors in older MC versions (IE 1.20.1)
						1, 1,
						(button) -> {});
				
				spacerButton.visible = false;
				this.configListWidget.addButton(this, configType, spacerButton, null, null, null);
				
				return true;
			}
			
			return false;
		}
		private boolean tryCreateLinkedEntry(AbstractConfigBase<?> configType)
		{
			if (configType instanceof ConfigUiLinkedEntry)
			{
				this.addMenuItem(((ConfigUiLinkedEntry) configType).get());
				
				return true;
			}
			
			return false;
		}
		
		private Component GetTranslatableTextComponentForConfig(AbstractConfigBase<?> configType)
		{ return Translatable(TRANSLATION_PREFIX + configType.getNameAndCategory());}
		
		
		
		//===========//
		// rendering //
		//===========//
		
		@Override
		public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta)
		{
			super.render(matrices, mouseX, mouseY, delta);
			
			this.configListWidget.render(matrices, mouseX, mouseY, delta); // Render buttons
			
			
			// Render config title
			this.DhDrawCenteredString(matrices, this.font, this.title, 
					this.width / 2, 15, 
					0xFFFFFFFF); // ARGB white
			
			
			// render DH version
			this.DhDrawString(matrices, this.font, TextOrLiteral(ModInfo.VERSION), 2, this.height - 10, 
					0xFFAAAAAA); // ARGB white
			
			// If the update is pending, display this message to inform the user that it will apply when the game restarts
			if (SelfUpdater.deleteOldJarOnJvmShutdown)
			{
				this.DhDrawString(matrices, this.font, Translatable(ModInfo.ID + ".updater.waitingForClose"), 4, this.height - 42, 
						0xFFFFFFFF); // ARGB white
			}
			
			
			this.renderTooltip(matrices, mouseX, mouseY, delta);
			
		}
		
		private void renderTooltip(GuiGraphics matrices, int mouseX, int mouseY, float delta)
		{
			AbstractWidget hoveredWidget = this.configListWidget.getHoveredButton(mouseX, mouseY);
			if (hoveredWidget == null)
			{
				return;
			}
			
			
			ButtonEntry button = ButtonEntry.BUTTON_BY_WIDGET.get(hoveredWidget);
			
			
			// A quick fix for tooltips on linked entries
			AbstractConfigBase<?> configBase = ConfigUiLinkedEntry.class.isAssignableFrom(button.dhConfigType.getClass()) ?
					((ConfigUiLinkedEntry) button.dhConfigType).get() :
					button.dhConfigType;
			
			boolean apiOverrideActive = false;
			if (configBase instanceof ConfigEntry)
			{
				apiOverrideActive = ((ConfigEntry<?>)configBase).apiIsOverriding();
			}
			
			String key = TRANSLATION_PREFIX + (configBase.category.isEmpty() ? "" : configBase.category + ".") + configBase.getName() + ".@tooltip";
			
			if (apiOverrideActive)
			{
				key = "distanthorizons.general.disabledByApi.@tooltip";
			}
			
			// display the validation error tooltip if present
			final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) configBase.guiValue);
			if (configGuiInfo.errorMessage != null)
			{ 
				this.DhRenderTooltip(matrices, this.font, configGuiInfo.errorMessage, mouseX, mouseY);
			}
			// display the tooltip if present
			else if (LANG_WRAPPER.langExists(key))
			{
				List<Component> list = new ArrayList<>();
				String lang = LANG_WRAPPER.getLang(key);
				for (String langLine : lang.split("\n"))
				{
					list.add(TextOrTranslatable(langLine));
				}
				
				this.DhRenderComponentTooltip(matrices, this.font, list, mouseX, mouseY);
			}
		}
		
		
		
		//==========//
		// shutdown //
		//==========//
		
		/** When you close it, it goes to the previous screen and saves */
		@Override
		public void onClose()
		{
			ConfigHandler.INSTANCE.configFileHandler.saveToFile();
			Objects.requireNonNull(this.minecraft).setScreen(this.parent);
			
			CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());
		}
		
		
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class ConfigListWidget extends ContainerObjectSelectionList<ButtonEntry>
	{
		Font textRenderer;
		
		public ConfigListWidget(Minecraft minecraftClient, int canvasWidth, int canvasHeight, int topMargin, int botMargin, int itemSpacing)
		{
			super(minecraftClient, canvasWidth, canvasHeight - (topMargin + botMargin), topMargin, itemSpacing);
			
			this.centerListVertically = false;
			this.textRenderer = minecraftClient.font;
		}
		
		public void addButton(ConfigScreen gui, AbstractConfigBase dhConfigType, AbstractWidget button, AbstractWidget resetButton, AbstractWidget indexButton, Component text)
		{ this.addEntry(new ButtonEntry(gui, dhConfigType, button, text, resetButton, indexButton)); }
		
		@Override
		public int getRowWidth() { return 10_000; }
		
		public AbstractWidget getHoveredButton(double mouseX, double mouseY)
		{
			for (ButtonEntry buttonEntry : this.children())
			{
				AbstractWidget button = buttonEntry.button;
				if (button != null 
					&& button.visible)
				{
					double minX = button.getX();
					double minY = button.getY();
					
					double maxX = minX + button.getWidth();
					double maxY = minY + button.getHeight();
					
					if (mouseX >= minX && mouseX < maxX
						&& mouseY >= minY && mouseY < maxY)
					{
						return button;
					}
				}
			}
			
			return null;
		}
		
	}
	
	
	public static class ButtonEntry extends ContainerObjectSelectionList.Entry<ButtonEntry>
	{
		private static final Font textRenderer = Minecraft.getInstance().font;
		
		private final AbstractWidget button;
		
		private final ConfigScreen gui;
		private final AbstractConfigBase dhConfigType;
		
		private final AbstractWidget resetButton;
		private final AbstractWidget indexButton;
		private final Component text;
		private final List<AbstractWidget> children = new ArrayList<>();
		
		@NotNull
		private final EConfigCommentTextPosition textPosition;
		
		public static final Map<AbstractWidget, Component> TEXT_BY_WIDGET = new HashMap<>();
		/// TODO we should just use a wrapper or something
		public static final Map<AbstractWidget, ButtonEntry> BUTTON_BY_WIDGET = new HashMap<>();
		
		
		
		public ButtonEntry(ConfigScreen gui, AbstractConfigBase dhConfigType, 
				AbstractWidget button, Component text, AbstractWidget resetButton, AbstractWidget indexButton)
		{
			TEXT_BY_WIDGET.put(button, text);
			BUTTON_BY_WIDGET.put(button, this);
			
			this.gui = gui;
			this.dhConfigType = dhConfigType;
			
			this.button = button;
			this.resetButton = resetButton;
			this.text = text;
			this.indexButton = indexButton;
			
			if (button != null) { this.children.add(button); }
			if (resetButton != null) { this.children.add(resetButton); }
			if (indexButton != null) { this.children.add(indexButton); }
			
			
			EConfigCommentTextPosition textPosition = null;
			if (this.dhConfigType instanceof ConfigUIComment)
			{
				textPosition = ((ConfigUIComment)this.dhConfigType).textPosition;
			}
			
			if (textPosition == null)
			{
				if (this.button != null)
				{
					// if a button is present
					textPosition = EConfigCommentTextPosition.RIGHT_JUSTIFIED;
				}
				else
				{
					textPosition = EConfigCommentTextPosition.CENTERED_OVER_BUTTONS;
				}
			}
			this.textPosition = textPosition;
			
		}
		
		
		
		@Override
		public void renderContent(GuiGraphics matrices, int mouseX, int mouseY, boolean hovered, float tickDelta)
		{
			try
			{
				int y = this.getY(); /// TODO why is the Y value being set during render?
				
				if (this.button != null)
				{
					SetY(this.button, y);
					this.button.render(matrices, mouseX, mouseY, tickDelta);
				}
				
				if (this.resetButton != null)
				{
					SetY(this.resetButton, y);
					this.resetButton.render(matrices, mouseX, mouseY, tickDelta);
				}
				
				if (this.indexButton != null)
				{
					SetY(this.indexButton, y);
					this.indexButton.render(matrices, mouseX, mouseY, tickDelta);
				}
				
				if (this.text != null)
				{
					int translatedLength = textRenderer.width(this.text);
					
					int textXPos;
					if (this.textPosition == EConfigCommentTextPosition.RIGHT_JUSTIFIED)
					{
						// text right justified aligned against the buttons
						textXPos = this.gui.width
								- translatedLength
								- ConfigScreenConfigs.SPACE_BETWEEN_TEXT_AND_OPTION_FIELD
								- ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN
								- ConfigScreenConfigs.OPTION_FIELD_WIDTH
								- ConfigScreenConfigs.BUTTON_WIDTH_SPACING
								- ConfigScreenConfigs.RESET_BUTTON_WIDTH;
					}
					else if (this.textPosition == EConfigCommentTextPosition.CENTERED_OVER_BUTTONS)
					{
						// have button centered relative to a category button
						textXPos = this.gui.width
								- (translatedLength / 2)
								- (ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH / 2)
								- ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
					}
					else if (this.textPosition == EConfigCommentTextPosition.CENTER_OF_SCREEN)
					{
						// have button centered in the screen
						textXPos = (this.gui.width / 2)
								- (translatedLength / 2);
					}
					else
					{
						throw new UnsupportedOperationException("No text position render defined for [" + this.textPosition + "]");
					}
				
				
				matrices.drawString(textRenderer, 
						this.text,
						textXPos, y + 5, 
						0xFFFFFFFF);
				}
			}
			catch (Exception e)
			{
				// should prevent crashing the game if there's an issue
				RATE_LIMITED_LOGGER.error("Unexpected gui rendering issue: ["+e.getMessage()+"]", e);
			}
		}
		
		@Override
		public @NotNull List<? extends GuiEventListener> children()
		{ return this.children; }
		
		@Override
		public @NotNull List<? extends NarratableEntry> narratables()
		{ return this.children; }
		
		
		
	}
	
	
	
	//================//
	// event handling //
	//================//
	
	public static class ConfigCoreInterface implements IConfigGui
	{
		/**
		 * in the future it would be good to pass in the current page and other variables, 
		 * but for now just knowing when the page is closed is good enough 
		 */
		public final ArrayList<Runnable> onScreenChangeListenerList = new ArrayList<>();
		
		
		
		@Override
		public void addOnScreenChangeListener(Runnable newListener) { this.onScreenChangeListenerList.add(newListener); }
		@Override
		public void removeOnScreenChangeListener(Runnable oldListener) { this.onScreenChangeListenerList.remove(oldListener); }
		
	}
	
}
