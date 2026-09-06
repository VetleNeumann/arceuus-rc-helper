package com.vetle.arceuusrc;

/**
 * Infers the player's Step in the Rotation from an Observation and counts Trips. Pure logic:
 * no injection, no client, only the previous Step as memory: it recognises a Trip when the
 * Step returns to the first mining Step, and tells look-alike inventories apart (the second
 * Batch at the altar, a chisel run whose stack has grown Full before the last Dark Block).
 */
public class Rotation
{
	private RotationStep lastStep = RotationStep.IDLE;
	private int tripsCompleted;

	public RotationStep advance(Observation obs)
	{
		RotationStep step = infer(obs.getInventory(), obs.getPosition(), lastStep);
		if (completesTrip(lastStep, step))
		{
			tripsCompleted++;
		}
		lastStep = step;
		return step;
	}

	public int tripsCompleted()
	{
		return tripsCompleted;
	}

	public void reset()
	{
		lastStep = RotationStep.IDLE;
		tripsCompleted = 0;
	}

	private static RotationStep infer(InventorySnapshot inv, Position position, RotationStep lastStep)
	{
		boolean hasFrags = inv.getFragments() > 0;
		boolean hasDark = inv.getDarkBlocks() > 0;
		boolean hasDense = inv.getDenseBlocks() > 0;
		boolean inventoryFull = inv.getEmptySlots() == 0;
		boolean fullStack = inv.isFullStack();
		boolean chiselling = lastStep == RotationStep.CHISEL_AND_RETURN;

		if (position.isAtAltar())
		{
			if (hasFrags)
			{
				return secondBatch(lastStep) ? RotationStep.CRAFT_REMAINING : RotationStep.CRAFT_FRAGMENTS;
			}
			if (hasDark)
			{
				return RotationStep.CHISEL_AT_ALTAR;
			}
			return RotationStep.RETURN_TO_MINE;
		}

		// A chisel run looks like the second load once the stack is Full (or, after the first
		// chisel, while the inventory is still full); only the previous Step tells them apart.
		if (hasFrags && hasDark && (position.isNearAltar() || (!chiselling && (inventoryFull || fullStack))))
		{
			return RotationStep.GO_ALTAR;
		}
		if (hasDense && inventoryFull)
		{
			return hasFrags ? RotationStep.GO_DARK_SECOND : RotationStep.GO_DARK_FIRST;
		}
		if (hasDark && (chiselling || !fullStack))
		{
			return RotationStep.CHISEL_AND_RETURN;
		}
		if (hasFrags && !hasDark && !hasDense)
		{
			return position.isAtMine() ? RotationStep.MINE_SECOND : RotationStep.RETURN_TO_MINE;
		}
		if (!position.isAtMine() && hasDense && !fullStack)
		{
			return RotationStep.GO_DARK_FIRST;
		}
		return RotationStep.MINE_FIRST;
	}

	/** The second Batch follows the chisel at the altar; it stays the second Batch until the player leaves. */
	private static boolean secondBatch(RotationStep lastStep)
	{
		return lastStep == RotationStep.CHISEL_AT_ALTAR || lastStep == RotationStep.CRAFT_REMAINING;
	}

	private static boolean completesTrip(RotationStep from, RotationStep to)
	{
		return (from == RotationStep.CRAFT_REMAINING || from == RotationStep.RETURN_TO_MINE)
			&& to == RotationStep.MINE_FIRST;
	}
}
