package tim.prune.data;

import java.awt.Color;
import java.util.List;

import tim.prune.UpdateMessageBroker;
import tim.prune.config.Config;
import tim.prune.function.edit.FieldEdit;
import tim.prune.function.edit.FieldEditList;
import tim.prune.gui.map.MapUtils;
import tim.prune.load.xml.GpxMetadata;


/**
 * Class to hold all track information,
 * including track points and waypoints
 */
public class Track
{
	// Data points
	private DataPoint[] _dataPoints = null;
	// Scaled x, y values
	private double[] _xValues = null;
	private double[] _yValues = null;
	private boolean _scaled = false;
	private int _numPoints = 0;
	private boolean _hasTrackpoint = false;
	private boolean _hasWaypoint = false;
	private Color _color;
	// Master field list
	private FieldList _masterFieldList = null;
	// variable ranges
	private AltitudeRange _altitudeRange = null;
	private DoubleRange _latRange = null, _longRange = null;
	private DoubleRange _xRange = null, _yRange = null;
	// GPX Meta data, track it primarly to write back out on save.
	private GpxMetadata _gpxMetadata = new GpxMetadata();

	public Track() {
		this( Color.BLUE );
	}

	/**
	 * Constructor for empty track
	 */
	public Track(Color color)
	{
		// create field list
		_masterFieldList = new FieldList(null);
		// make empty DataPoint array
		_dataPoints = new DataPoint[0];
		_numPoints = 0;
		_color = color;
		// needs to be scaled
		_scaled = false;
	}


	/**
	 * Load method, for initialising and reinitialising data
	 * @param inFieldArray array of Field objects describing fields
	 * @param inPointArray 2d object array containing data
	 * @param inAltFormat altitude format
	 */
	public void load(Field[] inFieldArray, Object[][] inPointArray, Altitude.Format inAltFormat)
	{
		if (inFieldArray == null || inPointArray == null)
		{
			_numPoints = 0;
			return;
		}
		// copy field list
		_masterFieldList = new FieldList(inFieldArray);
		// make DataPoint object from each point in inPointList
		_dataPoints = new DataPoint[inPointArray.length];
		String[] dataArray = null;
		int pointIndex = 0;
		for (int p=0; p < inPointArray.length; p++)
		{
			dataArray = (String[]) inPointArray[p];
			// Convert to DataPoint objects
			DataPoint point = new DataPoint(dataArray, _masterFieldList, inAltFormat, _color);
			if (point.isValid())
			{
				_dataPoints[pointIndex] = point;
				pointIndex++;
			}
		}
		_numPoints = pointIndex;
		// Set first track point to be start of segment
		DataPoint firstTrackPoint = getNextTrackPoint(0);
		if (firstTrackPoint != null) {
			firstTrackPoint.setSegmentStart(true);
		}
		// needs to be scaled
		_scaled = false;
	}


	/**
	 * Load the track by transferring the contents from a loaded Track object
	 * @param inOther Track object containing loaded data
	 */
	public void load(Track inOther)
	{
		_numPoints = inOther._numPoints;
		_masterFieldList = inOther._masterFieldList;
		_dataPoints = inOther._dataPoints;
		_gpxMetadata = inOther._gpxMetadata;
		// needs to be scaled
		_scaled = false;
	}

	/**
	 * Request that a rescale be done to recalculate derived values
	 */
	public void requestRescale()
	{
		_scaled = false;
	}

	/**
	 * Extend the track's field list with the given additional fields
	 * @param inFieldList list of fields to be added
	 */
	public void extendFieldList(FieldList inFieldList)
	{
		_masterFieldList = _masterFieldList.merge(inFieldList);
	}

	////////////////// Modification methods //////////////////////


	/**
	 * Combine this Track with new data
	 * @param inOtherTrack other track to combine
	 */
	public void combine(Track inOtherTrack)
	{
		// merge field list
		_masterFieldList = _masterFieldList.merge(inOtherTrack._masterFieldList);
		// expand data array and add other track's data points
		int totalPoints = getNumPoints() + inOtherTrack.getNumPoints();
		DataPoint[] mergedPoints = new DataPoint[totalPoints];
		System.arraycopy(_dataPoints, 0, mergedPoints, 0, getNumPoints());
		System.arraycopy(inOtherTrack._dataPoints, 0, mergedPoints, getNumPoints(), inOtherTrack.getNumPoints());
		_dataPoints = mergedPoints;
		// combine point count
		_numPoints = totalPoints;
		// needs to be scaled again
		_scaled = false;
		// inform listeners
		UpdateMessageBroker.informSubscribers();
	}


