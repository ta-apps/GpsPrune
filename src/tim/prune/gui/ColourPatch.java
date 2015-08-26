package tim.prune.gui;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JPanel;

import tim.prune.config.ColourUtils;

/**
 * Class to act as a colour patch to illustrate a chosen colour
 */
public class ColourPatch extends JPanel
{
	private static final long serialVersionUID = 3685900534546410455L;

	/**
	 * Constructor
	 */
	public ColourPatch(Color inColour)
	{
		Dimension size = new Dimension(80, 50);
		setMinimumSize(size);
		setPreferredSize(size);
		setColour(inColour);
	}

	/**
	 * Set the colour of the patch
	 * @param inColour Color to use
	 */
	public void setColour(Color inColour)
	{
		super.setBackground(inColour);
		setToolTipText(ColourUtils.makeHexCode(inColour));
	}
}