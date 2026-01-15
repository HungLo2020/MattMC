package net.minecraft.client.resources.language;

import net.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.locale.DeprecatedTranslationsInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientLanguage extends Language {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Map<String, String> storage;
	private final boolean defaultRightToLeft;
	
	// Iris: From MixinClientLanguage - track language codes for shaderpack translations
	private static final List<String> iris$languageCodes = new java.util.ArrayList<>();

	private ClientLanguage(Map<String, String> map, boolean bl) {
		this.storage = map;
		this.defaultRightToLeft = bl;
	}

	public static ClientLanguage loadFrom(ResourceManager resourceManager, List<String> list, boolean bl) {
		// Iris: From MixinClientLanguage - clear language codes at start
		iris$languageCodes.clear();
		new java.util.LinkedList<>(list).descendingIterator().forEachRemaining(iris$languageCodes::add);
		
		Map<String, String> map = new HashMap();

		for (String string : list) {
			String string2 = String.format(Locale.ROOT, "lang/%s.json", string);

			for (String string3 : resourceManager.getNamespaces()) {
				try {
					ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(string3, string2);
					appendFrom(string, resourceManager.getResourceStack(resourceLocation), map);
				} catch (Exception var10) {
					LOGGER.warn("Skipped language file: {}:{} ({})", string3, string2, var10.toString());
				}
			}
		}

		DeprecatedTranslationsInfo.loadFromDefaultResource().applyToMap(map);
		return new ClientLanguage(Map.copyOf(map), bl);
	}

	private static void appendFrom(String string, List<Resource> list, Map<String, String> map) {
		// Iris: From MixinClientLanguage - load Iris built-in language files
		String json = String.format(Locale.ROOT, "lang/%s.json", string);
		if (net.irisshaders.iris.Iris.class.getResource("/assets/iris/" + json) != null) {
			Language.loadFromJson(net.irisshaders.iris.Iris.class.getResourceAsStream("/assets/iris/" + json), map::put);
		}
		
		for (Resource resource : list) {
			try {
				InputStream inputStream = resource.open();

				try {
					Language.loadFromJson(inputStream, map::put);
				} catch (Throwable var9) {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (Throwable var8) {
							var9.addSuppressed(var8);
						}
					}

					throw var9;
				}

				if (inputStream != null) {
					inputStream.close();
				}
			} catch (IOException var10) {
				LOGGER.warn("Failed to load translations for {} from pack {}", string, resource.sourcePackId(), var10);
			}
		}
	}

	public String getOrDefault(String string, String string2) {
		// Iris: From MixinClientLanguage - check shaderpack language overrides first
		String override = iris$lookupOverriddenEntry(string);
		if (override != null) {
			return override;
		}
		
		return (String)this.storage.getOrDefault(string, string2);
	}

	public boolean has(String string) {
		// Iris: From MixinClientLanguage - check shaderpack language overrides
		String override = iris$lookupOverriddenEntry(string);
		if (override != null) {
			return true;
		}
		
		return this.storage.containsKey(string);
	}
	
	// Iris: From MixinClientLanguage - lookup shaderpack language entries
	private String iris$lookupOverriddenEntry(String key) {
		net.irisshaders.iris.shaderpack.ShaderPack pack = net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
		
		if (pack == null) {
			return null;
		}
		
		net.irisshaders.iris.shaderpack.LanguageMap languageMap = pack.getLanguageMap();
		
		if (storage.containsKey(key)) {
			// Don't allow shader packs to override existing MC translations
			return null;
		}
		
		for (String code : iris$languageCodes) {
			Map<String, String> translations = languageMap.getTranslations(code);
			
			if (translations != null) {
				String translation = translations.get(key);
				
				if (translation != null) {
					return translation;
				}
			}
		}
		
		return null;
	}

	public boolean isDefaultRightToLeft() {
		return this.defaultRightToLeft;
	}

	public FormattedCharSequence getVisualOrder(FormattedText formattedText) {
		return FormattedBidiReorder.reorder(formattedText, this.defaultRightToLeft);
	}
}