	/**
	 * Crop the track to the given size - subsequent points are not (yet) deleted
	 * @param inNewSize new number of points in track
	 */
	public void cropTo(int inNewSize)
	{
		if (inNewSize >= 0 && inNewSize < getNumPoints())
		{
			_numPoints = inNewSize;
			// needs to be scaled again
			_scaled = false;
			UpdateMessageBroker.informSubscribers();
		}
	}


	/**
	 * Delete the points marked for deletion
	 * @return number of points deleted
	 */
	public int deleteMarkedPoints()
	{
		int numCopied = 0;
		// Copy selected points
		DataPoint[] newPointArray = new DataPoint[_numPoints];
		for (int i=0; i<_numPoints; i++)
		{
			DataPoint point = _dataPoints[i];
			// Don't delete photo points
			if (!point.getDeleteFlag())
			{
				newPointArray[numCopied] = point;
				numCopied++;
			}
		}

		// Copy array references
		int numDeleted = _numPoints - numCopied;
		if (numDeleted > 0)
		{
			_dataPoints = new DataPoint[numCopied];
			System.arraycopy(newPointArray, 0, _dataPoints, 0, numCopied);
			_numPoints = _dataPoints.length;
			_scaled = false;
		}
		return numDeleted;
	}


	/**
	 * Delete the specified point
	 * @param inIndex point index
	 * @return true if successful
	 */
	public boolean deletePoint(int inIndex)
	{
		boolean answer = deleteRange(inIndex, inIndex);
		return answer;
	}


	/**
	 * Delete the specified range of points from the Track
	 * @param inStart start of range (inclusive)
	 * @param inEnd end of range (inclusive)
	 * @return true if successful
	 */
	public boolean deleteRange(int inStart, int inEnd)
	{
		if (inStart < 0 || inEnd < 0 || inEnd < inStart)
		{
			// no valid range selected so can't delete
			return false;
		}
		// check through range to be deleted, and see if any new segment flags present
		boolean hasSegmentStart = false;
		DataPoint nextTrackPoint = getNextTrackPoint(inEnd+1);
		if (nextTrackPoint != null) {
			for (int i=inStart; i<=inEnd && !hasSegmentStart; i++) {
				hasSegmentStart |= _dataPoints[i].getSegmentStart();
			}
			// If segment break found, make sure next trackpoint also has break
			if (hasSegmentStart) {nextTrackPoint.setSegmentStart(true);}
		}
		// valid range, let's delete it
		int numToDelete = inEnd - inStart + 1;
		DataPoint[] newPointArray = new DataPoint[_numPoints - numToDelete];
		// Copy points before the selected range
		if (inStart > 0)
		{
			System.arraycopy(_dataPoints, 0, newPointArray, 0, inStart);
		}
		// Copy points after the deleted one(s)
		if (inEnd < (_numPoints - 1))
		{
			System.arraycopy(_dataPoints, inEnd + 1, newPointArray, inStart,
				_numPoints - inEnd - 1);
		}
		// Copy points over original array
		_dataPoints = newPointArray;
		_numPoints -= numToDelete;
		// needs to be scaled again
		_scaled = false;
		return true;
	}


	/**
	 * Reverse the specified range of points
	 * @param inStart start index
	 * @param inEnd end index
	 * @return true if successful, false otherwise
	 */
	public boolean reverseRange(int inStart, int inEnd)
	{
		if (inStart < 0 || inEnd < 0 || inStart >= inEnd || inEnd >= _numPoints)
		{
			return false;
		}
		// calculate how many point swaps are required
		int numPointsToReverse = (inEnd - inStart + 1) / 2;
		DataPoint p = null;
		for (int i=0; i<numPointsToReverse; i++)
		{
			// swap pairs of points
			p = _dataPoints[inStart + i];
			_dataPoints[inStart + i] = _dataPoints[inEnd - i];
			_dataPoints[inEnd - i] = p;
		}
		// adjust segment starts
		shiftSegmentStarts(inStart, inEnd);
		// Find first track point and following track point, and set segment starts to true
		DataPoint firstTrackPoint = getNextTrackPoint(inStart);
		if (firstTrackPoint != null) {firstTrackPoint.setSegmentStart(true);}
		DataPoint nextTrackPoint = getNextTrackPoint(inEnd+1);
		if (nextTrackPoint != null) {nextTrackPoint.setSegmentStart(true);}
		// needs to be scaled again
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
		return true;
	}


