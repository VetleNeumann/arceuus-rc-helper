package com.vetle.arceuusrc;

import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * What the Helper decided to show this tick: the Next Action, the active Reminders, the Fragment
 * Estimate, and which of the highlight, the Path on floor and minimap, the Status Panel and the
 * Idle Tint are drawn.
 * Decided once per tick from the config and the tick's state so that overlays only draw.
 */
@Value
public class Guidance
{
	private static final Guidance NONE =
		new Guidance(NextAction.idle(), List.of(), false, false, false, false, false, FarBind.State.NONE, List.of(),
			FragmentEstimate.none());

	NextAction nextAction;
	/** Reminders that apply, in display order. */
	List<Reminder> reminders;
	boolean drawFloorPath;
	boolean drawMinimapPath;
	boolean highlightClick;
	boolean showPanel;
	boolean idleTint;
	/** Far Bind row on the Status Panel; NONE when hidden by config or not applicable. */
	FarBind.State farBind;
	/** Far Bind Area tiles to draw; empty unless the state is STEP_IN and the row is shown. */
	List<WorldPoint> farBindArea;
	/** Number drawn on the Fragment stack; none when there is no stack or it is hidden by config. */
	FragmentEstimate fragmentEstimate;

	/** Nothing shown: before the first tick and after reset. */
	public static Guidance none()
	{
		return NONE;
	}

	public static Guidance decide(ArceuusRcHelperConfig config, NextAction nextAction, List<Reminder> reminders)
	{
		return decide(config, nextAction, reminders, FarBind.State.NONE, List.of());
	}

	public static Guidance decide(
		ArceuusRcHelperConfig config,
		NextAction nextAction,
		List<Reminder> reminders,
		FarBind.State farBind,
		List<WorldPoint> farBindArea)
	{
		return decide(config, nextAction, reminders, farBind, farBindArea, InventorySnapshot.empty());
	}

	public static Guidance decide(
		ArceuusRcHelperConfig config,
		NextAction nextAction,
		List<Reminder> reminders,
		FarBind.State farBind,
		List<WorldPoint> farBindArea,
		InventorySnapshot inventory)
	{
		boolean helperOn = config.enableHelper();
		boolean inRotation = helperOn && nextAction.getStep() != RotationStep.IDLE;
		boolean showFarBind = helperOn && config.showFarBind();
		return new Guidance(
			nextAction,
			reminders,
			helperOn && config.pathDisplay().showsFloor(),
			helperOn && config.pathDisplay().showsMinimap(),
			helperOn && config.highlightNextClick(),
			config.showStatusPanel() && (inRotation || !reminders.isEmpty()),
			config.idleFlash() && hasIdle(reminders),
			showFarBind ? farBind : FarBind.State.NONE,
			showFarBind ? farBindArea : List.of(),
			helperOn && config.showFragmentEstimate() ? FragmentEstimate.of(inventory) : FragmentEstimate.none());
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
