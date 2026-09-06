package com.vetle.arceuusrc;

import com.vetle.arceuusrc.game.InventoryChecker;
import com.vetle.arceuusrc.game.SceneTracker;
import com.vetle.arceuusrc.game.ShortestPathBridge;
import java.awt.Color;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/**
 * The guidance layer. Each tick it feeds the Observation through the Rotation, asks the path
 * router for the Path and assembles the Next Action the overlays draw. Switching the Helper off
 * suppresses the Next Action only; the Rotation keeps advancing and Reminders keep running.
 */
@Singleton
public class Helper
{
	private final ArceuusRcHelperConfig config;
	private final InventoryChecker inventoryChecker;
	private final ReminderService reminderService;
	private final SceneTracker sceneTracker;
	private final RcPathRouter pathRouter;
	private final ShortestPathBridge shortestPathBridge;
	private final Rotation rotation = new Rotation();

	@Getter
	private HelperAction currentAction = HelperAction.idle();

	@Getter
	private InventorySnapshot snapshot = InventorySnapshot.empty();

	@Getter
	private RcMode resolvedMode = RcMode.BLOOD;

	@Inject
	Helper(
		ArceuusRcHelperConfig config,
		InventoryChecker inventoryChecker,
		ReminderService reminderService,
		SceneTracker sceneTracker,
		RcPathRouter pathRouter,
		ShortestPathBridge shortestPathBridge)
	{
		this.config = config;
		this.inventoryChecker = inventoryChecker;
		this.reminderService = reminderService;
		this.sceneTracker = sceneTracker;
		this.pathRouter = pathRouter;
		this.shortestPathBridge = shortestPathBridge;
	}

	public int getTripsCompleted()
	{
		return rotation.tripsCompleted();
	}

	public void reset()
	{
		currentAction = HelperAction.idle();
		rotation.reset();
		pathRouter.reset();
		shortestPathBridge.clear();
		inventoryChecker.reset();
	}

	public void update(Observation obs)
	{
		if (!obs.isInArceuus())
		{
			clearAction();
			reminderService.update(obs);
			return;
		}

		resolvedMode = obs.getRune();
		snapshot = obs.getInventory();
		reminderService.update(obs);
		RotationStep step = rotation.advance(obs);

		if (!config.enableHelper())
		{
			clearAction();
			return;
		}

		Position position = obs.getPosition();
		WorldPoint start = position.getTile();
		boolean atMine = position.isAtMine();
		TileObject destination = destinationObject(step, obs);
		WorldPoint end = pathEnd(destination, step, atMine);
		Color color = colorFor(step);
		boolean ownPath = !config.pathDisplay().isOff() && !shortestPathBridge.isDriving();
		List<WorldPoint> path = pathRouter.pathTo(
			obs.getWorldView(),
			start,
			end,
			step,
			obs.getAgility(),
			resolvedMode,
			atMine,
			ownPath);
		RcPathRouter.ClickTarget click = pathRouter.nextClick(step, destination, path, start, atMine);
		shortestPathBridge.update(start, shortestPathTarget(end, step), color, obs.getTick());
		currentAction = new HelperAction(
			step,
			step.detail(resolvedMode),
			path,
			click.getObject(),
			click.getTile(),
			color);
	}

	private void clearAction()
	{
		currentAction = HelperAction.idle();
		pathRouter.reset();
		shortestPathBridge.clear();
	}

	/**
	 * Shortest Path targets must be walkable. Object SW tiles (runestones, altars) are often
	 * collision-blocked, which makes SP report "Destination could not be reached".
	 */
	private WorldPoint shortestPathTarget(WorldPoint end, RotationStep step)
	{
		if (end == null)
		{
			return null;
		}
		switch (step)
		{
			case MINE_FIRST:
			case MINE_SECOND:
			case CHISEL_AND_RETURN:
			case RETURN_TO_MINE:
				return ArceuusRcArea.MINE_STAND;
			case GO_DARK_FIRST:
			case GO_DARK_SECOND:
				return ArceuusRcArea.DARK_ALTAR;
			case GO_ALTAR:
			case CRAFT_FRAGMENTS:
			case CRAFT_REMAINING:
			case CHISEL_AT_ALTAR:
				return craftAltar();
			default:
				return end;
		}
	}

	private TileObject destinationObject(RotationStep step, Observation obs)
	{
		WorldPoint tile = obs.getPosition().getTile();
		switch (step)
		{
			case MINE_FIRST:
			case MINE_SECOND:
				return sceneTracker.chooseRunestone(tile, obs.isAnimating());
			case GO_DARK_FIRST:
			case GO_DARK_SECOND:
				return sceneTracker.getDarkAltar();
			case GO_ALTAR:
			case CRAFT_FRAGMENTS:
			case CRAFT_REMAINING:
				return sceneTracker.altarFor(resolvedMode);
			case CHISEL_AT_ALTAR:
				return null;
			case CHISEL_AND_RETURN:
			case RETURN_TO_MINE:
				if (obs.getPosition().isAtMine() && step == RotationStep.CHISEL_AND_RETURN)
				{
					return null;
				}
				return sceneTracker.chooseRunestone(tile, obs.isAnimating());
			default:
				return null;
		}
	}

	private WorldPoint pathEnd(TileObject destination, RotationStep step, boolean atMine)
	{
		if (destination != null)
		{
			return destination.getWorldLocation();
		}
		if (atMine && (step == RotationStep.MINE_FIRST || step == RotationStep.MINE_SECOND
			|| step == RotationStep.CHISEL_AND_RETURN || step == RotationStep.RETURN_TO_MINE))
		{
			return null;
		}
		return fallbackTile(step);
	}

	private WorldPoint fallbackTile(RotationStep step)
	{
		switch (step)
		{
			case MINE_FIRST:
			case MINE_SECOND:
			case CHISEL_AND_RETURN:
			case RETURN_TO_MINE:
				return ArceuusRcArea.MINE_STAND;
			case GO_DARK_FIRST:
			case GO_DARK_SECOND:
				return ArceuusRcArea.DARK_ALTAR;
			case GO_ALTAR:
			case CRAFT_FRAGMENTS:
			case CRAFT_REMAINING:
			case CHISEL_AT_ALTAR:
				return craftAltar();
			default:
				return null;
		}
	}

	private WorldPoint craftAltar()
	{
		return resolvedMode == RcMode.SOUL ? ArceuusRcArea.SOUL_ALTAR : ArceuusRcArea.BLOOD_ALTAR;
	}

	private Color colorFor(RotationStep step)
	{
		switch (step)
		{
			case GO_ALTAR:
			case CRAFT_FRAGMENTS:
			case CRAFT_REMAINING:
				return resolvedMode.getColor();
			default:
				return step.getColor();
		}
	}
}
