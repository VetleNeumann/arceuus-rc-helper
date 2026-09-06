package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.ArceuusRcHelperConfig;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.GhostStyle;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Result;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * PROTOTYPE, THROWAWAY (wayfinder #22): recomputes the Camera Check each frame and draws the
 * predicted on-screen shape ("ghost") of every Blood Hop's target from its Landing Tile.
 */
public class CameraCheckGhostPrototypeOverlay extends Overlay
{
	private static final Stroke DASHED = new BasicStroke(
		1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f);

	private final CameraCheckPrototype prototype;
	private final ArceuusRcHelperConfig config;

	@Inject
	private CameraCheckGhostPrototypeOverlay(CameraCheckPrototype prototype, ArceuusRcHelperConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.prototype = prototype;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!prototype.enabled())
		{
			return null;
		}
		prototype.update();
		GhostStyle style = config.protoGhostStyle();
		if (style == GhostStyle.NONE)
		{
			return null;
		}
		for (Result r : prototype.getResults())
		{
			if (r.getShape() == null)
			{
				continue;
			}
			Color line = r.getVerdict().getColor();
			Color fill = new Color(line.getRed(), line.getGreen(), line.getBlue(), style == GhostStyle.FILLED ? 60 : 0);
			Stroke old = graphics.getStroke();
			graphics.setStroke(DASHED);
			graphics.setColor(fill);
			graphics.fill(r.getShape());
			graphics.setColor(line);
			graphics.draw(r.getShape());
			graphics.setStroke(old);
			if (style == GhostStyle.LABELLED)
			{
				Rectangle b = r.getShape().getBounds();
				String text = r.getHop().getName() + " " + r.getVerdict().getLabel();
				if (r.getCause() != CameraCheckPrototype.Cause.NONE)
				{
					text += " (" + r.getCause().word(config.protoCauseWords()) + ")";
				}
				OverlayUtil.renderTextLocation(graphics, new Point(b.x, b.y - 4), text, line);
			}
		}
		return null;
	}
}
