package com.vetle.arceuusrc;

import java.awt.Color;
import java.util.List;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Click-here steps for the standard Arceuus blood/soul rotation.
 */
@Getter
public enum RotationStep
{
	MINE_FIRST("Mine", Color.CYAN),
	GO_DARK_FIRST("Venerate", new Color(160, 80, 255)),
	CHISEL_AND_RETURN("Chisel", Color.ORANGE),
	MINE_SECOND("Mine again", Color.CYAN),
	GO_DARK_SECOND("Venerate", new Color(160, 80, 255)),
	GO_ALTAR("Altar", new Color(220, 40, 40)),
	CRAFT_FRAGMENTS("Craft", new Color(220, 40, 40)),
	CHISEL_AT_ALTAR("Chisel", Color.ORANGE),
	CRAFT_REMAINING("Craft", new Color(220, 40, 40)),
	RETURN_TO_MINE("Return", Color.CYAN),
	IDLE("Waiting…", Color.GRAY);

	private final String label;
	private final Color color;

	RotationStep(String label, Color color)
	{
		this.label = label;
		this.color = color;
	}

	/** The one-line instruction for this Step, worded for the Rune being crafted. */
	public String detail(RcMode rune)
	{
		String altar = rune == RcMode.SOUL ? "soul" : "blood";
		switch (this)
		{
			case MINE_FIRST:
				return "Fill your first inventory";
			case GO_DARK_FIRST:
				return "Click the Dark Altar to venerate all dense blocks";
			case CHISEL_AND_RETURN:
				return "Use chisel on dark blocks while running back to the mine";
			case MINE_SECOND:
				return "Fill your second inventory";
			case GO_DARK_SECOND:
				return "Venerate the second inventory at the Dark Altar";
			case GO_ALTAR:
				return "Carry fragments + dark blocks to the " + altar + " altar";
			case CRAFT_FRAGMENTS:
				return "Click the " + altar + " altar to craft your fragments";
			case CHISEL_AT_ALTAR:
				return "Chisel the remaining dark blocks into fragments";
			case CRAFT_REMAINING:
				return "Click the " + altar + " altar again for the second batch";
			case RETURN_TO_MINE:
				return "Take the shortcut back to the dense essence mine";
			default:
				return label;
		}
	}

	public List<WorldPoint> highlightTiles(RcMode resolvedMode)
	{
		switch (this)
		{
			case MINE_FIRST:
			case MINE_SECOND:
				return ArceuusRcArea.RUNESTONES;
			case GO_DARK_FIRST:
			case GO_DARK_SECOND:
				return List.of(ArceuusRcArea.DARK_ALTAR);
			case CHISEL_AND_RETURN:
				return List.of(ArceuusRcArea.NORTH_SHORTCUT, ArceuusRcArea.MINE_STAND);
			case RETURN_TO_MINE:
				return List.of(ArceuusRcArea.BOULDER_SHORTCUT, ArceuusRcArea.SHORTCUT, ArceuusRcArea.NORTH_SHORTCUT, ArceuusRcArea.MINE_STAND);
			case GO_ALTAR:
			case CRAFT_FRAGMENTS:
			case CRAFT_REMAINING:
				return List.of(resolvedMode == RcMode.SOUL ? ArceuusRcArea.SOUL_ALTAR : ArceuusRcArea.BLOOD_ALTAR);
			case CHISEL_AT_ALTAR:
				return List.of(resolvedMode == RcMode.SOUL ? ArceuusRcArea.SOUL_ALTAR : ArceuusRcArea.BLOOD_ALTAR);
			default:
				return List.of();
		}
	}
}
