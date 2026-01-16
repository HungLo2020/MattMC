package com.seibel.distanthorizons.core.jar.gui.cusomJObject;

import javax.swing.*;
import java.awt.*;

/**
 * A rectangular box that can be placed with java swing
 *
 * @author coolGi
 */
public class JBox extends JComponent
{
	private static final String uiClassID = "BoxBarUI";
	
	private Color color;
	
	private int x;
	private int y;
	private int width;
	private int height;
	
	public JBox()
	{
		this(null);
	}
	
	public JBox(Color color)
	{
		this.color = color;
	}
	
	public JBox(Color color, Rectangle rectangle)
	{
		this(color, rectangle.x, rectangle.y, rectangle.width, rectangle.height);
	}
	
	public JBox(Color color, int x, int y, int width, int height)
	{
		this.color = color;
		setBounds(x, y, width, height);
	}
	
	public void setColor(Color color)
	{
		this.color = color;
	}
	
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		g.setColor(color);
		g.fillRect(0, 0, getBounds().width, getBounds().height);
	}
	
}
