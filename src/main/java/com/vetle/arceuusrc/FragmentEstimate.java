package com.vetle.arceuusrc;

import lombok.Value;

/**
 * The Fragment Estimate as decided for drawing on the Fragment stack: how many, whether that is a
 * Full Stack, and whether the number is known or a guess drawn as a question mark.
 */
@Value
public class FragmentEstimate
{
	private static final FragmentEstimate NONE = new FragmentEstimate(0, false, true);

	int count;
	boolean fullStack;
	boolean known;

	/** Nothing drawn: no stack, or hidden by config. */
	public static FragmentEstimate none()
	{
		return NONE;
	}

	public static FragmentEstimate of(InventorySnapshot inv)
	{
		return inv.getFragments() > 0
			? new FragmentEstimate(inv.getFragments(), inv.isFullStack(), inv.isFragmentsKnown())
			: NONE;
	}

	public boolean isShown()
	{
		return count > 0;
	}
}
