package com.vetle.arceuusrc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

/**
 * Estimates the Fragment count the game hides. The item container reports the stack as 1, so
 * the tracker keeps the last estimate and corrects it from what it can see: a visible quantity
 * on the widget, Dark Blocks disappearing as they are chiselled, a full second inventory, and
 * the "roughly equivalent to N pieces of essence" chat line. Stateful; reset on login.
 */
@Singleton
public class FragmentTracker
{
	private static final int FRAGMENTS_PER_BLOCK = 4;
	private static final int MAX_FRAGMENTS = 111;
	private static final int TYPICAL_FULL_STACK = 108;
	private static final Pattern COUNT_MANY = Pattern.compile(
		"this stack of fragments is roughly equivalent to (\\d+) pieces? of essence",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern COUNT_ONE = Pattern.compile(
		"this stack of fragments is roughly equivalent to one piece of essence",
		Pattern.CASE_INSENSITIVE);

	private int trackedFragments;
	private boolean confirmed;
	private int lastDarkBlocks = -1;

	public void reset()
	{
		trackedFragments = 0;
		confirmed = false;
		lastDarkBlocks = -1;
	}

	public void onChatMessage(ChatMessage event)
	{
		String message = Text.removeTags(event.getMessage());
		if (COUNT_ONE.matcher(message).find())
		{
			trackedFragments = 1;
			confirmed = true;
			return;
		}
		Matcher many = COUNT_MANY.matcher(message);
		if (many.find())
		{
			try
			{
				trackedFragments = Math.min(MAX_FRAGMENTS, Integer.parseInt(many.group(1)));
				confirmed = true;
			}
			catch (NumberFormatException ignored)
			{
				// Keep the previous estimate.
			}
		}
	}

	/** Resolves the Fragment count for this tick's read and returns the finished snapshot. */
	public InventorySnapshot observe(RawInventory raw)
	{
		int fragments = resolveFragmentCount(
			raw.isFragmentItemPresent(),
			raw.getFragmentContainerQty(),
			raw.getFragmentWidgetQty(),
			raw.getDarkBlocks(),
			raw.getEmptySlots());
		return new InventorySnapshot(
			raw.getDenseBlocks(),
			raw.getDarkBlocks(),
			fragments,
			confirmed,
			raw.getEmptySlots(),
			raw.isHasChisel(),
			raw.isHasPickaxe(),
			raw.isHasInactiveBloodEssence(),
			raw.isHasActiveBloodEssence(),
			raw.isLanternEquipped(),
			raw.isLanternInInventory(),
			raw.getLanternItemId());
	}

	private int resolveFragmentCount(boolean hasFragmentItem, int containerQty, int widgetQty, int dark, int empty)
	{
		if (!hasFragmentItem)
		{
			trackedFragments = 0;
			confirmed = false;
			lastDarkBlocks = dark;
			return 0;
		}

		int visible = Math.max(containerQty, widgetQty);
		if (visible > 1)
		{
			trackedFragments = Math.min(MAX_FRAGMENTS, visible);
			confirmed = true;
		}
		else if (lastDarkBlocks >= 0 && dark < lastDarkBlocks)
		{
			trackedFragments = Math.min(MAX_FRAGMENTS, trackedFragments + FRAGMENTS_PER_BLOCK * (lastDarkBlocks - dark));
			confirmed = false;
		}
		else if (trackedFragments <= 1 && empty == 0 && dark > 0)
		{
			// Second inventory: fragment stack + full bag of dark. Quantity is hidden as 1.
			trackedFragments = TYPICAL_FULL_STACK;
			confirmed = false;
		}
		else if (trackedFragments <= 0)
		{
			trackedFragments = Math.max(1, visible);
			confirmed = false;
		}

		lastDarkBlocks = dark;
		return trackedFragments;
	}
}
