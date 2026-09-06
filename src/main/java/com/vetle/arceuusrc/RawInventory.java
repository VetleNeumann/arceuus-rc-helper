package com.vetle.arceuusrc;

import lombok.Value;

/**
 * The inventory and worn gear exactly as the game shows them on one tick, before the Fragment
 * count is resolved. The game hides the Fragment stack size (the container reports 1), so the
 * three fragment fields carry what was visible and {@link FragmentTracker} estimates the count.
 */
@Value
public class RawInventory
{
	int denseBlocks;
	int darkBlocks;
	/** A Fragment stack occupies a slot, whatever the visible quantity. */
	boolean fragmentItemPresent;
	/** Quantity reported by the item container for the Fragment stack. */
	int fragmentContainerQty;
	/** Quantity shown on the inventory widget for the Fragment stack. */
	int fragmentWidgetQty;
	int emptySlots;
	boolean hasChisel;
	boolean hasPickaxe;
	boolean hasInactiveBloodEssence;
	boolean hasActiveBloodEssence;
	boolean lanternEquipped;
	boolean lanternInInventory;
	int lanternItemId;
}
