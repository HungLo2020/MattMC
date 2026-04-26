package com.seibel.distanthorizons.core.jar;

import com.seibel.distanthorizons.core.jar.gui.BaseJFrame;
import com.seibel.distanthorizons.core.jar.gui.cusomJObject.JBox;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.util.Arrays;
import java.util.List;

/**
 * The main class when you run the standalone jar.
 */
public class JarMain {
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	public static List<String> argList;

	public static void main(String[] args) {
		argList = Arrays.asList(args);

		if (!argList.contains("--no-custom-logger")) {
			LoggerContext context = (LoggerContext) LogManager.getContext(false);
			try {
				context.setConfigLocation(JarUtils.accessFileURI("/log4jConfig.xml"));
			} catch (Exception e) {
				LOGGER.error("Failed to set log4j config. Try running with the \"--no-custom-logger\" argument", e);
			}
		}

		LOGGER.debug("Running " + ModInfo.READABLE_NAME + " standalone jar");
		LOGGER.warn("The standalone jar is still a massive WIP");

		if (args.length == 0 || argList.contains("--gui")) {
			JarDependencySetup.createInitialBindings();
			startGUI();
			return;
		}

		LOGGER.warn("Standalone export and installer actions have been removed from this build.");
	}

	public static void startGUI() {
		BaseJFrame frame = new BaseJFrame(false, true);
		frame.addExtraButtons(frame.getWidth(), 0, true, false);
		frame.add(new JBox(UIManager.getColor("Separator.foreground"), 0, 220, frame.getWidth(), 5));

		JLabel titleLabel = new JLabel(ModInfo.READABLE_NAME + " standalone jar");
		titleLabel.setBounds(20, 200, frame.getWidth() - 40, 20);
		frame.add(titleLabel);

		JTextArea message = new JTextArea(
			"The standalone jar no longer includes any built-in update or installer subsystem.\n\n"
				+ "To use Distant Horizons, place the mod jar in your loader's mods folder manually."
		);
		message.setEditable(false);
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setOpaque(false);
		message.setBounds(20, 240, frame.getWidth() - 40, 80);
		frame.add(message);

		frame.addLogo();
		frame.validate();
		frame.setVisible(true);
	}
}