	/**
	 * Add the given time offset to the specified range
	 * @param inStart start of range
	 * @param inEnd end of range
	 * @param inOffset offset to add (-ve to subtract)
	 * @param inUndo true for undo operation
	 * @return true on success
	 */
	public boolean addTimeOffset(int inStart, int inEnd, long inOffset, boolean inUndo)
	{
		// sanity check
		if (inStart < 0 || inEnd < 0 || inStart >= inEnd || inEnd >= _numPoints) {
			return false;
		}
		boolean foundTimestamp = false;
		// Loop over all points within range
		for (int i=inStart; i<=inEnd; i++)
		{
			Timestamp timestamp = _dataPoints[i].getTimestamp();
			if (timestamp != null)
			{
				// This point has a timestamp so add the offset to it
				foundTimestamp = true;
				timestamp.addOffset(inOffset);
				_dataPoints[i].setModified(inUndo);
			}
		}
		return foundTimestamp;
	}

	/**
	 * Add the given altitude offset to the specified range
	 * @param inStart start of range
	 * @param inEnd end of range
	 * @param inOffset offset to add (-ve to subtract)
	 * @param inFormat altitude format of offset
	 * @param inDecimals number of decimal places in offset
	 * @return true on success
	 */
	public boolean addAltitudeOffset(int inStart, int inEnd, double inOffset,
	 Altitude.Format inFormat, int inDecimals)
	{
		// sanity check
		if (inStart < 0 || inEnd < 0 || inStart >= inEnd || inEnd >= _numPoints) {
			return false;
		}
		boolean foundAlt = false;
		// Loop over all points within range
		for (int i=inStart; i<=inEnd; i++)
		{
			Altitude alt = _dataPoints[i].getAltitude();
			if (alt != null && alt.isValid())
			{
				// This point has an altitude so add the offset to it
				foundAlt = true;
				alt.addOffset(inOffset, inFormat, inDecimals);
				_dataPoints[i].setModified(false);
			}
		}
		// needs to be scaled again
		_scaled = false;
		return foundAlt;
	}


	/**
	 * Collect all waypoints to the start or end of the track
	 * @param inAtStart true to collect at start, false for end
	 * @return true if successful, false if no change
	 */
	public boolean collectWaypoints(boolean inAtStart)
	{
		// Check for mixed data, numbers of waypoints & nons
		int numWaypoints = 0, numNonWaypoints = 0;
		boolean wayAfterNon = false, nonAfterWay = false;
		DataPoint[] waypoints = new DataPoint[_numPoints];
		DataPoint[] nonWaypoints = new DataPoint[_numPoints];
		DataPoint point = null;
		for (int i=0; i<_numPoints; i++)
		{
			point = _dataPoints[i];
			if (point.isWaypoint())
			{
				waypoints[numWaypoints] = point;
				numWaypoints++;
				wayAfterNon |= (numNonWaypoints > 0);
			}
			else
			{
				nonWaypoints[numNonWaypoints] = point;
				numNonWaypoints++;
				nonAfterWay |= (numWaypoints > 0);
			}
		}
		// Exit if the data is already in the specified order
		if (numWaypoints == 0 || numNonWaypoints == 0
			|| (inAtStart && !wayAfterNon && nonAfterWay)
			|| (!inAtStart && wayAfterNon && !nonAfterWay))
		{
			return false;
		}

		// Copy the arrays back into _dataPoints in the specified order
		if (inAtStart)
		{
			System.arraycopy(waypoints, 0, _dataPoints, 0, numWaypoints);
			System.arraycopy(nonWaypoints, 0, _dataPoints, numWaypoints, numNonWaypoints);
		}
		else
		{
			System.arraycopy(nonWaypoints, 0, _dataPoints, 0, numNonWaypoints);
			System.arraycopy(waypoints, 0, _dataPoints, numNonWaypoints, numWaypoints);
		}
		// needs to be scaled again
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
		return true;
	}


	/**
	 * Interleave all waypoints by each nearest track point
	 * @return true if successful, false if no change
	 */
	public boolean interleaveWaypoints()
	{
		// Separate waypoints and find nearest track point
		int numWaypoints = 0;
		DataPoint[] waypoints = new DataPoint[_numPoints];
		int[] pointIndices = new int[_numPoints];
		DataPoint point = null;
		int i = 0;
		for (i=0; i<_numPoints; i++)
		{
			point = _dataPoints[i];
			if (point.isWaypoint())
			{
				waypoints[numWaypoints] = point;
				pointIndices[numWaypoints] = getNearestPointIndex(
					_xValues[i], _yValues[i], -1.0, true);
				numWaypoints++;
			}
		}
		// Exit if data not mixed
		if (numWaypoints == 0 || numWaypoints == _numPoints)
			return false;

		// Loop round points copying to correct order
		DataPoint[] dataCopy = new DataPoint[_numPoints];
		int copyIndex = 0;
		for (i=0; i<_numPoints; i++)
		{
			point = _dataPoints[i];
			// if it's a track point, copy it
			if (!point.isWaypoint())
			{
				dataCopy[copyIndex] = point;
				copyIndex++;
			}
			// check for waypoints with this index
			for (int j=0; j<numWaypoints; j++)
			{
				if (pointIndices[j] == i)
				{
					dataCopy[copyIndex] = waypoints[j];
					copyIndex++;
				}
			}
		}
		// Copy data back to track
		_dataPoints = dataCopy;
		// needs to be scaled again to recalc x, y
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
		return true;
	}


