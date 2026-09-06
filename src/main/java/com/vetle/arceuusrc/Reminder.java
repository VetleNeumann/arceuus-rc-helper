package com.vetle.arceuusrc;

import lombok.Value;

/** A warning that something outside the Rotation needs the player's attention. */
@Value
public class Reminder
{
	public enum Kind
	{
		GEAR,
		LANTERN,
		ESSENCE,
		IDLE
	}

	Kind kind;
	String text;
}
