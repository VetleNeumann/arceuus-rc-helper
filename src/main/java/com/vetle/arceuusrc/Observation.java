package com.vetle.arceuusrc;

import lombok.Value;
import net.runelite.api.WorldView;

/**
 * Everything the plugin reads from the game on one tick. Built once by the observer in the game
 * package; every logic module takes it as a value and never touches the client itself.
 */
@Value
public class Observation
{
	/** Player is inside the Arceuus runecrafting area; nothing else is meaningful when false. */
	boolean inArceuus;
	/** The Rune this Trip targets, already resolved from the configured Mode and Runecraft level. */
	RcMode rune;
	Position position;
	/** Inventory contents; empty when outside Arceuus so the Fragment tracker is not disturbed. */
	InventorySnapshot inventory;
	int agility;
	/** Any animation is playing (mining, chiselling, crafting). */
	boolean animating;
	/** Pose animation is the idle pose, i.e. the player is not walking or running. */
	boolean idlePose;
	int tick;
	/** The Blood Altar's tile lies inside the loaded scene, the first half of Far Bind. */
	boolean bloodAltarInScene;
	/** Scene handle for pathfinding; null when no scene is loaded. */
	WorldView worldView;
}
