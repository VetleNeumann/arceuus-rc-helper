package com.vetle.arceuusrc.game;

import com.vetle.arceuusrc.RawInventory;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;

/**
 * Reads the inventory and worn gear from the Client into a {@link RawInventory}. Stateless: the
 * Fragment count the game hides is resolved by {@code FragmentTracker} in the root package.
 */
@Singleton
public class InventoryReader
{
	private static final int INVENTORY_SIZE = 28;

	private final Client client;

	@Inject
	public InventoryReader(Client client)
	{
		this.client = client;
	}

	public RawInventory read()
	{
		int dense = 0;
		int dark = 0;
		int fragmentQty = 0;
		boolean hasFragmentItem = false;
		int empty = 0;
		boolean chisel = false;
		boolean pickaxe = false;
		boolean inactiveEssence = false;
		boolean activeEssence = false;
		boolean lanternInv = false;
		int lanternId = -1;

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			empty = INVENTORY_SIZE;
		}
		else
		{
			Item[] items = inventory.getItems();
			for (int i = 0; i < INVENTORY_SIZE; i++)
			{
				Item item = i < items.length ? items[i] : null;
				if (item == null || item.getId() < 0)
				{
					empty++;
					continue;
				}

				int id = item.getId();
				int qty = Math.max(0, item.getQuantity());
				if (id == ItemID.ARCEUUS_ESSENCE_BLOCK)
				{
					dense += Math.max(1, qty);
				}
				else if (id == ItemID.ARCEUUS_ESSENCE_BLOCK_DARK)
				{
					dark += Math.max(1, qty);
				}
				else if (id == ItemID.BIGBLANKRUNE)
				{
					hasFragmentItem = true;
					fragmentQty += qty;
				}
				else if (isChisel(id))
				{
					chisel = true;
				}
				else if (id == ItemID.BLOOD_ESSENCE_INACTIVE)
				{
					inactiveEssence = true;
				}
				else if (id == ItemID.BLOOD_ESSENCE_ACTIVE)
				{
					activeEssence = true;
				}
				else if (isAbyssalLantern(id))
				{
					lanternInv = true;
					lanternId = id;
				}
				else if (isPickaxe(id))
				{
					pickaxe = true;
				}
			}
		}

		int widgetQty = fragmentQuantityFromWidget();

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			Item shield = equipment.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx());
			if (shield != null && isAbyssalLantern(shield.getId()))
			{
				lanternId = shield.getId();
			}
			Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
			if (weapon != null && isPickaxe(weapon.getId()))
			{
				pickaxe = true;
			}
		}

		boolean lanternEquipped = lanternId != -1 && equipmentHasLantern(equipment);

		return new RawInventory(
			dense,
			dark,
			hasFragmentItem,
			fragmentQty,
			widgetQty,
			empty,
			chisel,
			pickaxe,
			inactiveEssence,
			activeEssence,
			lanternEquipped,
			lanternInv,
			lanternId);
	}

	private int fragmentQuantityFromWidget()
	{
		Widget container = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (container == null)
		{
			return 0;
		}
		Widget[] children = container.getDynamicChildren();
		if (children == null)
		{
			return 0;
		}
		int total = 0;
		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			if (child != null && child.getItemId() == ItemID.BIGBLANKRUNE)
			{
				total += Math.max(0, child.getItemQuantity());
			}
		}
		return total;
	}

	private boolean equipmentHasLantern(ItemContainer equipment)
	{
		if (equipment == null)
		{
			return false;
		}
		Item shield = equipment.getItem(EquipmentInventorySlot.SHIELD.getSlotIdx());
		return shield != null && isAbyssalLantern(shield.getId());
	}

	static boolean isChisel(int id)
	{
		return id == ItemID.CHISEL || id == ItemID.JEWELLERS_CHISEL;
	}

	static boolean isAbyssalLantern(int id)
	{
		return id == ItemID.ABYSSAL_LANTERN
			|| id == ItemID.ABYSSAL_LANTERN_NORMAL
			|| id == ItemID.ABYSSAL_LANTERN_NORMAL_BLUE
			|| id == ItemID.ABYSSAL_LANTERN_NORMAL_RED
			|| id == ItemID.ABYSSAL_LANTERN_NORMAL_WHITE
			|| id == ItemID.ABYSSAL_LANTERN_NORMAL_PURPLE
			|| id == ItemID.ABYSSAL_LANTERN_NORMAL_GREEN
			|| id == ItemID.ABYSSAL_LANTERN_OAK
			|| id == ItemID.ABYSSAL_LANTERN_WILLOW
			|| id == ItemID.ABYSSAL_LANTERN_MAPLE
			|| id == ItemID.ABYSSAL_LANTERN_YEW
			|| id == ItemID.ABYSSAL_LANTERN_BLISTERWOOD
			|| id == ItemID.ABYSSAL_LANTERN_MAGIC
			|| id == ItemID.ABYSSAL_LANTERN_REDWOOD;
	}

	private boolean isPickaxe(int id)
	{
		ItemComposition def = client.getItemDefinition(id);
		if (def == null || def.getName() == null)
		{
			return false;
		}
		String name = def.getName().toLowerCase(Locale.ROOT);
		return name.contains("pickaxe") || name.contains("pick axe");
	}
}
