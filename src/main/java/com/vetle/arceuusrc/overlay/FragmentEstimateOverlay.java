package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.FragmentEstimate;
import com.vetle.arceuusrc.Helper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import javax.inject.Inject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Draws the Fragment Estimate on the Fragment stack in the inventory, which the game leaves
 * unnumbered. Cyan when the game confirmed the count, yellow when the plugin inferred it. Reads
 * the Guidance decided this tick and draws; nothing is decided here.
 */
public class FragmentEstimateOverlay extends WidgetItemOverlay
{
	private static final Color CONFIRMED = Color.CYAN;
	private static final Color INFERRED = Color.YELLOW;
	/** Baseline offset from the icon's top-left, matching the core Runecraft pouch overlay. */
	private static final int TEXT_OFFSET_Y = 15;

	private final Helper helper;
	private final TextComponent text = new TextComponent();

	@Inject
	private FragmentEstimateOverlay(Helper helper)
	{
		this.helper = helper;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem itemWidget)
	{
		if (itemId != ItemID.BIGBLANKRUNE)
		{
			return;
		}
		FragmentEstimate estimate = helper.getGuidance().getFragmentEstimate();
		if (!estimate.isShown())
		{
			return;
		}
		net.runelite.api.Point location = itemWidget.getCanvasLocation();
		if (location == null)
		{
			return;
		}
		graphics.setFont(FontManager.getRunescapeSmallFont());
		text.setText(String.valueOf(estimate.getCount()));
		text.setColor(estimate.isConfirmed() ? CONFIRMED : INFERRED);
		text.setPosition(new Point(location.getX(), location.getY() + TEXT_OFFSET_Y));
		text.render(graphics);
	}
}
