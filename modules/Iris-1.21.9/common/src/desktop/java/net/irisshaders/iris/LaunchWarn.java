package net.irisshaders.iris;

import javax.swing.*;
import java.awt.*;

/**
 * Launch warning - network functionality removed.
 * Now only shows a local message without attempting to open browser.
 */
public class LaunchWarn {
	public static void main(String[] args) {
		String message = DesktopBuildConfig.IS_SHARED_BETA
			? "If you're seeing this, you didn't read instructions.\n (Hint: This isn't a installer. It's a mod.)"
			: "This file is the mod version of Iris, meant to be installed as a mod.";
		
		if (GraphicsEnvironment.isHeadless()) {
			System.err.println(message);
		} else {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
				// Ignored
			}

			JOptionPane.showMessageDialog(null, message, "Iris Installer", JOptionPane.INFORMATION_MESSAGE);
		}

		System.exit(0);
	}
}
