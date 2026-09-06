package com.vetle.arceuusrc;

import java.util.List;
import lombok.Value;

/**
 * What the Helper decided to show this tick: the Next Action, the active Reminders, and which of
 * the highlight, the Path on floor and minimap, the Status Panel and the Idle Tint are drawn.
 * Decided once per tick from the config and the tick's state so that overlays only draw.
 */
@Value
public class Guidance
{
	private static final Guidance NONE =
		new Guidance(NextAction.idle(), List.of(), false, false, false, false, false);

	NextAction nextAction;
	/** Reminders that apply, in display order. */
	List<Reminder> reminders;
	boolean drawFloorPath;
	boolean drawMinimapPath;
	boolean highlightClick;
	boolean showPanel;
	boolean idleTint;

	/** Nothing shown: before the first tick and after reset. */
	public static Guidance none()
	{
		return NONE;
	}

	public static Guidance decide(ArceuusRcHelperConfig config, NextAction nextAction, List<Reminder> reminders)
	{
		boolean helperOn = config.enableHelper();
		boolean inRotation = helperOn && nextAction.getStep() != RotationStep.IDLE;
		return new Guidance(
			nextAction,
			reminders,
			helperOn && config.pathDisplay().showsFloor(),
			helperOn && config.pathDisplay().showsMinimap(),
			helperOn && config.highlightNextClick(),
			config.showStatusPanel() && (inRotation || !reminders.isEmpty()),
			config.idleFlash() && hasIdle(reminders));
	}

	private static boolean hasIdle(List<Reminder> reminders)
	{
		for (Reminder reminder : reminders)
		{
			if (reminder.getKind() == Reminder.Kind.IDLE)
			{
				return true;
			}
		}
		return false;
	}
}
