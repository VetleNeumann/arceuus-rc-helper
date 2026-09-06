package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.FarBind;
import com.vetle.arceuusrc.Guidance;
import com.vetle.arceuusrc.NextAction;
import com.vetle.arceuusrc.InventorySnapshot;
import com.vetle.arceuusrc.RcMode;
import com.vetle.arceuusrc.Reminder;
import com.vetle.arceuusrc.Helper;
import com.vetle.arceuusrc.RotationStep;
import com.vetle.arceuusrc.ArceuusRcHelperPlugin;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class StatusOverlay extends OverlayPanel
{
	private static final Color LABEL = Color.WHITE;
	private static final Color WARN = new Color(255, 168, 76);
	private static final Color ESSENCE_OK = new Color(120, 200, 140);
	private static final Dimension SIZE = new Dimension(156, 0);

	private final Helper helper;

	@Inject
	private StatusOverlay(
		ArceuusRcHelperPlugin plugin,
		Helper helper)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.helper = helper;
		panelComponent.setBorder(new Rectangle(8, 8, 8, 8));
		panelComponent.setGap(new Point(0, 4));
		panelComponent.setPreferredSize(SIZE);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Arceuus RC Helper");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Guidance guidance = helper.getGuidance();
		if (!guidance.isShowPanel())
		{
			return null;
		}

		NextAction action = guidance.getNextAction();
		boolean inRotation = action.getStep() != RotationStep.IDLE;
		List<Reminder> reminders = guidance.getReminders();

		RcMode mode = helper.getResolvedMode();
		InventorySnapshot inv = helper.getSnapshot();

		panelComponent.getChildren().add(LineComponent.builder()
			.left(mode == RcMode.SOUL ? "Souls" : "Bloods")
			.leftColor(mode.getColor())
			.build());

		if (inRotation)
		{
			Color stepColor = action.getColor() != null ? action.getColor() : Color.WHITE;
			panelComponent.getChildren().add(line("Next", action.getStep().getLabel(), stepColor));
		}

		panelComponent.getChildren().add(line("Dense", String.valueOf(inv.getDenseBlocks()), LABEL));
		panelComponent.getChildren().add(line("Dark", String.valueOf(inv.getDarkBlocks()), LABEL));
		panelComponent.getChildren().add(line("Trips", String.valueOf(helper.getTripsCompleted()), LABEL));

		if (mode == RcMode.BLOOD)
		{
			Color essenceColor = inv.isHasActiveBloodEssence() ? ESSENCE_OK : LABEL;
			panelComponent.getChildren().add(line("Essence", essenceText(inv), essenceColor));
		}

		FarBind.State farBind = guidance.getFarBind();
		if (inRotation && farBind != FarBind.State.NONE)
		{
			panelComponent.getChildren().add(line("Far Bind", farBind.getLabel(), farBindColor(farBind)));
		}

		for (Reminder reminder : reminders)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(reminder.getText())
				.leftColor(WARN)
				.build());
		}

		return super.render(graphics);
	}

	private static LineComponent line(String left, String right, Color rightColor)
	{
		return LineComponent.builder()
			.left(left)
			.leftColor(LABEL)
			.right(right)
			.rightColor(rightColor)
			.build();
	}

	/** One colour per meaning: neutral when nothing to do, area colour when a step fixes it, warn when nothing does. */
	private static Color farBindColor(FarBind.State state)
	{
		switch (state)
		{
			case STEP_IN:
				return FarBindOverlay.AREA;
			case NOT_LOADED:
				return WARN;
			default:
				return LABEL;
		}
	}

	/** Plain state of the Blood Essence; whether it needs attention is a Reminder, not this row. */
	private String essenceText(InventorySnapshot inv)
	{
		if (inv.isHasActiveBloodEssence())
		{
			Integer charges = helper.getBloodEssenceCharges();
			return charges != null ? charges + " charges" : "active";
		}
		return inv.isHasInactiveBloodEssence() ? "inactive" : "none";
	}
}
