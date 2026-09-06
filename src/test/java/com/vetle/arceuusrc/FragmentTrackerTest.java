package com.vetle.arceuusrc;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Characterisation of the Fragment estimate: the game hides the stack size, so the tracker
 * reads a sequence of Raw Inventories and chat messages. Names read as "sequence → estimate".
 */
public class FragmentTrackerTest
{
	private static final int SLOTS = 28;

	private final FragmentTracker tracker = new FragmentTracker();

	@Test
	public void noFragmentStackMeansZero()
	{
		assertEquals(0, fragments(carrying(0, 0, false, 0, 0)));
	}

	@Test
	public void visibleStackQuantityIsTrusted()
	{
		assertEquals(37, fragments(carrying(0, 0, true, 37, 0)));
		assertEquals(52, fragments(carrying(0, 0, true, 1, 52)));
	}

	@Test
	public void visibleQuantityCapsAtTheLargestStack()
	{
		assertEquals(111, fragments(carrying(0, 0, true, 200, 0)));
	}

	@Test
	public void hiddenStackWithRoomLeftStartsAtOne()
	{
		assertEquals(1, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void chisellingDarkBlocksAddsFourFragmentsEach()
	{
		fragments(carrying(0, 8, true, 1, 1));
		assertEquals(13, fragments(carrying(0, 5, true, 1, 1)));
		assertEquals(33, fragments(carrying(0, 0, true, 1, 1)));
	}

	@Test
	public void chisellingNeverExceedsTheLargestStack()
	{
		fragments(carrying(0, 27, true, 1, 1));
		assertEquals(111, fragments(carrying(0, 0, true, 1, 1)));
	}

	@Test
	public void fullSecondInventoryWithHiddenStackIsATypicalFullStack()
	{
		assertEquals(108, fragments(carrying(0, 27, true, 1, 1)));
	}

	@Test
	public void chatLineWithManyPiecesSetsTheEstimate()
	{
		tracker.onChatMessage(chat("This stack of fragments is roughly equivalent to 27 pieces of essence."));
		assertEquals(27, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void chatLineWithOnePieceSetsOne()
	{
		tracker.onChatMessage(chat("This stack of fragments is roughly equivalent to one piece of essence."));
		assertEquals(1, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void chatLineCapsAtTheLargestStack()
	{
		tracker.onChatMessage(chat("<col=ff0000>This stack of fragments is roughly equivalent to 500 pieces of essence.</col>"));
		assertEquals(111, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void unrelatedChatKeepsTheEstimate()
	{
		tracker.onChatMessage(chat("This stack of fragments is roughly equivalent to 27 pieces of essence."));
		tracker.onChatMessage(chat("Welcome to Old School RuneScape."));
		assertEquals(27, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void droppingTheStackForgetsTheEstimate()
	{
		tracker.onChatMessage(chat("This stack of fragments is roughly equivalent to 27 pieces of essence."));
		assertEquals(0, fragments(carrying(0, 5, false, 0, 0)));
		assertEquals(1, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void resetForgetsEstimateAndDarkBlockCount()
	{
		fragments(carrying(0, 8, true, 1, 1));
		tracker.reset();
		assertEquals(1, fragments(carrying(0, 5, true, 1, 1)));
	}

	@Test
	public void gearAndBlocksPassThroughUnchanged()
	{
		RawInventory raw = new RawInventory(3, 4, true, 1, 1, 20, false, true, true, false, true, false, 42);
		InventorySnapshot snap = tracker.observe(raw);
		assertEquals(new InventorySnapshot(3, 4, 1, false, 20, false, true, true, false, true, false, 42), snap);
	}

	@Test
	public void stackWatchedFromEmptyIsKnown()
	{
		assertTrue(known(carrying(0, 27, false, 0, 0)));
		assertEquals(4, fragments(carrying(0, 26, true, 1, 1)));
		assertTrue(known(carrying(0, 26, true, 1, 1)));
		assertEquals(8, fragments(carrying(0, 25, true, 1, 1)));
		assertTrue(known(carrying(0, 25, true, 1, 1)));
	}

	@Test
	public void stackFirstSeenAfterResetIsUnknownUntilCounted()
	{
		assertFalse(known(carrying(0, 5, true, 1, 1)));
		assertFalse(known(carrying(0, 3, true, 1, 1)));
		tracker.onChatMessage(chat("This stack of fragments is roughly equivalent to 27 pieces of essence."));
		assertTrue(known(carrying(0, 3, true, 1, 1)));
		assertTrue(known(carrying(0, 1, true, 1, 1)));
	}

	@Test
	public void assumedFullStackIsUnknown()
	{
		assertEquals(108, fragments(carrying(0, 27, true, 1, 1)));
		assertFalse(known(carrying(0, 27, true, 1, 1)));
	}

	@Test
	public void visibleQuantityIsKnown()
	{
		assertTrue(known(carrying(0, 0, true, 37, 0)));
	}

	@Test
	public void pickedUpStackIsUnknown()
	{
		known(carrying(0, 5, false, 0, 0));
		assertFalse(known(carrying(0, 5, true, 1, 1)));
	}

	private int fragments(RawInventory raw)
	{
		return tracker.observe(raw).getFragments();
	}

	private boolean known(RawInventory raw)
	{
		return tracker.observe(raw).isFragmentsKnown();
	}

	private static ChatMessage chat(String text)
	{
		return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, "", 0);
	}

	/** Blocks take one slot each; a Fragment stack takes one slot with a hidden quantity. */
	private static RawInventory carrying(int dense, int dark, boolean stack, int containerQty, int widgetQty)
	{
		int used = dense + dark + (stack ? 1 : 0);
		return new RawInventory(dense, dark, stack, containerQty, widgetQty, SLOTS - used,
			true, true, false, false, true, false, -1);
	}
}
