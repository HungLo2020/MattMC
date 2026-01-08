package net.fabricmc.loader.language;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.objectweb.asm.ClassReader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.LoaderUtil;

@Deprecated
public class JavaLanguageAdapter implements LanguageAdapter {
	private static boolean canApplyInterface(String itfString) throws IOException {
		// TODO: Be a bit more involved
		switch (itfString) {
		case "net/fabricmc/api/ClientModInitializer":
			if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
				return false;
			}

			break;
		case "net/fabricmc/api/DedicatedServerModInitializer":
			if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
				return false;
			}
		}

		InputStream stream = FabricLauncherBase.getLauncher().getResourceAsStream(LoaderUtil.getClassFileName(itfString));
		if (stream == null) return false;

		ClassReader reader = new ClassReader(stream);

		for (String s : reader.getInterfaces()) {
			if (!canApplyInterface(s)) {
				stream.close();
				return false;
			}
		}

		stream.close();
		return true;
	}

	public static Class<?> getClass(String className, Options options) throws ClassNotFoundException, IOException {
		InputStream stream = FabricLauncherBase.getLauncher().getResourceAsStream(LoaderUtil.getClassFileName(className));
		if (stream == null) throw new ClassNotFoundException("Could not find or load class " + className);

		ClassReader reader = new ClassReader(stream);

		for (String s : reader.getInterfaces()) {
			if (!canApplyInterface(s)) {
				switch (options.getMissingSuperclassBehavior()) {
				case RETURN_NULL:
					stream.close();
					return null;
				case CRASH:
				default:
					stream.close();
					throw new ClassNotFoundException("Could not find or load class " + s);
				}
			}
		}

		stream.close();
		return FabricLauncherBase.getClass(className);
	}

	@Override
	public Object createInstance(Class<?> modClass, Options options) throws LanguageAdapterException {
		try {
			Constructor<?> constructor = modClass.getDeclaredConstructor();
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new LanguageAdapterException("Could not find constructor for class " + modClass.getName() + "!", e);
		} catch (IllegalAccessException e) {
			throw new LanguageAdapterException("Could not access constructor of class " + modClass.getName() + "!", e);
		} catch (InvocationTargetException | IllegalArgumentException | InstantiationException e) {
			throw new LanguageAdapterException("Could not instantiate class " + modClass.getName() + "!", e);
		}
	}
}