	/**
	 * Cut and move the specified section
	 * @param inSectionStart start index of section
	 * @param inSectionEnd end index of section
	 * @param inMoveTo index of move to point
	 * @return true if move successful
	 */
	public boolean cutAndMoveSection(int inSectionStart, int inSectionEnd, int inMoveTo)
	{
		// TODO: Move cut/move into separate function?
		// Check that indices make sense
		if (inSectionStart > 0 && inSectionEnd > inSectionStart && inMoveTo >= 0
			&& (inMoveTo < inSectionStart || inMoveTo > (inSectionEnd+1)))
		{
			// do the cut and move
			DataPoint[] newPointArray = new DataPoint[_numPoints];
			// System.out.println("Cut/move section (" + inSectionStart + " - " + inSectionEnd + ") to before point " + inMoveTo);
			// Is it a forward copy or a backward copy?
			if (inSectionStart > inMoveTo)
			{
				int sectionLength = inSectionEnd - inSectionStart + 1;
				// move section to earlier point
				if (inMoveTo > 0) {
					System.arraycopy(_dataPoints, 0, newPointArray, 0, inMoveTo); // unchanged points before
				}
				System.arraycopy(_dataPoints, inSectionStart, newPointArray, inMoveTo, sectionLength); // moved bit
				// after insertion point, before moved bit
				System.arraycopy(_dataPoints, inMoveTo, newPointArray, inMoveTo + sectionLength, inSectionStart - inMoveTo);
				// after moved bit
				if (inSectionEnd < (_numPoints - 1)) {
					System.arraycopy(_dataPoints, inSectionEnd+1, newPointArray, inSectionEnd+1, _numPoints - inSectionEnd - 1);
				}
			}
			else
			{
				// Move section to later point
				if (inSectionStart > 0) {
					System.arraycopy(_dataPoints, 0, newPointArray, 0, inSectionStart); // unchanged points before
				}
				// from end of section to move to point
				if (inMoveTo > (inSectionEnd + 1)) {
					System.arraycopy(_dataPoints, inSectionEnd+1, newPointArray, inSectionStart, inMoveTo - inSectionEnd - 1);
				}
				// moved bit
				System.arraycopy(_dataPoints, inSectionStart, newPointArray, inSectionStart + inMoveTo - inSectionEnd - 1,
					inSectionEnd - inSectionStart + 1);
				// unchanged bit after
				if (inSectionEnd < (_numPoints - 1)) {
					System.arraycopy(_dataPoints, inMoveTo, newPointArray, inMoveTo, _numPoints - inMoveTo);
				}
			}
			// Copy array references
			_dataPoints = newPointArray;
			_scaled = false;
			return true;
		}
		return false;
	}


	/**
	 * Interpolate extra points between two selected ones
	 * @param inStartIndex start index of interpolation
	 * @param inNumPoints num points to insert
	 * @return true if successful
	 */
	public boolean interpolate(int inStartIndex, int inNumPoints)
	{
		// check parameters
		if (inStartIndex < 0 || inStartIndex >= _numPoints || inNumPoints <= 0)
			return false;

		// get start and end points
		DataPoint startPoint = getPoint(inStartIndex);
		DataPoint endPoint = getPoint(inStartIndex + 1);

		// Make array of points to insert
		DataPoint[] insertedPoints = startPoint.interpolate(endPoint, inNumPoints);

		// Insert points into track
		return insertRange(insertedPoints, inStartIndex + 1);
	}


