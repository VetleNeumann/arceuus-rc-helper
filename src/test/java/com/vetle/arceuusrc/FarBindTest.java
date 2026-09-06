package com.vetle.arceuusrc;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Reach samples are the stationary hover measurements from
 * docs/research/blood-altar-far-click.md: Bind offered at 46 tiles from the footprint, not at 47.
 */
public class FarBindTest
{
	private static final WorldPoint MINE_EDGE_IN = new WorldPoint(1765, 3855, 0);
	private static final WorldPoint MINE_EDGE_OUT = new WorldPoint(1766, 3855, 0);
	private static final WorldPoint SOUTH_OF_DARK_ALTAR = new WorldPoint(1718, 3878, 0);
	private static final WorldPoint SOUTH_EAST_OF_DARK_ALTAR = new WorldPoint(1723, 3878, 0);
	private static final WorldPoint ONE_TILE_SHORT = new WorldPoint(1723, 3879, 0);
	private static final WorldPoint DARK_ALTAR = ArceuusRcArea.DARK_ALTAR;

	@Test
	public void reachIsMeasuredToTheNearestFootprintTile()
	{
		assertEquals(46, FarBind.reachTo(MINE_EDGE_IN));
		assertEquals(46, FarBind.reachTo(SOUTH_OF_DARK_ALTAR));
		assertEquals(46, FarBind.reachTo(SOUTH_EAST_OF_DARK_ALTAR));
		assertEquals(47, FarBind.reachTo(MINE_EDGE_OUT));
		assertEquals(47, FarBind.reachTo(ONE_TILE_SHORT));
		assertEquals(50, FarBind.reachTo(DARK_ALTAR));
		assertEquals(0, FarBind.reachTo(ArceuusRcArea.BLOOD_ALTAR));
	}

	@Test
	public void goingToTheAltarIsReadyInsideReachWhenTheAltarIsLoaded()
	{
		assertEquals(FarBind.State.READY, at(RotationStep.GO_ALTAR, SOUTH_OF_DARK_ALTAR, true));
		assertEquals(FarBind.State.READY, at(RotationStep.GO_ALTAR, MINE_EDGE_IN, true));
	}

	@Test
	public void goingToTheAltarOutsideReachSaysStepIn()
	{
		assertEquals(FarBind.State.STEP_IN, at(RotationStep.GO_ALTAR, ONE_TILE_SHORT, true));
		assertEquals(FarBind.State.STEP_IN, at(RotationStep.GO_ALTAR, DARK_ALTAR, true));
	}

	@Test
	public void unloadedAltarIsNotFarBindableWhateverTheDistance()
	{
		assertEquals(FarBind.State.NOT_LOADED, at(RotationStep.GO_ALTAR, SOUTH_OF_DARK_ALTAR, false));
		assertEquals(FarBind.State.NOT_LOADED, at(RotationStep.GO_DARK_SECOND, DARK_ALTAR, false));
	}

	@Test
	public void secondVenerateReportsOnlyWhetherTheAltarIsLoaded()
	{
		assertEquals(FarBind.State.LOADED, at(RotationStep.GO_DARK_SECOND, DARK_ALTAR, true));
		assertEquals(FarBind.State.LOADED, at(RotationStep.GO_DARK_SECOND, MINE_EDGE_OUT, true));
	}

	@Test
	public void otherStepsAndSoulsDoNotApply()
	{
		assertEquals(FarBind.State.NONE, at(RotationStep.GO_DARK_FIRST, DARK_ALTAR, true));
		assertEquals(FarBind.State.NONE, at(RotationStep.CRAFT_FRAGMENTS, ArceuusRcArea.BLOOD_ALTAR, true));
		assertEquals(FarBind.State.NONE, at(RotationStep.MINE_SECOND, MINE_EDGE_IN, true));
		assertEquals(FarBind.State.NONE, FarBind.evaluate(RotationStep.GO_ALTAR, RcMode.SOUL, SOUTH_OF_DARK_ALTAR, true));
		assertEquals(FarBind.State.NONE, FarBind.evaluate(RotationStep.GO_ALTAR, RcMode.BLOOD, null, true));
	}

	@Test
	public void areaAroundTheDarkAltarIsTheStripSouthOfIt()
	{
		List<WorldPoint> area = FarBind.area(DARK_ALTAR);

		assertTrue(area.contains(SOUTH_OF_DARK_ALTAR));
		assertTrue(area.contains(SOUTH_EAST_OF_DARK_ALTAR));
		assertTrue(area.contains(new WorldPoint(1718, 3874, 0)));
		assertFalse(area.contains(ONE_TILE_SHORT));
		assertFalse(area.contains(DARK_ALTAR));
		assertFalse("outside the drawn radius", area.contains(new WorldPoint(1718, 3873, 0)));
		for (WorldPoint tile : area)
		{
			assertTrue(FarBind.reachTo(tile) <= 46);
			assertTrue(tile.distanceTo(DARK_ALTAR) <= 8);
		}
	}

	@Test
	public void areaIsEmptyWhenTheWholeNeighbourhoodIsInReach()
	{
		assertTrue(FarBind.area(ArceuusRcArea.BLOOD_ALTAR.dy(10)).isEmpty());
	}

	private static FarBind.State at(RotationStep step, WorldPoint tile, boolean altarInScene)
	{
		return FarBind.evaluate(step, RcMode.BLOOD, tile, altarInScene);
	}
}
