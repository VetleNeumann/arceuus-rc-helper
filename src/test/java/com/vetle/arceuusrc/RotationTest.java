package com.vetle.arceuusrc;

import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** One test per row of the Rotation table; names read as "situation → Step". */
public class RotationTest
{
	private static final int SLOTS = 28;
	/** Full Stack: Rotation's threshold for skipping the chisel Step. */
	private static final int FULL_STACK = 100;
	private static final WorldPoint TILE = new WorldPoint(1762, 3854, 0);

	private static final Position AT_MINE = new Position(TILE, true, false, false);
	private static final Position ON_THE_WAY = new Position(TILE, false, false, false);
	private static final Position NEAR_ALTAR = new Position(TILE, false, false, true);
	private static final Position AT_ALTAR = new Position(TILE, false, true, true);

	private final Rotation rotation = new Rotation();

	@Test
	public void emptyHandedAtMineMinesFirstLoad()
	{
		assertEquals(RotationStep.MINE_FIRST, step(carrying(0, 0, 0), AT_MINE));
	}

	@Test
	public void fullLoadOfDenseBlocksGoesToDarkAltar()
	{
		assertEquals(RotationStep.GO_DARK_FIRST, step(carrying(SLOTS, 0, 0), AT_MINE));
	}

	@Test
	public void partialDenseLoadAwayFromMineStillGoesToDarkAltar()
	{
		assertEquals(RotationStep.GO_DARK_FIRST, step(carrying(10, 0, 0), ON_THE_WAY));
	}

	@Test
	public void partialDenseLoadAtMineKeepsMining()
	{
		assertEquals(RotationStep.MINE_FIRST, step(carrying(10, 0, 0), AT_MINE));
	}

	@Test
	public void darkBlocksAfterVenerateAreChiselledOnTheWayBack()
	{
		assertEquals(RotationStep.CHISEL_AND_RETURN, step(carrying(0, SLOTS, 0), ON_THE_WAY));
	}

	@Test
	public void fragmentsOnlyAtMineMinesSecondLoad()
	{
		assertEquals(RotationStep.MINE_SECOND, step(carrying(0, 0, 40), AT_MINE));
	}

	@Test
	public void fragmentsOnlyAwayFromMineReturns()
	{
		assertEquals(RotationStep.RETURN_TO_MINE, step(carrying(0, 0, 40), ON_THE_WAY));
	}

	@Test
	public void secondFullDenseLoadGoesToDarkAltarAgain()
	{
		assertEquals(RotationStep.GO_DARK_SECOND, step(carrying(SLOTS - 1, 0, 40), AT_MINE));
	}

	@Test
	public void fragmentsPlusDarkBlocksWithFullInventoryGoToAltar()
	{
		assertEquals(RotationStep.GO_ALTAR, step(carrying(0, SLOTS - 1, 40), ON_THE_WAY));
	}

	@Test
	public void fragmentsPlusDarkBlocksNearAltarGoToAltar()
	{
		assertEquals(RotationStep.GO_ALTAR, step(carrying(0, 5, 40), NEAR_ALTAR));
	}

	@Test
	public void fullStackPlusDarkBlocksGoToAltarWithoutChiselling()
	{
		assertEquals(RotationStep.GO_ALTAR, step(carrying(0, 5, FULL_STACK), ON_THE_WAY));
	}

	@Test
	public void fragmentsPlusFewDarkBlocksFarFromAltarKeepChiselling()
	{
		assertEquals(RotationStep.CHISEL_AND_RETURN, step(carrying(0, 5, 40), ON_THE_WAY));
	}

	@Test
	public void atAltarWithFragmentsCrafts()
	{
		assertEquals(RotationStep.CRAFT_FRAGMENTS, step(carrying(0, 5, 40), AT_ALTAR));
	}

	@Test
	public void atAltarWithOnlyDarkBlocksChisels()
	{
		assertEquals(RotationStep.CHISEL_AT_ALTAR, step(carrying(0, 5, 0), AT_ALTAR));
	}

	@Test
	public void chiselAtAltarThenFragmentsCraftsSecondBatch()
	{
		step(carrying(0, 8, 60), AT_ALTAR);
		step(carrying(0, 8, 0), AT_ALTAR);
		assertEquals(RotationStep.CRAFT_REMAINING, step(carrying(0, 0, 32), AT_ALTAR));
	}

	@Test
	public void secondBatchStaysSecondBatchWhileCrafting()
	{
		step(carrying(0, 8, 60), AT_ALTAR);
		step(carrying(0, 8, 0), AT_ALTAR);
		step(carrying(0, 0, 32), AT_ALTAR);
		assertEquals(RotationStep.CRAFT_REMAINING, step(carrying(0, 0, 32), AT_ALTAR));
	}

	@Test
	public void leavingTheAltarAfterChisellingForgetsTheSecondBatch()
	{
		step(carrying(0, 8, 0), AT_ALTAR);
		step(carrying(0, 0, 32), NEAR_ALTAR);
		assertEquals(RotationStep.CRAFT_FRAGMENTS, step(carrying(0, 0, 32), AT_ALTAR));
	}

	@Test
	public void tripCountsWhenSecondBatchBecomesMiningAgain()
	{
		step(carrying(0, 8, 0), AT_ALTAR);
		step(carrying(0, 0, 32), AT_ALTAR);
		step(carrying(0, 0, 0), AT_MINE);
		assertEquals(1, rotation.tripsCompleted());
	}

	@Test
	public void atAltarEmptyHandedReturnsToMine()
	{
		assertEquals(RotationStep.RETURN_TO_MINE, step(carrying(0, 0, 0), AT_ALTAR));
	}

	@Test
	public void unknownPositionFallsBackToMining()
	{
		assertEquals(RotationStep.MINE_FIRST, step(carrying(0, 0, 0), Position.UNKNOWN));
	}

	@Test
	public void tripCountsWhenReturningToMineBecomesMiningAgain()
	{
		step(carrying(0, 0, 0), AT_ALTAR);
		assertEquals(0, rotation.tripsCompleted());
		step(carrying(0, 0, 0), AT_MINE);
		assertEquals(1, rotation.tripsCompleted());
	}

	@Test
	public void tripDoesNotCountWhileStayingAtMine()
	{
		step(carrying(0, 0, 0), AT_MINE);
		step(carrying(0, 0, 0), AT_MINE);
		step(carrying(3, 0, 0), AT_MINE);
		assertEquals(0, rotation.tripsCompleted());
	}

	@Test
	public void resetForgetsTripsAndLastStep()
	{
		step(carrying(0, 0, 0), AT_ALTAR);
		rotation.reset();
		step(carrying(0, 0, 0), AT_MINE);
		assertEquals(0, rotation.tripsCompleted());
	}

	private RotationStep step(InventorySnapshot inv, Position position)
	{
		return rotation.advance(new Observation(true, RcMode.BLOOD, position, inv, 99, false, true, 0, false, null));
	}

	/** Fragments are one stackable slot; blocks take one slot each. */
	private static InventorySnapshot carrying(int dense, int dark, int fragments)
	{
		int used = dense + dark + (fragments > 0 ? 1 : 0);
		return new InventorySnapshot(dense, dark, fragments, SLOTS - used, true, true, false, false, true, false, -1);
	}
}
