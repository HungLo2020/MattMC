package com.seibel.distanthorizons.core.config.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class ConfigScreen extends JComponent
{
	
	public ConfigScreen()
	{
		setLayout(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 0.5;
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(10, 10, 0, 10);
		
		add(new JLabel("Hello World!"), constraints);
	}
	
	
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame();
			
			frame.add(new ConfigScreen());
			
			frame.setSize(300, 200);
			frame.setLocationRelativeTo(null); // Makes the window open at the center of the screen
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setVisible(true);
		});
	}
	
}
