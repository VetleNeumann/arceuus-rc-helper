package com.vetle.arceuusrc;

import lombok.Value;

@Value
public class InventorySnapshot
{
	private static final int FULL_STACK = 100;
	private static final InventorySnapshot EMPTY =
		new InventorySnapshot(0, 0, 0, true, 28, false, false, false, false, false, false, -1);

	int denseBlocks;
	int darkBlocks;
	/** The Fragment Estimate. */
	int fragments;
	/** False while the Fragment Estimate is a guess: the plugin did not watch the stack being made. */
	boolean fragmentsKnown;
	int emptySlots;
	boolean hasChisel;
	boolean hasPickaxe;
	boolean hasInactiveBloodEssence;
	boolean hasActiveBloodEssence;
	boolean lanternEquipped;
	boolean lanternInInventory;
	int lanternItemId;

	/** Nothing carried, nothing worn: the inventory outside Arceuus, before any scan. */
	public static InventorySnapshot empty()
	{
		return EMPTY;
	}

	/** Full Stack: enough Fragments held that the next load of Dense Blocks goes straight to the altar. */
	public boolean isFullStack()
	{
		return fragments >= FULL_STACK;
	}
}
