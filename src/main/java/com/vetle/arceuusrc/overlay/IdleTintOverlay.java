package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.ArceuusRcHelperConfig;
import com.vetle.arceuusrc.Helper;
import com.vetle.arceuusrc.Reminder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class IdleTintOverlay extends Overlay
{
	private static final Color TINT = new Color(255, 70, 70, 35);

	private final Client client;
	private final ArceuusRcHelperConfig config;
	private final Helper helper;

	@Inject
	private IdleTintOverlay(Client client, ArceuusRcHelperConfig config, Helper helper)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		this.client = client;
		this.config = config;
		this.helper = helper;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.idleFlash() || !isIdle())
		{
			return null;
		}

		graphics.setColor(TINT);
		graphics.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		return null;
	}

	private boolean isIdle()
	{
		for (Reminder reminder : helper.getActiveReminders())
		{
			if (reminder.getKind() == Reminder.Kind.IDLE)
			{
				return true;
			}
		}
		return false;
	}
}
