/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.gamerule.widget;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screens.world.EditGameRulesScreen;
import net.minecraft.client.gui.widget.Button;
import net.minecraft.network.chat.OrderedText;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.gamerule.v1.rule.EnumRule;

public final class EnumRuleWidget<E extends Enum<E>> extends EditGameRulesScreen.NamedRuleWidget {
	private final Button buttonWidget;
	private final String rootTranslationKey;

	public EnumRuleWidget(EditGameRulesScreen gameRuleScreen, Component name, List<OrderedText> description, final String ruleName, EnumRule<E> rule, String translationKey) {
		gameRuleScreen.super(description, name);

		// Overwrite line wrapping to account for button larger than vanilla's by 44 pixels.
		this.name = Minecraft.getInstance().textRenderer.wrapLines(name, 175 - 44);

		// Base translation key needs to be set before the button widget is created.
		this.rootTranslationKey = translationKey;
		this.buttonWidget = Button.builder(this.getValueText(rule.get()), (buttonWidget) -> {
			rule.cycle();
			buttonWidget.setMessage(this.getValueText(rule.get()));
		}).position(10, 5).size(88, 20).build();

		this.children.add(this.buttonWidget);
	}

	public Component getValueText(E value) {
		final String key = this.rootTranslationKey + "." + value.name().toLowerCase(Locale.ROOT);
		return Component.translatableWithFallback(key, value.toString());
	}

	@Override
	public void render(DrawContext drawContext, int mouseX, int mouseY, boolean hovered, float tickDelta) {
		this.drawName(drawContext, this.getContentY(), this.getContentX());

		this.buttonWidget.setX(this.getContentRightEnd() - 89);
		this.buttonWidget.setY(this.getContentY());
		this.buttonWidget.render(drawContext, mouseX, mouseY, tickDelta);
	}
}
