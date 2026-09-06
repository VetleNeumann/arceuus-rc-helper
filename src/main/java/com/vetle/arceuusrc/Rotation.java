package com.vetle.arceuusrc;

/**
 * Infers the player's Step in the Rotation from an Observation and counts Trips. Pure logic:
 * no injection, no client, only the previous Step as memory so a Trip can be recognised when
 * the Step returns to the first mining Step.
 */
public class Rotation
{
	/** Enough Fragments held that the next load of Dense Blocks goes straight to the altar. */
	static final int FULL_FRAGMENTS = 100;

	private RotationStep lastStep = RotationStep.IDLE;
	private int tripsCompleted;

	public RotationStep advance(Observation obs)
	{
		RotationStep step = infer(obs.getInventory(), obs.getPosition());
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

	private static RotationStep infer(InventorySnapshot inv, Position position)
	{
		boolean hasFrags = inv.getFragments() > 0;
		boolean hasDark = inv.getDarkBlocks() > 0;
		boolean hasDense = inv.getDenseBlocks() > 0;
		boolean inventoryFull = inv.getEmptySlots() == 0;
		boolean fullStack = inv.getFragments() >= FULL_FRAGMENTS;

		if (position.isAtAltar())
		{
			if (hasFrags)
			{
				return RotationStep.CRAFT_FRAGMENTS;
			}
			if (hasDark)
			{
				return RotationStep.CHISEL_AT_ALTAR;
			}
			return RotationStep.RETURN_TO_MINE;
		}

		if (hasFrags && hasDark && (inventoryFull || fullStack || position.isNearAltar()))
		{
			return RotationStep.GO_ALTAR;
		}
		if (hasDense && inventoryFull)
		{
			return hasFrags ? RotationStep.GO_DARK_SECOND : RotationStep.GO_DARK_FIRST;
		}
		if (hasDark && !fullStack)
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

	private static boolean completesTrip(RotationStep from, RotationStep to)
	{
		return (from == RotationStep.CRAFT_REMAINING || from == RotationStep.RETURN_TO_MINE)
			&& to == RotationStep.MINE_FIRST;
	}
}
