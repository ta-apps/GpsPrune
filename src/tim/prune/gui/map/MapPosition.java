package tim.prune.gui.map;

/**
 * Class to hold the current position of the map
 */
public class MapPosition
{
	/** Width and height of each tile of map */
	private static final int MAP_TILE_SIZE = 256;

	/** x position (scale depends on zoom) */
	private long _xPosition = 0L;
	/** y position (scale depends on zoom) */
	private long _yPosition = 0L;

	/** Zoom level, from 2 to max */
	private int _zoom = 12;
	/** Factor to zoom by, 2 to the power of zoom */
	private int _zoomFactor = 1 << _zoom;
	/** Maximum zoom level */
	private static final int MAX_ZOOM = 21;


	public void gotoLatLon(double inLat, double inLon) {
		_xPosition = transformToPixels(MapUtils.getXFromLongitude(inLon));
		_yPosition = transformToPixels(MapUtils.getYFromLatitude(inLat));
	}

	/**
	 * Zoom and pan to show the selected area
	 * @param inMinX minimum transformed X
	 * @param inMaxX maximum transformed X
	 * @param inMinY minimum transformed Y
	 * @param inMaxY maximum transformed Y
	 * @param inWidth width of display
	 * @param inHeight height of display
	 */
	public void zoomToXY(double inMinX, double inMaxX, double inMinY, double inMaxY, int inWidth, int inHeight)
	{
		double diffX = Math.abs(inMaxX - inMinX);
		double diffY = Math.abs(inMaxY - inMinY);
		// Find out what zoom level to go to
		int requiredZoom = -1;
		for (int currZoom = MAX_ZOOM; currZoom >= 2; currZoom--)
		{
			if (transformToPixels(diffX, currZoom) < inWidth
				&& transformToPixels(diffY, currZoom) < inHeight)
			{
				requiredZoom = currZoom;
				break;
			}
		}
		if (requiredZoom < 2) requiredZoom = 2;
		// Set position
		_zoom = requiredZoom;
		_zoomFactor = 1 << _zoom;
		_xPosition = transformToPixels((inMinX + inMaxX) / 2.0);
		_yPosition = transformToPixels((inMinY + inMaxY) / 2.0);
	}

	/**
	 * Zoom and pan to show the selected area
	 * @param inMinX minimum pixels X
	 * @param inMaxX maximum pixels X
	 * @param inMinY minimum pixels Y
	 * @param inMaxY maximum pixels Y
	 * @param inWidth width of display
	 * @param inHeight height of display
	 */
	public void zoomToPixels(int inMinX, int inMaxX, int inMinY, int inMaxY, int inWidth, int inHeight)
	{
		int diffX = Math.abs(inMaxX - inMinX);
		int diffY = Math.abs(inMaxY - inMinY);
		// Find out what zoom level to go to
		int requiredZoom = -1;
		int multFactor = 0;
		for (int currZoom = MAX_ZOOM; currZoom >= _zoom; currZoom--)
		{
			multFactor = 1 << (currZoom - _zoom);
			if ((diffX * multFactor) < inWidth && (diffY * multFactor) < inHeight)
			{
				requiredZoom = currZoom;
				break;
			}
		}
		_zoom = requiredZoom;
		_zoomFactor = 1 << _zoom;
		// Set position
		_xPosition = (_xPosition - inWidth/2 + (inMinX + inMaxX) / 2) * multFactor;
		_yPosition = (_yPosition - inHeight/2 + (inMinY + inMaxY) / 2) * multFactor;
	}

	/**
	 * Transform a given coordinate into pixels using the current zoom value
	 * @param inValue value to transform
	 * @return pixels
	 */
	private long transformToPixels(double inValue)
	{
		return transformToPixels(inValue, _zoom);
	}

	/**
	 * Transform a given coordinate into pixels using the specified zoom value
	 * @param inValue value to transform
	 * @param inZoom zoom value to use
	 * @return pixels
	 */
	private static long transformToPixels(double inValue, int inZoom)
	{
		return (long) (inValue * MAP_TILE_SIZE * (1 << inZoom));
	}

	/**
	 * Convert pixels back into x coordinates
	 * @param inPixelX x coordinate on screen
	 * @param inWidth current width of window
	 * @return x coordinate
	 */
	public double getXFromPixels(int inPixelX, int inWidth)
	{
		return ((inPixelX - inWidth/2) + _xPosition) * 1.0 / MAP_TILE_SIZE / _zoomFactor;
	}

	/**
	 * Gets viewport X values from provided Longitude.
	 * @param inLong
	 * @param inWidth
	 * @return Returns X value for current viewport.
	 */
	public int getXFromLong(double inLong, int inWidth)
	{
		int x = (int)((MapUtils.getXFromLongitude(inLong) * MAP_TILE_SIZE * _zoomFactor) - _xPosition) + (inWidth/2);
		if (x < -100 || x > inWidth+100) {
			if (inLong < 0) inLong = 180 + (180 + inLong);
			else if (inLong > 0) inLong = -180 - (180 - inLong);
			x = (int)((MapUtils.getXFromLongitude(inLong) * MAP_TILE_SIZE * _zoomFactor) - _xPosition) + (inWidth/2);
		}
		return x;
	}

