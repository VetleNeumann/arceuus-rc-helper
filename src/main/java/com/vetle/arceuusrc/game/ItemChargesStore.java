package com.vetle.arceuusrc.game;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the Blood Essence charge count that RuneLite's Item Charges plugin keeps on the RS
 * profile, so a fresh login can start from a known count before any chat message arrives.
 */
@Singleton
public class ItemChargesStore
{
	private static final String GROUP = "itemCharge";
	private static final String BLOOD_ESSENCE = "bloodEssence";

	private final ConfigManager configManager;

	@Inject
	public ItemChargesStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Stored Blood Essence charges, or -1 when the Item Charges plugin has none recorded. */
	public int bloodEssenceCharges()
	{
		try
		{
			Integer stored = configManager.getRSProfileConfiguration(GROUP, BLOOD_ESSENCE, Integer.class);
			return stored != null ? stored : -1;
		}
		catch (Exception ex)
		{
			return -1;
		}
	}
}
