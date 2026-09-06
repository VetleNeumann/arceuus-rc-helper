package com.vetle.arceuusrc;

import java.awt.Color;
import lombok.Getter;

@Getter
public enum RcMode
{
	BLOOD("Blood", new Color(196, 42, 50)),
	SOUL("Soul", new Color(58, 186, 198)),
	AUTO("Auto", Color.WHITE);

	/** Runecraft level at which Auto switches from Blood to Soul. */
	public static final int SOUL_LEVEL = 90;

	private final String label;
	private final Color color;

	RcMode(String label, Color color)
	{
		this.label = label;
		this.color = color;
	}

	/** Turns the configured Mode into the Rune for this Trip; Auto follows the Runecraft level. */
	public static RcMode resolve(RcMode configured, int runecraftLevel)
	{
		if (configured != AUTO)
		{
			return configured;
		}
		return runecraftLevel >= SOUL_LEVEL ? SOUL : BLOOD;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
