package com.vetle.arceuusrc;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import org.junit.Test;

public class RemindersTest
{
	private static final Instant T0 = Instant.parse("2026-09-06T12:00:00Z");
	private static final WorldPoint TILE = new WorldPoint(1762, 3854, 0);
	private static final WorldPoint NEXT_TILE = new WorldPoint(1763, 3854, 0);

	private final StubConfig config = new StubConfig();
	private final Reminders reminders = new Reminders(config, mock(ConfigManager.class));

	@Test
	public void fullyEquippedPlayerGetsNoReminders()
	{
		assertEquals(List.of(), texts(evaluate(ready(), RcMode.BLOOD)));
	}

	@Test
	public void missingChiselAndPickaxeAreGearReminders()
	{
		InventorySnapshot inv = gear(false, false, true, false, ItemID.ABYSSAL_LANTERN_MAGIC);
		assertEquals(List.of("Need a chisel", "Need a pickaxe"), texts(evaluate(inv, RcMode.SOUL)));
		assertEquals(Reminder.Kind.GEAR, evaluate(inv, RcMode.SOUL).get(0).getKind());
	}

	@Test
	public void lanternInBagAsksToEquipNoLanternAsksForOne()
	{
		assertEquals(List.of("Equip your lantern"),
			texts(evaluate(gear(true, true, false, true, -1), RcMode.SOUL)));
		assertEquals(List.of("Need a lantern"),
			texts(evaluate(gear(true, true, false, false, -1), RcMode.SOUL)));
	}

	@Test
	public void unlitLanternAsksToLightIt()
	{
		assertEquals(List.of("Light your lantern"),
			texts(evaluate(gear(true, true, true, false, ItemID.ABYSSAL_LANTERN), RcMode.SOUL)));
	}

	@Test
	public void blisterwoodHelpsBloodsOnly()
	{
		InventorySnapshot inv = gear(true, true, true, false, ItemID.ABYSSAL_LANTERN_BLISTERWOOD);
		assertEquals(List.of(), texts(evaluate(withActiveEssence(inv), RcMode.BLOOD)));
		assertEquals(List.of("Wrong lantern logs"), texts(evaluate(inv, RcMode.SOUL)));
	}

	@Test
	public void magicRedwoodAndWillowHelpBothRunes()
	{
		for (int id : new int[]{ItemID.ABYSSAL_LANTERN_MAGIC, ItemID.ABYSSAL_LANTERN_REDWOOD, ItemID.ABYSSAL_LANTERN_WILLOW})
		{
			assertEquals(List.of(), texts(evaluate(gear(true, true, true, false, id), RcMode.SOUL)));
		}
	}

	@Test
	public void gotrOnlyLogsDoNotHelpArceuus()
	{
		for (int id : new int[]{ItemID.ABYSSAL_LANTERN_NORMAL, ItemID.ABYSSAL_LANTERN_OAK, ItemID.ABYSSAL_LANTERN_MAPLE, ItemID.ABYSSAL_LANTERN_YEW})
		{
			assertEquals(List.of("Wrong lantern logs"), texts(evaluate(gear(true, true, true, false, id), RcMode.SOUL)));
		}
	}

	@Test
	public void lanternLogCheckOffSkipsLogWarnings()
	{
		config.lanternLogCheck = false;
		assertEquals(List.of(), texts(evaluate(gear(true, true, true, false, ItemID.ABYSSAL_LANTERN_OAK), RcMode.SOUL)));
	}

	@Test
	public void gearReminderOffSkipsGearAndLantern()
	{
		config.gearReminder = false;
		assertEquals(List.of(), texts(evaluate(gear(false, false, false, false, -1), RcMode.SOUL)));
	}

	@Test
	public void essenceReminderIsBloodOnly()
	{
		InventorySnapshot noEssence = gear(true, true, true, false, ItemID.ABYSSAL_LANTERN_MAGIC);
		assertEquals(List.of("Need blood essence"), texts(evaluate(noEssence, RcMode.BLOOD)));
		assertEquals(List.of(), texts(evaluate(noEssence, RcMode.SOUL)));
	}

	@Test
	public void inactiveEssenceAsksToActivate()
	{
		InventorySnapshot inv = new InventorySnapshot(0, 0, 0, 28, true, true, true, false, true, false, ItemID.ABYSSAL_LANTERN_MAGIC);
		assertEquals(List.of("Activate your blood essence"), texts(evaluate(inv, RcMode.BLOOD)));
	}

	@Test
	public void activeEssenceWarnsOnlyWhenChargesKnownAndLow()
	{
		assertEquals(List.of(), texts(evaluate(ready(), RcMode.BLOOD)));

		reminders.onChatMessage(chat("Your blood essence has 500 charges remaining"));
		assertEquals(List.of(), texts(evaluate(ready(), RcMode.BLOOD)));
		assertEquals(Integer.valueOf(500), reminders.bloodEssenceCharges());

		reminders.onChatMessage(chat("Your blood essence has 100 charges remaining"));
		assertEquals(List.of("Blood essence low: 100 charges"), texts(evaluate(ready(), RcMode.BLOOD)));
	}

	@Test
	public void extractingRunesCountsDownCharges()
	{
		reminders.onChatMessage(chat("You activate the blood essence."));
		reminders.onChatMessage(chat("You manage to extract power from the Blood Essence and craft 8 extra runes"));
		assertEquals(Integer.valueOf(992), reminders.bloodEssenceCharges());
	}