	/**
	 * Average selected points
	 * @param inStartIndex start index of selection
	 * @param inEndIndex end index of selection
	 * @return true if successful
	 */
	public boolean average(int inStartIndex, int inEndIndex)
	{
		// check parameters
		if (inStartIndex < 0 || inStartIndex >= _numPoints || inEndIndex <= inStartIndex)
			return false;

		DataPoint startPoint = getPoint(inStartIndex);
		double firstLatitude = startPoint.getLatitude().getDouble();
		double firstLongitude = startPoint.getLongitude().getDouble();
		double latitudeDiff = 0.0, longitudeDiff = 0.0;
		double totalAltitude = 0;
		int numAltitudes = 0;
		Altitude.Format altFormat = Config.getConfigBoolean(Config.KEY_METRIC_UNITS)?Altitude.Format.METRES:Altitude.Format.FEET;
		// loop between start and end points
		for (int i=inStartIndex; i<= inEndIndex; i++)
		{
			DataPoint currPoint = getPoint(i);
			latitudeDiff += (currPoint.getLatitude().getDouble() - firstLatitude);
			longitudeDiff += (currPoint.getLongitude().getDouble() - firstLongitude);
			if (currPoint.hasAltitude()) {
				totalAltitude += currPoint.getAltitude().getValue(altFormat);
				numAltitudes++;
			}
		}
		int numPoints = inEndIndex - inStartIndex + 1;
		double meanLatitude = firstLatitude + (latitudeDiff / numPoints);
		double meanLongitude = firstLongitude + (longitudeDiff / numPoints);
		Altitude meanAltitude = null;
		if (numAltitudes > 0) {meanAltitude = new Altitude((int) (totalAltitude / numAltitudes), altFormat);}

		DataPoint insertedPoint = new DataPoint(new Latitude(meanLatitude, Coordinate.FORMAT_NONE),
			new Longitude(meanLongitude, Coordinate.FORMAT_NONE), meanAltitude);
		// Make into singleton
		insertedPoint.setSegmentStart(true);
		DataPoint nextPoint = getNextTrackPoint(inEndIndex+1);
		if (nextPoint != null) {nextPoint.setSegmentStart(true);}
		// Insert points into track
		return insertRange(new DataPoint[] {insertedPoint}, inEndIndex + 1);
	}


	/**
	 * Append the specified points to the end of the track
	 * @param inPoints DataPoint objects to add
	 */
	public void appendPoints(DataPoint[] inPoints)
	{
		// Insert points into track
		if (inPoints != null && inPoints.length > 0)
		{
			insertRange(inPoints, _numPoints);
		}
		// needs to be scaled again to recalc x, y
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
	}


	//////// information methods /////////////


	/**
	 * Get the point at the given index
	 * @param inPointNum index number, starting at 0
	 * @return DataPoint object, or null if out of range
	 */
	public DataPoint getPoint(int inPointNum)
	{
		if (inPointNum > -1 && inPointNum < getNumPoints())
		{
			return _dataPoints[inPointNum];
		}
		return null;
	}
	
	public Color getColor( int inPointNum ) {
		DataPoint point = getPoint(inPointNum);
		return point == null ? null : point.getColor();
	}


	/**
	 * @return altitude range of points as AltitudeRange object
	 */
	public AltitudeRange getAltitudeRange()
	{
		if (!_scaled) scalePoints();
		return _altitudeRange;
	}

	/**
	 * @return the number of (valid) points in the track
	 */
	public int getNumPoints()
	{
		return _numPoints;
	}

	/**
	 * @return The range of x values as a DoubleRange object
	 */
	public DoubleRange getXRange()
	{
		if (!_scaled) scalePoints();
		return _xRange;
	}

	/**
	 * @return The range of y values as a DoubleRange object
	 */
	public DoubleRange getYRange()
	{
		if (!_scaled) scalePoints();
		return _yRange;
	}

	/**
	 * @return The range of lat values as a DoubleRange object
	 */
	public DoubleRange getLatRange()
	{
		if (!_scaled) scalePoints();
		return _latRange;
	}
	/**
	 * @return The range of lon values as a DoubleRange object
	 */
	public DoubleRange getLonRange()
	{
		if (!_scaled) scalePoints();
		return _longRange;
	}

	/**
	 * @param inPointNum point index, starting at 0
	 * @return scaled x value of specified point
	 */
	public double getX(int inPointNum)
	{
		if (!_scaled) scalePoints();
		return _xValues[inPointNum];
	}

	/**
	 * @param inPointNum point index, starting at 0
	 * @return scaled y value of specified point
	 */
	public double getY(int inPointNum)
	{
		if (!_scaled) scalePoints();
		return _yValues[inPointNum];
	}

	/**
	 * @return the master field list
	 */
	public FieldList getFieldList()
	{
		return _masterFieldList;
	}


	/**
	 * Checks if any data exists for the specified field
	 * @param inField Field to examine
	 * @return true if data exists for this field
	 */
	public boolean hasData(Field inField)
	{
		// Don't use this method for altitudes
		if (inField.equals(Field.ALTITUDE)) {return hasAltitudeData();}
		return hasData(inField, 0, _numPoints-1);
	}


