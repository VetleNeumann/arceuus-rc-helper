package com.vetle.arceuusrc;

import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ArceuusRcAreaTest
{
	@Test
	public void everyLandmarkIsInsideTheArea()
	{
		assertTrue(ArceuusRcArea.isInArceuusRc(ArceuusRcArea.RUNESTONE_SOUTH));
		assertTrue(ArceuusRcArea.isInArceuusRc(ArceuusRcArea.DARK_ALTAR));
		assertTrue(ArceuusRcArea.isInArceuusRc(ArceuusRcArea.BLOOD_ALTAR));
		assertTrue(ArceuusRcArea.isInArceuusRc(ArceuusRcArea.SOUL_ALTAR));
	}

	@Test
	public void unknownOrFarAwayIsOutside()
	{
		assertFalse(ArceuusRcArea.isInArceuusRc(null));
		assertFalse(ArceuusRcArea.isInArceuusRc(new WorldPoint(3222, 3218, 0)));
	}
}
