package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.ArceuusRcHelperConfig;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Cause;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Result;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.UnknownStyle;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Verdict;
import java.awt.Color;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

/**
 * PROTOTYPE, THROWAWAY (wayfinder #22): row rendering shared by the Status Panel and the
 * separate-panel variants.
 */
final class CameraCheckRows
{
	private CameraCheckRows()
	{
	}

	static void addSummary(PanelComponent panel, CameraCheckPrototype prototype, ArceuusRcHelperConfig config)
	{
		panel.getChildren().add(LineComponent.builder()
			.left("Camera")
			.leftColor(Color.WHITE)
			.right(prototype.summary(config.protoSummaryStyle()))
			.rightColor(prototype.summaryColor(config.protoSummaryStyle()))
			.build());
	}

	static void addRows(PanelComponent panel, CameraCheckPrototype prototype, ArceuusRcHelperConfig config)
	{
		for (Result r : prototype.getResults())
		{
			Verdict v = r.getVerdict();
			String right;
			if (v == Verdict.UNKNOWN)
			{
				UnknownStyle style = config.protoUnknownStyle();
				if (style == UnknownStyle.HIDE)
				{
					continue;
				}
				right = style == UnknownStyle.QUESTION ? "?" : "unknown";
			}
			else if (v == Verdict.CLEAR)
			{
				right = v.getLabel();
			}
			else
			{
				right = v.getLabel();
				if (r.getCause() != Cause.NONE)
				{
					right += " · " + r.getCause().word(config.protoCauseWords());
				}
			}
			panel.getChildren().add(LineComponent.builder()
				.left("  " + r.getHop().getName())
				.leftColor(Color.LIGHT_GRAY)
				.right(right)
				.rightColor(v.getColor())
				.build());
		}
	}
}
