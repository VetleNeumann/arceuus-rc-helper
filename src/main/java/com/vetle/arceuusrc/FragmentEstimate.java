package com.vetle.arceuusrc;

import lombok.Value;

/**
 * The Fragment Estimate as decided for drawing on the Fragment stack: how many, and whether that
 * is a Full Stack.
 */
@Value
public class FragmentEstimate
{
	private static final FragmentEstimate NONE = new FragmentEstimate(0, false);

	int count;
	boolean fullStack;

	/** Nothing drawn: no stack, or hidden by config. */
	public static FragmentEstimate none()
	{
		return NONE;
	}

	public static FragmentEstimate of(InventorySnapshot inv)
	{
		return inv.getFragments() > 0
			? new FragmentEstimate(inv.getFragments(), inv.isFullStack())
			: NONE;
	}

	public boolean isShown()
	{
		return count > 0;
	}
}
