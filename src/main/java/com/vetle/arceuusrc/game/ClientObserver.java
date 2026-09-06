package com.vetle.arceuusrc.game;

import com.vetle.arceuusrc.ArceuusRcArea;
import com.vetle.arceuusrc.ArceuusRcHelperConfig;
import com.vetle.arceuusrc.FragmentTracker;
import com.vetle.arceuusrc.InventorySnapshot;
import com.vetle.arceuusrc.Observation;
import com.vetle.arceuusrc.Position;
import com.vetle.arceuusrc.RcMode;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * The one place that reads per-tick player state from the client. Produces an
 * {@link Observation} for the logic modules, which never see the client.
 */
@Singleton
public class ClientObserver
{
	private final Client client;
	private final ArceuusRcHelperConfig config;
	private final SceneTracker sceneTracker;
	private final InventoryReader inventoryReader;
	private final FragmentTracker fragmentTracker;

	@Inject
	public ClientObserver(
		Client client,
		ArceuusRcHelperConfig config,
		SceneTracker sceneTracker,
		InventoryReader inventoryReader,
		FragmentTracker fragmentTracker)
	{
		this.client = client;
		this.config = config;
		this.sceneTracker = sceneTracker;
		this.inventoryReader = inventoryReader;
		this.fragmentTracker = fragmentTracker;
	}

	public Observation observe()
	{
		Player player = client.getLocalPlayer();
		WorldPoint tile = player == null ? null : player.getWorldLocation();
		boolean inArceuus = ArceuusRcArea.isInArceuusRc(tile);
		RcMode rune = RcMode.resolve(config.mode(), client.getRealSkillLevel(Skill.RUNECRAFT));
		Position position = inArceuus
			? new Position(
				tile,
				sceneTracker.isAtMine(tile),
				sceneTracker.isAtAltar(tile, rune),
				sceneTracker.isNearAltar(tile, rune))
			: Position.UNKNOWN;
		InventorySnapshot inventory = inArceuus ? fragmentTracker.observe(inventoryReader.read()) : InventorySnapshot.empty();
		boolean animating = player != null && player.getAnimation() != -1;
		boolean idlePose = player == null || player.getPoseAnimation() == player.getIdlePoseAnimation();
		return new Observation(
			inArceuus,
			rune,
			position,
			inventory,
			client.getRealSkillLevel(Skill.AGILITY),
			animating,
			idlePose,
			client.getTickCount(),
			sceneTracker.isBloodAltarInScene(),
			client.getTopLevelWorldView());
	}
}
