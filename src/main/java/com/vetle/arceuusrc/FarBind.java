package com.vetle.arceuusrc;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Far Bind: clicking the Blood Altar from a distance so the character walks there on its own.
 * Holds only when the altar sits inside the loaded scene and the player stands within the
 * client's reach of it. Pure functions over the Observation; see
 * docs/research/blood-altar-far-click.md for the measurements behind the numbers.
 */
public final class FarBind
{
	/**
	 * Farthest the client offers an object's menu, in tiles from the nearest tile of its
	 * footprint. Measured in game on 2026-09-06 (Bind at 46, none at 47), not derived.
	 */
	static final int REACH = 46;
	/** How far around the player the Far Bind Area is drawn. */
	static final int AREA_RADIUS = 8;

	public enum State
	{
		/** Not a Blood Trip, or a Step where Far Bind does not matter. */
		NONE(""),
		/** Second Venerate: the altar is inside the scene, so Far Bind will work after it. */
		LOADED("loaded"),
		/** The altar fell out of the scene; nothing to do but walk. */
		NOT_LOADED("no"),
		/** The altar can be clicked from here. */
		READY("ready"),
		/** Inside the scene but outside reach; the Far Bind Area shows where to stand. */
		STEP_IN("step in");

		private final String label;

		State(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private FarBind()
	{
	}

	public static State evaluate(RotationStep step, RcMode rune, WorldPoint tile, boolean altarInScene)
	{
		if (rune != RcMode.BLOOD || tile == null)
		{
			return State.NONE;
		}
		switch (step)
		{
			case GO_DARK_SECOND:
				return altarInScene ? State.LOADED : State.NOT_LOADED;
			case GO_ALTAR:
				if (!altarInScene)
				{
					return State.NOT_LOADED;
				}
				return reachTo(tile) <= REACH ? State.READY : State.STEP_IN;
			default:
				return State.NONE;
		}
	}

	/** Chebyshev distance from a tile to the nearest tile of the Blood Altar's footprint. */
	public static int reachTo(WorldPoint tile)
	{
		WorldPoint sw = ArceuusRcArea.BLOOD_ALTAR_FOOTPRINT_SW;
		int max = ArceuusRcArea.BLOOD_ALTAR_FOOTPRINT_SIZE - 1;
		int dx = Math.max(Math.max(sw.getX() - tile.getX(), tile.getX() - (sw.getX() + max)), 0);
		int dy = Math.max(Math.max(sw.getY() - tile.getY(), tile.getY() - (sw.getY() + max)), 0);
		return Math.max(dx, dy);
	}

	/**
	 * The Far Bind Area around a player standing outside reach: every tile within
	 * {@link #AREA_RADIUS} that is inside reach. Empty when the player is already in reach.
	 */
	public static List<WorldPoint> area(WorldPoint player)
	{
		List<WorldPoint> tiles = new ArrayList<>();
		if (player == null || reachTo(player) <= REACH)
		{
			return tiles;
		}
		for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++)
		{
			for (int dy = -AREA_RADIUS; dy <= AREA_RADIUS; dy++)
			{
				WorldPoint tile = player.dx(dx).dy(dy);
				if (reachTo(tile) <= REACH)
				{
					tiles.add(tile);
				}
			}
		}
		return tiles;
	}
}
