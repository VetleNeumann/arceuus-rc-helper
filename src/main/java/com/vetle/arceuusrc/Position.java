package com.vetle.arceuusrc;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Where the player stands, reduced to what the Rotation needs: the tile, and whether that tile
 * counts as at the Mine, At Altar or Near Altar for the current Rune.
 */
@Value
public class Position
{
	public static final Position UNKNOWN = new Position(null, false, false, false);

	WorldPoint tile;
	boolean atMine;
	boolean atAltar;
	boolean nearAltar;
}