	@Test
	public void essenceReminderOffSaysNothing()
	{
		config.bloodEssenceReminder = false;
		assertEquals(List.of(), texts(evaluate(gear(true, true, true, false, ItemID.ABYSSAL_LANTERN_MAGIC), RcMode.BLOOD)));
	}

	@Test
	public void idleAfterStandingStillForTheConfiguredTime()
	{
		assertEquals(List.of(), texts(reminders.evaluate(obs(ready(), RcMode.BLOOD, TILE, false, true), T0)));
		assertEquals(List.of(), texts(reminders.evaluate(obs(ready(), RcMode.BLOOD, TILE, false, true), T0.plusSeconds(14))));
		List<Reminder> idle = reminders.evaluate(obs(ready(), RcMode.BLOOD, TILE, false, true), T0.plusSeconds(15));
		assertEquals(List.of("Idle"), texts(idle));
		assertEquals(Reminder.Kind.IDLE, idle.get(0).getKind());
	}

	@Test
	public void movingOrAnimatingRestartsTheIdleTimer()
	{
		reminders.evaluate(obs(ready(), RcMode.BLOOD, TILE, false, true), T0);
		reminders.evaluate(obs(ready(), RcMode.BLOOD, NEXT_TILE, false, true), T0.plusSeconds(10));
		assertEquals(List.of(), texts(reminders.evaluate(obs(ready(), RcMode.BLOOD, NEXT_TILE, false, true), T0.plusSeconds(20))));

		reminders.evaluate(obs(ready(), RcMode.BLOOD, NEXT_TILE, true, true), T0.plusSeconds(30));
		assertEquals(List.of(), texts(reminders.evaluate(obs(ready(), RcMode.BLOOD, NEXT_TILE, false, true), T0.plusSeconds(40))));
		assertEquals(List.of("Idle"), texts(reminders.evaluate(obs(ready(), RcMode.BLOOD, NEXT_TILE, false, true), T0.plusSeconds(45))));
	}

	@Test
	public void walkingPoseIsNotIdle()
	{
		reminders.evaluate(obs(ready(), RcMode.BLOOD, TILE, false, false), T0);
		assertEquals(List.of(), texts(reminders.evaluate(obs(ready(), RcMode.BLOOD, TILE, false, false), T0.plusSeconds(60))));
	}

	@Test
	public void outsideArceuusNothingApplies()
	{
		Observation away = new Observation(false, RcMode.BLOOD, Position.UNKNOWN, InventorySnapshot.empty(), 99, false, true, 0, null);
		assertEquals(List.of(), reminders.evaluate(away, T0));
	}

	@Test
	public void orderIsGearLanternEssenceIdle()
	{
		InventorySnapshot bare = gear(false, true, false, false, -1);
		reminders.evaluate(obs(bare, RcMode.BLOOD, TILE, false, true), T0);
		List<Reminder.Kind> kinds = reminders.evaluate(obs(bare, RcMode.BLOOD, TILE, false, true), T0.plusSeconds(15))
			.stream().map(Reminder::getKind).collect(Collectors.toList());
		assertEquals(List.of(Reminder.Kind.GEAR, Reminder.Kind.LANTERN, Reminder.Kind.ESSENCE, Reminder.Kind.IDLE), kinds);
		assertTrue(config.idleReminderSeconds() <= 15);
	}

	private List<Reminder> evaluate(InventorySnapshot inv, RcMode rune)
	{
		return reminders.evaluate(obs(inv, rune, TILE, false, true), T0);
	}

	private static Observation obs(InventorySnapshot inv, RcMode rune, WorldPoint tile, boolean animating, boolean idlePose)
	{
		return new Observation(true, rune, new Position(tile, true, false, false), inv, 99, animating, idlePose, 0, null);
	}

	private static List<String> texts(List<Reminder> list)
	{
		return list.stream().map(Reminder::getText).collect(Collectors.toList());
	}

	/** Chisel, pickaxe, lit magic lantern equipped and an active Blood Essence. */
	private static InventorySnapshot ready()
	{
		return withActiveEssence(gear(true, true, true, false, ItemID.ABYSSAL_LANTERN_MAGIC));
	}

	private static InventorySnapshot gear(boolean chisel, boolean pickaxe, boolean lanternEquipped, boolean lanternInBag, int lanternId)
	{
		return new InventorySnapshot(0, 0, 0, 28, chisel, pickaxe, false, false, lanternEquipped, lanternInBag, lanternId);
	}

	private static InventorySnapshot withActiveEssence(InventorySnapshot inv)
	{
		return new InventorySnapshot(inv.getDenseBlocks(), inv.getDarkBlocks(), inv.getFragments(), inv.getEmptySlots(),
			inv.isHasChisel(), inv.isHasPickaxe(), false, true,
			inv.isLanternEquipped(), inv.isLanternInInventory(), inv.getLanternItemId());
	}

	private static ChatMessage chat(String text)
	{
		return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, "", 0);
	}

	/** Config defaults, with the few gates the tests flip. */
	private static class StubConfig implements ArceuusRcHelperConfig
	{
		boolean gearReminder = true;
		boolean lanternLogCheck = true;
		boolean bloodEssenceReminder = true;

		@Override
		public boolean gearReminder()
		{
			return gearReminder;
		}

		@Override
		public boolean lanternLogCheck()
		{
			return lanternLogCheck;
		}

		@Override
		public boolean bloodEssenceReminder()
		{
			return bloodEssenceReminder;
		}
	}
}
