package com.vetle.arceuusrc;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** One test per display decision; names read as "config and state → what is drawn". */
public class GuidanceTest
{
	private static final Reminder IDLE = new Reminder(Reminder.Kind.IDLE, "Idle");
	private static final Reminder GEAR = new Reminder(Reminder.Kind.GEAR, "Need a chisel");

	private final StubConfig config = new StubConfig();

	@Test
	public void helperOnDrawsPathHighlightAndPanel()
	{
		Guidance g = Guidance.decide(config, mining(), List.of());
		assertTrue(g.isDrawFloorPath());
		assertTrue(g.isDrawMinimapPath());
		assertTrue(g.isHighlightClick());
		assertTrue(g.isShowPanel());
		assertFalse(g.isIdleTint());
	}

	@Test
	public void helperOffDrawsNothingButKeepsThePanelForReminders()
	{
		config.enableHelper = false;
		Guidance off = Guidance.decide(config, NextAction.idle(), List.of());
		assertFalse(off.isDrawFloorPath());
		assertFalse(off.isDrawMinimapPath());
		assertFalse(off.isHighlightClick());
		assertFalse(off.isShowPanel());
		assertTrue(Guidance.decide(config, NextAction.idle(), List.of(GEAR)).isShowPanel());
	}

	@Test
	public void pathDisplayChoosesFloorOrMinimap()
	{
		config.pathDisplay = PathDisplay.FLOOR;
		assertTrue(Guidance.decide(config, mining(), List.of()).isDrawFloorPath());
		assertFalse(Guidance.decide(config, mining(), List.of()).isDrawMinimapPath());
		config.pathDisplay = PathDisplay.MINIMAP;
		assertFalse(Guidance.decide(config, mining(), List.of()).isDrawFloorPath());
		assertTrue(Guidance.decide(config, mining(), List.of()).isDrawMinimapPath());
		config.pathDisplay = PathDisplay.OFF;
		assertFalse(Guidance.decide(config, mining(), List.of()).isDrawFloorPath());
		assertFalse(Guidance.decide(config, mining(), List.of()).isDrawMinimapPath());
	}

	@Test
	public void highlightFollowsItsOwnSwitch()
	{
		config.highlightNextClick = false;
		Guidance g = Guidance.decide(config, mining(), List.of());
		assertFalse(g.isHighlightClick());
		assertTrue(g.isDrawFloorPath());
	}

	@Test
	public void panelHidesWhenSwitchedOffEvenWithReminders()
	{
		config.showStatusPanel = false;
		assertFalse(Guidance.decide(config, mining(), List.of(GEAR)).isShowPanel());
	}

	@Test
	public void panelHidesWhenIdleWithNoReminders()
	{
		assertFalse(Guidance.decide(config, NextAction.idle(), List.of()).isShowPanel());
	}

	@Test
	public void idleTintNeedsTheSwitchAndAnIdleReminder()
	{
		assertFalse(Guidance.decide(config, mining(), List.of(IDLE)).isIdleTint());
		config.idleFlash = true;
		assertFalse(Guidance.decide(config, mining(), List.of(GEAR)).isIdleTint());
		assertTrue(Guidance.decide(config, mining(), List.of(GEAR, IDLE)).isIdleTint());
	}

	@Test
	public void farBindFollowsItsToggleAndTheHelper()
	{
		List<WorldPoint> area = List.of(ArceuusRcArea.DARK_APPROACH);
		Guidance shown = Guidance.decide(config, mining(), List.of(), FarBind.State.STEP_IN, area);
		assertEquals(FarBind.State.STEP_IN, shown.getFarBind());
		assertEquals(area, shown.getFarBindArea());

		config.showFarBind = false;
		Guidance toggledOff = Guidance.decide(config, mining(), List.of(), FarBind.State.STEP_IN, area);
		assertEquals(FarBind.State.NONE, toggledOff.getFarBind());
		assertTrue(toggledOff.getFarBindArea().isEmpty());

		config.showFarBind = true;
		config.enableHelper = false;
		Guidance helperOff = Guidance.decide(config, mining(), List.of(), FarBind.State.READY, area);
		assertEquals(FarBind.State.NONE, helperOff.getFarBind());
		assertTrue(helperOff.getFarBindArea().isEmpty());
	}

	@Test
	public void fragmentEstimateFollowsTheSnapshotItsToggleAndTheHelper()
	{
		InventorySnapshot inferred = carrying(13, false);
		FragmentEstimate shown = decide(inferred).getFragmentEstimate();
		assertTrue(shown.isShown());
		assertEquals(13, shown.getCount());
		assertFalse(shown.isConfirmed());

		assertTrue(decide(carrying(27, true)).getFragmentEstimate().isConfirmed());
		assertFalse(decide(carrying(0, false)).getFragmentEstimate().isShown());

		config.showFragmentEstimate = false;
		assertFalse(decide(inferred).getFragmentEstimate().isShown());

		config.showFragmentEstimate = true;
		config.enableHelper = false;
		assertFalse(decide(inferred).getFragmentEstimate().isShown());
	}

	@Test
	public void noneShowsNothing()
	{
		Guidance g = Guidance.none();
		assertFalse(g.isDrawFloorPath() || g.isDrawMinimapPath() || g.isHighlightClick()
			|| g.isShowPanel() || g.isIdleTint());
		assertTrue(g.getReminders().isEmpty());
		assertEquals(FarBind.State.NONE, g.getFarBind());
		assertTrue(g.getFarBindArea().isEmpty());
		assertFalse(g.getFragmentEstimate().isShown());
	}

	private Guidance decide(InventorySnapshot inv)
	{
		return Guidance.decide(config, mining(), List.of(), FarBind.State.NONE, List.of(), inv);
	}

	private static InventorySnapshot carrying(int fragments, boolean confirmed)
	{
		return new InventorySnapshot(0, 0, fragments, confirmed, 27, true, true, false, false, true, false, -1);
	}

	private static NextAction mining()
	{
		return new NextAction(RotationStep.MINE_FIRST, "Mine", Collections.emptyList(), null, null, null);
	}

	private static class StubConfig implements ArceuusRcHelperConfig
	{
		boolean enableHelper = true;
		boolean highlightNextClick = true;
		PathDisplay pathDisplay = PathDisplay.FLOOR_AND_MINIMAP;
		boolean showStatusPanel = true;
		boolean idleFlash = false;
		boolean showFarBind = true;
		boolean showFragmentEstimate = true;

		@Override
		public boolean showFragmentEstimate()
		{
			return showFragmentEstimate;
		}

		@Override
		public boolean showFarBind()
		{
			return showFarBind;
		}

		@Override
		public boolean enableHelper()
		{
			return enableHelper;
		}

		@Override
		public boolean highlightNextClick()
		{
			return highlightNextClick;
		}

		@Override
		public PathDisplay pathDisplay()
		{
			return pathDisplay;
		}

		@Override
		public boolean showStatusPanel()
		{
			return showStatusPanel;
		}

		@Override
		public boolean idleFlash()
		{
			return idleFlash;
		}
	}
}