	/**
	 * Convert pixels back into y coordinates
	 * @param inPixelY y coordinate on screen
	 * @param inHeight current height of window
	 * @return y coordinate
	 */
	public double getYFromPixels(int inPixelY, int inHeight)
	{
		return ((inPixelY - inHeight/2) + _yPosition) * 1.0 / MAP_TILE_SIZE / _zoomFactor;
	}

	/**
	 * Gets viewport Y value from Latitude.
	 * @param inLat
	 * @param inHeight
	 * @return Returns Y value for current viewport.
	 */
	public int getYFromLat(double inLat, int inHeight) {
		int y = (int)((MapUtils.getYFromLatitude(inLat) * MAP_TILE_SIZE * _zoomFactor) - _yPosition) + (inHeight/2);
		return y;
	}

	/**
	 * Get the horizontal offset from the centre
	 * @param inValue value to transform
	 * @return number of pixels right (+ve) or left (-ve) from the centre
	 */
	public int getXFromCentre(double inValue)
	{
		return (int) (transformToPixels(inValue) - _xPosition);
	}

	/**
	 * Get the vertical offset from the centre
	 * @param inValue value to transform
	 * @return number of pixels up (+ve) or down (-ve) from the centre
	 */
	public int getYFromCentre(double inValue)
	{
		return (int) (transformToPixels(inValue) - _yPosition);
	}

	/**
	 * Convert a pixel value into a bounds value for sensitivity
	 * @param inPixels number of pixels
	 * @return bounds value to use for x,y checking
	 */
	public double getBoundsFromPixels(int inPixels) {
		return (inPixels * 1.0 / MAP_TILE_SIZE / _zoomFactor);
	}

	/**
	 * Get the leftmost, rightmost, upper and lower index boundaries for the tiles to display
	 * @param inWidth width of window
	 * @param inHeight height of window
	 * @return tile indices as array left, right, up, down
	 */
	public int[] getTileIndices(int inWidth, int inHeight, int[] result)
	{
		result = result == null || result.length != 4 ? new int[4] : result;
		result[0] = getTileIndex(_xPosition - inWidth/2);
		result[1] = getTileIndex(_xPosition + inWidth/2);
		result[2] = getTileIndex(_yPosition - inHeight/2);
		result[3] = getTileIndex(_yPosition + inHeight/2);
		return result;
	}

	/**
	 * Get the pixel offsets for the display
	 * @param inWidth width of window
	 * @param inHeight height of window
	 * @return offsets as x, y
	 */
	public int[] getDisplayOffsets(int inWidth, int inHeight, int[] result)
	{
		result = result == null || result.length != 2 ? new int[2] : result;
		result[0] = getDisplayOffset(_xPosition - inWidth/2);
		result[1] = getDisplayOffset(_yPosition - inHeight/2);
		return result;
	}

	/**
	 * @return x index of the centre tile
	 */
	public int getCentreTileX()
	{
		return getTileIndex(_xPosition);
	}

	/**
	 * @return y index of the centre tile
	 */
	public int getCentreTileY()
	{
		return getTileIndex(_yPosition);
	}

	/**
	 * @param inPosition position of point
	 * @return tile index for that point
	 */
	public int getTileIndex(long inPosition)
	{
		int ret = (int) (inPosition / MAP_TILE_SIZE);
		if (inPosition < 0) {
			ret = (int) (inPosition / MAP_TILE_SIZE) - 1;
		}
		return ret;
	}

	/**
	 * @param inPosition position of point
	 * @return pixel offset for that point
	 */
	public int getDisplayOffset(long inPosition)
	{
		int ret = (int) (inPosition % MAP_TILE_SIZE);
		if (ret < 0) ret = MAP_TILE_SIZE + ret;
		return ret;
	}

	/**
	 * Zoom in one level
	 */
	public void zoomIn()
	{
		if (_zoom < MAX_ZOOM)
		{
			_zoom++;
			_zoomFactor = 1 << _zoom;
			_xPosition *= 2;
			_yPosition *= 2;
		}
	}

	/**
	 * Zoom out one level
	 */
	public void zoomOut()
	{
		if (_zoom >= 2)
		{
			_zoom--;
			_zoomFactor = 1 << _zoom;
			_xPosition /= 2;
			_yPosition /= 2;
		}
	}

	/**
	 * @return current zoom level
	 */
	public int getZoom()
	{
		return _zoom;
	}

	/**
	 * Pan map by the specified amount
	 * @param inDeltaX amount to pan right
	 * @param inDeltaY amount to pan down
	 * @param inHeight current map window height, used to limit Y panning to 
	 *    the map (i.e. no scrolling off the map up or down).
	 */
	public void pan(int inDeltaX, int inDeltaY, int inHeight)
	{
		long max = MAP_TILE_SIZE*_zoomFactor;
		long newX = _xPosition + inDeltaX;
		long newY = _yPosition + inDeltaY;
		if (inDeltaY < 0) {
			newY = newY > (inHeight/2)?newY:inHeight/2;
		} else {
			newY = (newY+inHeight/2) > max?max-inHeight/2:newY;
		}
		newX = newX < 0?max+newX:newX;
		newX = newX > max?max-newX:newX;
		if (inHeight > max) {
			newY = max/2;
		}
		_xPosition = newX;
		_yPosition = newY;
	}
}
