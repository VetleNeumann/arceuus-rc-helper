package com.vetle.arceuusrc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import com.vetle.arceuusrc.game.ItemChargesStore;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.util.Text;

/**
 * Decides which Reminders apply to an Observation: Gear, Lantern, Essence and Idle, in that
 * order. Blood Essence charges are remembered between ticks from chat messages and the Item
 * Charges store; the idle timer is driven by the clock the caller passes in.
 */
@Singleton
public class Reminders
{
	private static final Pattern ESSENCE_CHARGES = Pattern.compile(
		"Your blood essence has (\\d{1,4}) charges? remaining",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern ESSENCE_EXTRACT = Pattern.compile(
		"You manage to extract power from the Blood Essence and craft (\\d{1,3}) extra runes?",
		Pattern.CASE_INSENSITIVE);
	private static final String ESSENCE_ACTIVATE = "You activate the blood essence.";
	private static final int MAX_BLOOD_ESSENCE_CHARGES = 1000;

	private final ArceuusRcHelperConfig config;
	private final ItemChargesStore itemCharges;

	private Integer bloodEssenceCharges;
	private Instant lastMoveAt;
	private WorldPoint lastTile;

	@Inject
	Reminders(ArceuusRcHelperConfig config, ItemChargesStore itemCharges)
	{
		this.config = config;
		this.itemCharges = itemCharges;
	}

	public void reset()
	{
		bloodEssenceCharges = null;
		lastMoveAt = null;
		lastTile = null;
	}

	/** Known Blood Essence charges, or null when none is active or the count is not known yet. */
	public Integer bloodEssenceCharges()
	{
		return bloodEssenceCharges;
	}

	public List<Reminder> evaluate(Observation obs, Instant now)
	{
		if (!obs.isInArceuus())
		{
			lastMoveAt = null;
			lastTile = null;
			return List.of();
		}

		InventorySnapshot inv = obs.getInventory();
		RcMode rune = obs.getRune();
		syncBloodEssenceCharges(inv);

		List<Reminder> reminders = new ArrayList<>();
		if (config.gearReminder())
		{
			addGear(inv, reminders);
			addLantern(inv, rune, reminders);
		}
		addEssence(inv, rune, reminders);
		if (isIdle(obs, now))
		{
			reminders.add(new Reminder(Reminder.Kind.IDLE, "Idle"));
		}
		return reminders;
	}

	public void onChatMessage(ChatMessage event)
	{
		if (event.getMessage() == null)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		Matcher check = ESSENCE_CHARGES.matcher(message);
		if (check.find())
		{
			setBloodEssenceCharges(Integer.parseInt(check.group(1)));
			return;
		}
		Matcher extract = ESSENCE_EXTRACT.matcher(message);
		if (extract.find())
		{
			int used = Integer.parseInt(extract.group(1));
			int current = bloodEssenceCharges != null
				? bloodEssenceCharges
				: itemCharges.bloodEssenceCharges();
			if (current >= 0)
			{
				setBloodEssenceCharges(Math.max(0, current - used));
			}
			return;
		}
		if (message.contains(ESSENCE_ACTIVATE))
		{
			setBloodEssenceCharges(MAX_BLOOD_ESSENCE_CHARGES);
		}
	}

	private static void addGear(InventorySnapshot inv, List<Reminder> out)
	{
		if (!inv.isHasChisel())
		{
			out.add(new Reminder(Reminder.Kind.GEAR, "Need a chisel"));
		}
		if (!inv.isHasPickaxe())
		{
			out.add(new Reminder(Reminder.Kind.GEAR, "Need a pickaxe"));
		}
	}

	private void addLantern(InventorySnapshot inv, RcMode rune, List<Reminder> out)
	{
		if (!inv.isLanternEquipped())
		{
			out.add(new Reminder(Reminder.Kind.LANTERN,
				inv.isLanternInInventory() ? "Equip your lantern" : "Need a lantern"));
			return;
		}
		if (!config.lanternLogCheck())
		{
			return;
		}
		int id = inv.getLanternItemId();
		if (id == ItemID.ABYSSAL_LANTERN)
		{
			out.add(new Reminder(Reminder.Kind.LANTERN, "Light your lantern"));
			return;
		}
		if (!isUsefulLantern(id, rune))
		{
			out.add(new Reminder(Reminder.Kind.LANTERN, "Wrong lantern logs"));
		}
	}

	/**
	 * Logs that actually boost Arceuus RC after the Aug 2026 lantern change:
	 * willow +5% runes, blisterwood +20% bloods, magic +10% runes, redwood = oak+willow.
	 */
	private static boolean isUsefulLantern(int id, RcMode rune)
	{
		if (id == ItemID.ABYSSAL_LANTERN_MAGIC || id == ItemID.ABYSSAL_LANTERN_REDWOOD
			|| id == ItemID.ABYSSAL_LANTERN_WILLOW)
		{
			return true;
		}
		return rune == RcMode.BLOOD && id == ItemID.ABYSSAL_LANTERN_BLISTERWOOD;
	}

	/** Blood only: Blood Essence is missing, not yet activated, or running low. */
	private void addEssence(InventorySnapshot inv, RcMode rune, List<Reminder> out)
	{
		if (rune != RcMode.BLOOD || !config.bloodEssenceReminder())
		{
			return;
		}
		if (inv.isHasActiveBloodEssence())
		{
			if (bloodEssenceCharges != null && bloodEssenceCharges <= config.bloodEssenceLowCharges())
			{
				out.add(new Reminder(Reminder.Kind.ESSENCE, "Blood essence low: " + bloodEssenceCharges + " charges"));
			}
			return;
		}
		out.add(new Reminder(Reminder.Kind.ESSENCE,
			inv.isHasInactiveBloodEssence() ? "Activate your blood essence" : "Need blood essence"));
	}

	private boolean isIdle(Observation obs, Instant now)
	{
		WorldPoint tile = obs.getPosition().getTile();
		if (tile == null)
		{
			return false;
		}
		// Standing still while mining or chiselling is not idle.
		boolean busy = obs.isAnimating() || !obs.isIdlePose();
		if (busy || lastMoveAt == null || lastTile == null || lastTile.distanceTo(tile) > 0)
		{
			lastTile = tile;
			lastMoveAt = now;
			return false;
		}
		return Duration.between(lastMoveAt, now).getSeconds() >= config.idleReminderSeconds();
	}

	private void syncBloodEssenceCharges(InventorySnapshot inv)
	{
		if (!inv.isHasActiveBloodEssence())
		{
			if (!inv.isHasInactiveBloodEssence())
			{
				bloodEssenceCharges = null;
			}
			return;
		}
		if (bloodEssenceCharges != null)
		{
			return;
		}
		int stored = itemCharges.bloodEssenceCharges();
		if (stored >= 0)
		{
			bloodEssenceCharges = stored;
		}
	}

	private void setBloodEssenceCharges(int charges)
	{
		bloodEssenceCharges = Math.max(0, Math.min(MAX_BLOOD_ESSENCE_CHARGES, charges));
	}
}
