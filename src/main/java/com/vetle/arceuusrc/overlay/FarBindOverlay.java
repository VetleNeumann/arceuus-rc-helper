package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.Helper;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws the Far Bind Area: the tiles near the player from which the Blood Altar can be clicked,
 * shown only while the player stands just outside it. The tile list is decided once per tick in
 * the Guidance; this overlay only projects it.
 */
public class FarBindOverlay extends Overlay
{
	/** Same colour as the "step in" Status Panel state, so one colour means one thing. */
	static final Color AREA = Color.ORANGE;
	private static final Color FILL = new Color(AREA.getRed(), AREA.getGreen(), AREA.getBlue(), 40);

	private final Client client;
	private final Helper helper;

	@Inject
	private FarBindOverlay(Client client, Helper helper)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.helper = helper;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<WorldPoint> area = helper.getGuidance().getFarBindArea();
		if (area.isEmpty())
		{
			return null;
		}
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		for (WorldPoint tile : area)
		{
			LocalPoint local = LocalPoint.fromWorld(worldView, tile);
			if (local == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, local);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, AREA, FILL, graphics.getStroke());
			}
		}
		return null;
	}
}
