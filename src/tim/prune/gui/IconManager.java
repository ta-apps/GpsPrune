package tim.prune.gui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import tim.prune.I18nManager;

/**
 * Class to manage the loading of icons
 * for toolbars and map buttons
 */
public abstract class IconManager
{

	/** Icon for window */
	public static final String WINDOW_ICON = "window_icon.png";
	public static final String DRAW_WINDOW_ICON = "draw_mono.png";

	/** Icon for scalebar button on main map display */
	public static final String SCALEBAR_BUTTON = "scalebar.gif";
	/** Icon for map button on main map display when selected */
	public static final String SCALEBAR_BUTTON_ON = "scalebar_on.gif";
	/** Icon for map button on main map display */
	public static final String MAP_BUTTON = "map_icon.gif";
	/** Icon for map button on main map display when selected */
	public static final String MAP_BUTTON_ON = "map_icon_on.gif";
	/** Icon for autopan button on main map display */
	public static final String AUTOPAN_BUTTON = "autopan.gif";
	/** Icon for autopan button on main map display when selected */
	public static final String AUTOPAN_BUTTON_ON = "autopan_on.gif";
	/** Icon for points connected icon on main map display */
	public static final String POINTS_CONNECTED_BUTTON = "points_connected.gif";
	/** Icon for points disconnected icon on main map display */
	public static final String POINTS_DISCONNECTED_BUTTON = "points_disconnected.gif";
	/** Icon for zoom in button on main map display */
	public static final String ZOOM_IN_BUTTON = "zoom_in.gif";
	/** Icon for zoom out button on main map display */
	public static final String ZOOM_OUT_BUTTON = "zoom_out.gif";

	/** Icon for open file */
	public static final String OPEN_FILE = "add_textfile_icon.png";
	/** Icon for save */
	public static final String DRAW_OPEN_FILE = "selected_menu_open.png";
	/** Icon for add photo */
	public static final String ADD_PHOTO = "add_photo_icon.png";
	/** Icon for save */
	public static final String SAVE_FILE = "save_icon.gif";
	/** Icon for save */
	public static final String DRAW_SAVE_FILE = "selected_menu_save.png";
	/** Icon for undo */
	public static final String UNDO = "undo_icon.gif";
	/** Icon for undo */
	public static final String REDO = "redo_icon.gif";
	/** Icon for edit point */
	public static final String EDIT_POINT = "edit_point_icon.gif";
	/** Icon for delete point */
	public static final String DELETE_POINT = "delete_point_icon.gif";
	/** Icon for delete range */
	public static final String DELETE_RANGE = "delete_range_icon.gif";
	/** Icon for set range start */
	public static final String SET_RANGE_START = "set_start_icon.png";
	/** Icon for set range end */
	public static final String SET_RANGE_END = "set_end_icon.png";
	/** Icon for connect point to photo */
	public static final String CONNECT_PHOTO = "link.gif";
	/** Icon for cut range and move */
	public static final String CUT_AND_MOVE = "cut_and_move.gif";

	/** Icon for rotating photos leftwards */
	public static final String ROTATE_LEFT = "rotate_left_icon.png";
	/** Icon for rotating photos rightwards */
	public static final String ROTATE_RIGHT = "rotate_right_icon.png";
	/** Icon for showing photo popup */
	public static final String SHOW_DETAILS = "show_details_icon.gif";
	/** Icon for playing audio file */
	public static final String PLAY_AUDIO = "play_audio.gif";
	/** Icon for stopping the current audio file */
	public static final String STOP_AUDIO = "stop_audio.gif";

	/**
	 * Get the specified image
	 * @param inFilename filename of image (using constants)
	 * @return ImageIcon object containing image
	 */
	public static ImageIcon getImageIcon(String inFilename)
	{
		return new ImageIcon(IconManager.class.getResource("images/" + inFilename));
	}

	public static ImageIcon getImageIcon(String inFilename, int width, int height)
	{
		try {
			ImageIcon icon = getImageIcon(inFilename);					
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics graphics = img.getGraphics();
			graphics.drawImage(icon.getImage(), 0, 0, width, height, null);
			graphics.dispose();
			return new ImageIcon(img);
		} catch ( Exception e ) {
			throw new RuntimeException("Failed to get image for " + inFilename, e);
		}
	}
	
	public static ImageIcon getImageForKey(String key, int width, int height)
	{
		try {
			ImageIcon icon = getImageForKey(key);					
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics graphics = img.getGraphics();
			graphics.drawImage(icon.getImage(), 0, 0, width, height, null);
			graphics.dispose();
			return new ImageIcon(img);
		} catch ( Exception e ) {
			String text = I18nManager.getText(key);
			throw new RuntimeException("Failed to get image for " + key + " (" + text + ")", e);
		}
	}
	
	public static ImageIcon getImageForKey(String key)
	{
		try {
			return new ImageIcon(IconManager.class.getResource(I18nManager.getText(key)));
		} catch ( Exception e ) {
			String text = I18nManager.getText(key);
			throw new RuntimeException("Failed to get image for " + key + " (" + text + ")", e);
		}
	}
}
