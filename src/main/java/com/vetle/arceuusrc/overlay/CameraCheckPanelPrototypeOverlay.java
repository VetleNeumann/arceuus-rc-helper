package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.ArceuusRcHelperConfig;
import com.vetle.arceuusrc.ArceuusRcHelperPlugin;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.PanelStyle;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * PROTOTYPE, THROWAWAY (wayfinder #22): the "separate panel" variant of the Camera Check.
 */
public class CameraCheckPanelPrototypeOverlay extends OverlayPanel
{
	private static final Dimension SIZE = new Dimension(156, 0);

	private final CameraCheckPrototype prototype;
	private final ArceuusRcHelperConfig config;

	@Inject
	private CameraCheckPanelPrototypeOverlay(
		ArceuusRcHelperPlugin plugin,
		CameraCheckPrototype prototype,
		ArceuusRcHelperConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.prototype = prototype;
		this.config = config;
		panelComponent.setBorder(new Rectangle(8, 8, 8, 8));
		panelComponent.setGap(new Point(0, 4));
		panelComponent.setPreferredSize(SIZE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!prototype.enabled() || config.protoPanelStyle() != PanelStyle.SEPARATE)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Camera Check").build());
		CameraCheckRows.addSummary(panelComponent, prototype, config);
		CameraCheckRows.addRows(panelComponent, prototype, config);
		return super.render(graphics);
	}
}
