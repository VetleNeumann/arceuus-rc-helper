package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.HelperAction;
import com.vetle.arceuusrc.InventorySnapshot;
import com.vetle.arceuusrc.RcMode;
import com.vetle.arceuusrc.ReminderService;
import com.vetle.arceuusrc.Helper;
import com.vetle.arceuusrc.RotationStep;
import com.vetle.arceuusrc.ArceuusRcHelperConfig;
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

	private final ArceuusRcHelperConfig config;
	private final Helper helper;
	private final ReminderService reminderService;

	@Inject
	private StatusOverlay(
		ArceuusRcHelperPlugin plugin,
		ArceuusRcHelperConfig config,
		Helper helper,
		ReminderService reminderService)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.config = config;
		this.helper = helper;
		this.reminderService = reminderService;
		panelComponent.setBorder(new Rectangle(8, 8, 8, 8));
		panelComponent.setGap(new Point(0, 4));
		panelComponent.setPreferredSize(SIZE);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Arceuus RC Helper");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStatusPanel())
		{
			return null;
		}

		HelperAction action = helper.getCurrentAction();
		boolean inRotation = config.enableHelper()
			&& action != null
			&& action.getStep() != RotationStep.IDLE;
		List<String> warnings = reminderService.getWarnings();
		if (!inRotation && warnings.isEmpty())
		{
			return null;
		}

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

		if (inv != null)
		{
			panelComponent.getChildren().add(line("Dense", String.valueOf(inv.getDenseBlocks()), LABEL));
			panelComponent.getChildren().add(line("Dark", String.valueOf(inv.getDarkBlocks()), LABEL));
			panelComponent.getChildren().add(line("Fragments", String.valueOf(inv.getFragments()), LABEL));
			panelComponent.getChildren().add(line("Trips", String.valueOf(helper.getTripsCompleted()), LABEL));

			if (mode == RcMode.BLOOD)
			{
				panelComponent.getChildren().add(line("Essence", essenceText(inv), essenceColor(inv)));
			}
		}

		for (String warning : warnings)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(warning)
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

	private String essenceText(InventorySnapshot inv)
	{
		if (inv.isHasActiveBloodEssence())
		{
			Integer charges = reminderService.getBloodEssenceCharges();
			if (charges != null)
			{
				return charges + " charges";
			}
			return "active";
		}
		if (inv.isHasInactiveBloodEssence())
		{
			return config.bloodEssenceReminder() ? "activate" : "inactive";
		}
		return config.bloodEssenceReminder() ? "need one" : "none";
	}

	private Color essenceColor(InventorySnapshot inv)
	{
		if (inv.isHasActiveBloodEssence())
		{
			Integer charges = reminderService.getBloodEssenceCharges();
			if (config.bloodEssenceReminder()
				&& charges != null
				&& charges <= config.bloodEssenceLowCharges())
			{
				return WARN;
			}
			return ESSENCE_OK;
		}
		return config.bloodEssenceReminder() ? WARN : LABEL;
	}
}
