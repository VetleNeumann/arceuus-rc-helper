package com.vetle.arceuusrc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ArceuusRcHelperConfig.GROUP)
public interface ArceuusRcHelperConfig extends Config
{
	String GROUP = "arceuus-rc-helper";
	String PATH_DISPLAY_KEY = "pathDisplay";
	String PATH_PROVIDER_KEY = "pathProvider";

	@ConfigSection(
		name = "Helper",
		description = "Click-here rotation guidance",
		position = 0
	)
	String helperSection = "helper";

	@ConfigSection(
		name = "Reminders",
		description = "Gear, blood essence, and idle warnings",
		position = 1
	)
	String reminderSection = "reminders";

	@ConfigSection(
		name = "Prototype",
		description = "THROWAWAY: Camera Check look prototype (wayfinder #22)",
		position = 9,
		closedByDefault = false
	)
	String prototypeSection = "prototype";

	@ConfigItem(
		keyName = "mode",
		name = "Rune type",
		description = "Which altar to guide. Auto uses soul at 90 Runecraft, otherwise blood.",
		section = helperSection,
		position = 0
	)
	default RcMode mode()
	{
		return RcMode.AUTO;
	}

	@ConfigItem(
		keyName = "enableHelper",
		name = "Enable helper",
		description = "Show the next-step overlay and status panel",
		section = helperSection,
		position = 1
	)
	default boolean enableHelper()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightNextClick",
		name = "Highlight next click",
		description = "Highlight the next object's clickbox (runestone, shortcut, or altar)",
		section = helperSection,
		position = 2
	)
	default boolean highlightNextClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = PATH_DISPLAY_KEY,
		name = "Path display",
		description = "Where to draw the path to your next destination. Click highlights are unaffected.",
		section = helperSection,
		position = 3
	)
	default PathDisplay pathDisplay()
	{
		return PathDisplay.FLOOR_AND_MINIMAP;
	}

	@ConfigItem(
		keyName = PATH_PROVIDER_KEY,
		name = "Path source",
		description = "Plugin lines draws this plugin's own path. Shortest Path hands the destination to the Shortest Path plugin instead, coloured by the current step, and falls back to plugin lines when that plugin is not running.",
		section = helperSection,
		position = 4
	)
	default PathProvider pathProvider()
	{
		return PathProvider.PLUGIN;
	}

	@ConfigItem(
		keyName = "showStatusPanel",
		name = "Show status panel",
		description = "Compact panel for the current step, inventory counts, and reminders",
		section = helperSection,
		position = 5
	)
	default boolean showStatusPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFarBind",
		name = "Far Bind",
		description = "Bloods only: say when the Blood Altar can be clicked from where you stand, "
			+ "and mark the tiles to step to when you are just outside that range",
		section = helperSection,
		position = 6
	)
	default boolean showFarBind()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFragmentEstimate",
		name = "Fragment estimate",
		description = "Draw the estimated stack size on the fragments in your inventory: "
			+ "yellow while short of a full stack, cyan once it is full, "
			+ "a question mark until you use Count on a stack the plugin did not watch being made",
		section = helperSection,
		position = 7
	)
	default boolean showFragmentEstimate()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gearReminder",
		name = "Gear reminders",
		description = "Remind you to bring a chisel (including jeweller's), a pickaxe, and an abyssal lantern",
		section = reminderSection,
		position = 0
	)
	default boolean gearReminder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lanternLogCheck",
		name = "Check lantern logs",
		description = "When gear reminders are on, warn if the lantern is unlit or using logs that don't help this method",
		section = reminderSection,
		position = 1
	)
	default boolean lanternLogCheck()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bloodEssenceReminder",
		name = "Blood essence reminder",
		description = "Remind you to bring and activate blood essence (blood mode only)",
		section = reminderSection,
		position = 2
	)
	default boolean bloodEssenceReminder()
	{
		return true;
	}

	@Range(min = 0, max = 1000)
	@ConfigItem(
		keyName = "bloodEssenceLowCharges",
		name = "Low essence charges",
		description = "Warn when activated blood essence is at or below this many charges",
		section = reminderSection,
		position = 3
	)
	default int bloodEssenceLowCharges()
	{
		return 100;
	}

	@Range(min = 3, max = 120)
	@ConfigItem(
		keyName = "idleReminderSeconds",
		name = "Idle reminder (seconds)",
		description = "Warn if you stand still in the Arceuus RC area this long",
		section = reminderSection,
		position = 4
	)
	default int idleReminderSeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "idleFlash",
		name = "Idle screen tint",
		description = "Gently tint the screen when idle. Off by default.",
		section = reminderSection,
		position = 5
	)
	default boolean idleFlash()
	{
		return false;
	}

	@ConfigItem(keyName = "protoCameraCheck", name = "Camera Check prototype", description = "Judge every Blood Hop from its Landing Tile", section = prototypeSection, position = 0)
	default boolean protoCameraCheck()
	{
		return true;
	}

	@ConfigItem(keyName = "protoGhostStyle", name = "Ghost style", description = "How the predicted target shapes are drawn", section = prototypeSection, position = 1)
	default com.vetle.arceuusrc.overlay.CameraCheckPrototype.GhostStyle protoGhostStyle()
	{
		return com.vetle.arceuusrc.overlay.CameraCheckPrototype.GhostStyle.OUTLINE;
	}

	@ConfigItem(keyName = "protoPanelStyle", name = "Panel style", description = "Rows in the Status Panel, a separate panel, or summary only", section = prototypeSection, position = 2)
	default com.vetle.arceuusrc.overlay.CameraCheckPrototype.PanelStyle protoPanelStyle()
	{
		return com.vetle.arceuusrc.overlay.CameraCheckPrototype.PanelStyle.STATUS_ROWS;
	}

	@ConfigItem(keyName = "protoCauseWords", name = "Cause words", description = "Long or short cause vocabulary", section = prototypeSection, position = 3)
	default com.vetle.arceuusrc.overlay.CameraCheckPrototype.CauseWords protoCauseWords()
	{
		return com.vetle.arceuusrc.overlay.CameraCheckPrototype.CauseWords.SHORT;
	}

	@ConfigItem(keyName = "protoSummaryStyle", name = "Summary style", description = "WORST reports Obstructed too; HIDDEN_ONLY says OK unless something is Hidden", section = prototypeSection, position = 4)
	default com.vetle.arceuusrc.overlay.CameraCheckPrototype.SummaryStyle protoSummaryStyle()
	{
		return com.vetle.arceuusrc.overlay.CameraCheckPrototype.SummaryStyle.WORST;
	}

	@ConfigItem(keyName = "protoUnknownStyle", name = "Unknown style", description = "How a Hop with unknown geometry shows", section = prototypeSection, position = 5)
	default com.vetle.arceuusrc.overlay.CameraCheckPrototype.UnknownStyle protoUnknownStyle()
	{
		return com.vetle.arceuusrc.overlay.CameraCheckPrototype.UnknownStyle.QUESTION;
	}

	@ConfigItem(keyName = "protoSimulateUnknown", name = "Simulate unknown geometry", description = "Treat the Scramble Hops as unknown to see how the panel handles it", section = prototypeSection, position = 6)
	default boolean protoSimulateUnknown()
	{
		return false;
	}
}
