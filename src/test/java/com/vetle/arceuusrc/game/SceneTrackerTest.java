package com.vetle.arceuusrc.game;

import com.vetle.arceuusrc.ArceuusRcArea;
import com.vetle.arceuusrc.RcMode;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** No scene objects are tracked here, so every check falls back to the fixed altar tiles. */
public class SceneTrackerTest
{
	private final SceneTracker tracker = new SceneTracker(null);

	@Test
	public void atAltarWithinTwelveTilesNearAltarWithinTwentyFour()
	{
		WorldPoint altar = ArceuusRcArea.BLOOD_ALTAR;
		assertTrue(tracker.isAtAltar(altar.dx(12), RcMode.BLOOD));
		assertTrue(tracker.isNearAltar(altar.dx(12), RcMode.BLOOD));

		assertFalse(tracker.isAtAltar(altar.dx(13), RcMode.BLOOD));
		assertTrue(tracker.isNearAltar(altar.dx(13), RcMode.BLOOD));

		assertFalse(tracker.isAtAltar(altar.dx(25), RcMode.BLOOD));
		assertFalse(tracker.isNearAltar(altar.dx(25), RcMode.BLOOD));
	}

	@Test
	public void runePicksWhichAltarCounts()
	{
		assertTrue(tracker.isAtAltar(ArceuusRcArea.SOUL_ALTAR, RcMode.SOUL));
		assertFalse(tracker.isAtAltar(ArceuusRcArea.SOUL_ALTAR, RcMode.BLOOD));
	}

	@Test
	public void unknownTileIsNowhere()
	{
		assertFalse(tracker.isAtAltar(null, RcMode.BLOOD));
		assertFalse(tracker.isNearAltar(null, RcMode.BLOOD));
		assertFalse(tracker.isAtMine(null));
	}

	@Test
	public void noRunestoneWhileAnimating()
	{
		assertNull(tracker.chooseRunestone(ArceuusRcArea.MINE_STAND, true));
	}
}
