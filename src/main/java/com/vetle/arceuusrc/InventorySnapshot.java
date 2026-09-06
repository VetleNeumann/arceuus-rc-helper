package com.vetle.arceuusrc;

import lombok.Value;

@Value
public class InventorySnapshot
{
	private static final InventorySnapshot EMPTY =
		new InventorySnapshot(0, 0, 0, false, 28, false, false, false, false, false, false, -1);

	int denseBlocks;
	int darkBlocks;
	/** The Fragment Estimate. */
	int fragments;
	/** True when the game stated the Fragment count; false when the plugin reasoned it out. */
	boolean fragmentsConfirmed;
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
}
