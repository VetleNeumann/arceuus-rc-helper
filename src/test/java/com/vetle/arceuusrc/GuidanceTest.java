package com.vetle.arceuusrc;

import java.util.Collections;
import java.util.List;
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
	public void noneShowsNothing()
	{
		Guidance g = Guidance.none();
		assertFalse(g.isDrawFloorPath() || g.isDrawMinimapPath() || g.isHighlightClick()
			|| g.isShowPanel() || g.isIdleTint());
		assertTrue(g.getReminders().isEmpty());
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