	/**
	 * Checks if any data exists for the specified field in the specified range
	 * @param inField Field to examine
	 * @param inStart start of range to check
	 * @param inEnd end of range to check (inclusive)
	 * @return true if data exists for this field
	 */
	public boolean hasData(Field inField, int inStart, int inEnd)
	{
		// Loop over selected point range
		for (int i=inStart; i<=inEnd; i++)
		{
			if (_dataPoints[i].getFieldValue(inField) != null)
			{
				// Check altitudes and timestamps
				if ((inField != Field.ALTITUDE || _dataPoints[i].getAltitude().isValid())
					&& (inField != Field.TIMESTAMP || _dataPoints[i].getTimestamp().isValid()))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return true if track has altitude data
	 */
	public boolean hasAltitudeData()
	{
		for (int i=0; i<_numPoints; i++) {
			if (_dataPoints[i].hasAltitude()) {return true;}
		}
		return false;
	}

	/**
	 * @return true if track contains at least one trackpoint
	 */
	public boolean hasTrackPoints()
	{
		if (!_scaled) scalePoints();
		return _hasTrackpoint;
	}

	/**
	 * @return true if track contains waypoints
	 */
	public boolean hasWaypoints()
	{
		if (!_scaled) scalePoints();
		return _hasWaypoint;
	}

	/**
	 * @return true if track contains any points marked for deletion
	 */
	public boolean hasMarkedPoints()
	{
		if (_numPoints < 1) {
			return false;
		}
		// Loop over points looking for any marked for deletion
		for (int i=0; i<=_numPoints-1; i++)
		{
			if (_dataPoints[i] != null && _dataPoints[i].getDeleteFlag()) {
				return true;
			}
		}
		// None found
		return false;
	}

	/**
	 * Clear all the deletion markers
	 */
	public void clearDeletionMarkers()
	{
		for (int i=0; i<_numPoints; i++)
		{
			_dataPoints[i].setMarkedForDeletion(false);
		}
	}

	/**
	 * Collect all the waypoints into the given List
	 * @param inList List to fill with waypoints
	 */
	public void getWaypoints(List<DataPoint> inList)
	{
		// clear list
		inList.clear();
		// loop over points and copy all waypoints into list
		for (int i=0; i<=_numPoints-1; i++)
		{
			if (_dataPoints[i] != null && _dataPoints[i].isWaypoint())
			{
				inList.add(_dataPoints[i]);
			}
		}
	}

	/**
	 * Appends all the waypoints to the given List
	 * @param inList List to fill with waypoints
	 */
	public void appendWaypoints(List<DataPoint> inList)
	{
		// loop over points and copy all waypoints into list
		for (int i=0; i<=_numPoints-1; i++)
		{
			if (_dataPoints[i] != null && _dataPoints[i].isWaypoint())
			{
				inList.add(_dataPoints[i]);
			}
		}
	}

	/**
	 * Search for the given Point in the track and return the index
	 * @param inPoint Point to look for
	 * @return index of Point, if any or -1 if not found
	 */
	public int getPointIndex(DataPoint inPoint)
	{
		if (inPoint != null)
		{
			// Loop over points in track
			for (int i=0; i<=_numPoints-1; i++)
			{
				if (_dataPoints[i] == inPoint)
				{
					return i;
				}
			}
		}
		// not found
		return -1;
	}


	///////// Internal processing methods ////////////////


	/**
	 * Scale all the points in the track to gain x and y values
	 * ready for plotting
	 */
	private void scalePoints()
	{
		// Loop through all points in track, to see limits of lat, long and altitude
		_longRange = new DoubleRange();
		_latRange = new DoubleRange();
		_altitudeRange = new AltitudeRange();
		int p;
		_hasWaypoint = false; _hasTrackpoint = false;
		for (p=0; p < getNumPoints(); p++)
		{
			DataPoint point = getPoint(p);
			if (point != null && point.isValid())
			{
				_longRange.addValue(point.getLongitude().getDouble());
				_latRange.addValue(point.getLatitude().getDouble());
				if (point.getAltitude().isValid())
				{
					_altitudeRange.addValue(point.getAltitude());
				}
				if (point.isWaypoint())
					_hasWaypoint = true;
				else
					_hasTrackpoint = true;
			}
		}

		// Loop over points and calculate scales
		_xValues = new double[getNumPoints()];
		_yValues = new double[getNumPoints()];
		_xRange = new DoubleRange();
		_yRange = new DoubleRange();
		for (p=0; p < getNumPoints(); p++)
		{
			DataPoint point = getPoint(p);
			if (point != null)
			{
				_xValues[p] = MapUtils.getXFromLongitude(point.getLongitude().getDouble());
				_xRange.addValue(_xValues[p]);
				_yValues[p] = MapUtils.getYFromLatitude(point.getLatitude().getDouble());
				_yRange.addValue(_yValues[p]);
			}
		}
		_scaled = true;
	}


	/**
	 * Find the nearest point to the specified x and y coordinates
	 * or -1 if no point is within the specified max distance
	 * @param inX x coordinate
	 * @param inY y coordinate
	 * @param inMaxDist maximum distance from selected coordinates
	 * @param inJustTrackPoints true if waypoints should be ignored
	 * @return index of nearest point or -1 if not found
	 */
	public int[] getRangeIndexWithin(double east, double north, double west, double south, int[] ret)
	{
		ret = ret == null ? new int[2] : ret;
		ret[0] = -1; // oldest
		ret[1] = -1; // newest

		for (int i=0; i < getNumPoints(); i++)
		{
			if( _dataPoints[i].getLatitude().getDouble() <= north 
					&& _dataPoints[i].getLatitude().getDouble() >= south
					&& _dataPoints[i].getLongitude().getDouble() <= east 
					&& _dataPoints[i].getLongitude().getDouble() >= west ) {					
				if( ret[0] == -1 ) {
					ret[0] = i;
				}
				ret[1] = i;
			}
		}

		return ret;
	}

	/**
	 * Find the nearest point to the specified x and y coordinates
	 * or -1 if no point is within the specified max distance
	 * @param inX x coordinate
	 * @param inY y coordinate
	 * @param inMaxDist maximum distance from selected coordinates
	 * @param inJustTrackPoints true if waypoints should be ignored
	 * @return index of nearest point or -1 if not found
	 */
	public int getNearestPointIndex(double inX, double inY, double inMaxDist, boolean inJustTrackPoints)
	{
		int nearestPoint = 0;
		double nearestDist = -1.0;
		double currDist;
		for (int i=0; i < getNumPoints(); i++)
		{
			if (!inJustTrackPoints || !_dataPoints[i].isWaypoint())
			{
				currDist = Math.abs(_xValues[i] - inX) + Math.abs(_yValues[i] - inY);
				if (currDist < nearestDist || nearestDist < 0.0)
				{
					nearestPoint = i;
					nearestDist = currDist;
				}
			}
		}
		// Check whether it's within required distance
		if (nearestDist > inMaxDist && inMaxDist > 0.0)
		{
			return -1;
		}
		return nearestPoint;
	}

	/**
	 * Get the next track point starting from the given index
	 * @param inStartIndex index to start looking from
	 * @return next track point, or null if end of data reached
	 */
	public DataPoint getNextTrackPoint(int inStartIndex)
	{
		return getNextTrackPoint(inStartIndex, _numPoints, true);
	}

	/**
	 * Get the next track point in the given range
	 * @param inStartIndex index to start looking from
	 * @param inEndIndex index to stop looking
	 * @return next track point, or null if end of data reached
	 */
	public DataPoint getNextTrackPoint(int inStartIndex, int inEndIndex)
	{
		return getNextTrackPoint(inStartIndex, inEndIndex, true);
	}

	/**
	 * Get the previous track point starting from the given index
	 * @param inStartIndex index to start looking from
	 * @return next track point, or null if end of data reached
	 */
	public DataPoint getPreviousTrackPoint(int inStartIndex)
	{
		return getNextTrackPoint(inStartIndex, _numPoints, false);
	}

	/**
	 * Get the next track point starting from the given index
	 * @param inStartIndex index to start looking from
	 * @param inEndIndex index to stop looking (inclusive)
	 * @param inCountUp true for next, false for previous
	 * @return next track point, or null if end of data reached
	 */
	private DataPoint getNextTrackPoint(int inStartIndex, int inEndIndex, boolean inCountUp)
	{
		// Loop forever over points
		int increment = inCountUp?1:-1;
		for (int i=inStartIndex; i<=inEndIndex; i+=increment)
		{
			DataPoint point = getPoint(i);
			// Exit if end of data reached - there wasn't a track point
			if (point == null) {return null;}
			if (point.isValid() && !point.isWaypoint()) {
				// next track point found
				return point;
			}
		}
		return null;
	}

	/**
	 * Shift all the segment start flags in the given range by 1
	 * Method used by reverse range and its undo
	 * @param inStartIndex start of range, inclusive
	 * @param inEndIndex end of range, inclusive
	 */
	public void shiftSegmentStarts(int inStartIndex, int inEndIndex)
	{
		boolean prevFlag = true;
		boolean currFlag = true;
		for (int i=inStartIndex; i<= inEndIndex; i++)
		{
			DataPoint point = getPoint(i);
			if (point != null && !point.isWaypoint())
			{
				// remember flag
				currFlag = point.getSegmentStart();
				// shift flag by 1
				point.setSegmentStart(prevFlag);
				prevFlag = currFlag;
			}
		}
	}

	////////////////// Cloning and replacing ///////////////////

	/**
	 * Clone the array of DataPoints
	 * @return shallow copy of DataPoint objects
	 */
	public DataPoint[] cloneContents()
	{
		DataPoint[] clone = new DataPoint[getNumPoints()];
		System.arraycopy(_dataPoints, 0, clone, 0, getNumPoints());
		return clone;
	}


	/**
	 * Clone the specified range of data points
	 * @param inStart start index (inclusive)
	 * @param inEnd end index (inclusive)
	 * @return shallow copy of DataPoint objects
	 */
	public DataPoint[] cloneRange(int inStart, int inEnd)
	{
		int numSelected = 0;
		if (inEnd >= 0 && inEnd >= inStart)
		{
			numSelected = inEnd - inStart + 1;
		}
		DataPoint[] result = new DataPoint[numSelected>0?numSelected:0];
		if (numSelected > 0)
		{
			System.arraycopy(_dataPoints, inStart, result, 0, numSelected);
		}
		return result;
	}


	/**
	 * Re-insert the specified point at the given index
	 * @param inPoint point to insert
	 * @param inIndex index at which to insert the point
	 * @return true if it worked, false otherwise
	 */
	public boolean insertPoint(DataPoint inPoint, int inIndex)
	{
		if (inIndex > _numPoints || inPoint == null)
		{
			return false;
		}
		// Make new array to copy points over to
		DataPoint[] newPointArray = new DataPoint[_numPoints + 1];
		if (inIndex > 0)
		{
			System.arraycopy(_dataPoints, 0, newPointArray, 0, inIndex);
		}
		newPointArray[inIndex] = inPoint;
		if (inIndex < _numPoints)
		{
			System.arraycopy(_dataPoints, inIndex, newPointArray, inIndex+1, _numPoints - inIndex);
		}
		// Change over to new array
		_dataPoints = newPointArray;
		_numPoints++;
		// needs to be scaled again
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
		return true;
	}


	/**
	 * Re-insert the specified point range at the given index
	 * @param inPoints point array to insert
	 * @param inIndex index at which to insert the points
	 * @return true if it worked, false otherwise
	 */
	public boolean insertRange(DataPoint[] inPoints, int inIndex)
	{
		if (inIndex > _numPoints || inPoints == null)
		{
			return false;
		}
		// Make new array to copy points over to
		DataPoint[] newPointArray = new DataPoint[_numPoints + inPoints.length];
		if (inIndex > 0)
		{
			System.arraycopy(_dataPoints, 0, newPointArray, 0, inIndex);
		}
		System.arraycopy(inPoints, 0, newPointArray, inIndex, inPoints.length);
		if (inIndex < _numPoints)
		{
			System.arraycopy(_dataPoints, inIndex, newPointArray, inIndex+inPoints.length, _numPoints - inIndex);
		}
		// Change over to new array
		_dataPoints = newPointArray;
		_numPoints += inPoints.length;
		// needs to be scaled again
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
		return true;
	}


	/**
	 * Replace the track contents with the given point array
	 * @param inContents array of DataPoint objects
	 * @return true on success
	 */
	public boolean replaceContents(DataPoint[] inContents)
	{
		// master field array stays the same
		// (would need to store field array too if we wanted to redo a load)
		// replace data array
		_dataPoints = inContents;
		_numPoints = _dataPoints.length;
		_scaled = false;
		UpdateMessageBroker.informSubscribers();
		return true;
	}


	/**
	 * Edit the specified point
	 * @param inPoint point to edit
	 * @param inEditList list of edits to make
	 * @param inUndo true if undo operation, false otherwise
	 * @return true if successful
	 */
	public boolean editPoint(DataPoint inPoint, FieldEditList inEditList, boolean inUndo)
	{
		if (inPoint != null && inEditList != null && inEditList.getNumEdits() > 0)
		{
			// go through edits one by one
			int numEdits = inEditList.getNumEdits();
			for (int i=0; i<numEdits; i++)
			{
				FieldEdit edit = inEditList.getEdit(i);
				Field editField = edit.getField();
				inPoint.setFieldValue(editField, edit.getValue(), inUndo);
				// Check that master field list has this field already (maybe point name has been added)
				if (!_masterFieldList.contains(editField)) {
					_masterFieldList.extendList(editField);
				}
			}
			// point possibly needs to be scaled again
			_scaled = false;
			// trigger listeners
			UpdateMessageBroker.informSubscribers();
			return true;
		}
		return false;
	}

	public void setGpxMetadata(GpxMetadata gpxMetadata) {
		this._gpxMetadata = gpxMetadata == null?new GpxMetadata():gpxMetadata;
	}

	public GpxMetadata getGpxMetadata() {
		return _gpxMetadata;
	}
}