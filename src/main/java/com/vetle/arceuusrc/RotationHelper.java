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

@Singleton
public class RotationHelper
{
	private static final int FULL_FRAGMENTS = 100;

	private final ArceuusRcHelperConfig config;
	private final InventoryChecker inventoryChecker;
	private final ReminderService reminderService;
	private final SceneTracker sceneTracker;
	private final RcPathRouter pathRouter;
	private final ShortestPathBridge shortestPathBridge;

	@Getter
	private HelperAction currentAction = HelperAction.idle();

	@Getter
	private InventorySnapshot snapshot = InventorySnapshot.empty();

	@Getter
	private RcMode resolvedMode = RcMode.BLOOD;

	@Getter
	private int tripsCompleted;

	private RotationStep lastStep = RotationStep.IDLE;

	@Inject
	RotationHelper(
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

	public void reset()
	{
		currentAction = HelperAction.idle();
		tripsCompleted = 0;
		lastStep = RotationStep.IDLE;
		pathRouter.reset();
		shortestPathBridge.clear();
		inventoryChecker.reset();
	}

	public void update(Observation obs)
	{
		if (!obs.isInArceuus())
		{
			currentAction = HelperAction.idle();
			pathRouter.reset();
			shortestPathBridge.clear();
			reminderService.update(obs);
			return;
		}

		resolvedMode = obs.getRune();
		snapshot = obs.getInventory();
		reminderService.update(obs);

		if (!config.enableHelper())
		{
			currentAction = HelperAction.idle();
			pathRouter.reset();
			shortestPathBridge.clear();
			return;
		}

		RotationStep step = inferStep(obs);
		if (isTripCompleteTransition(lastStep, step))
		{
			tripsCompleted++;
		}
		lastStep = step;

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
			detailFor(step, snapshot),
			path,
			click.getObject(),
			click.getTile(),
			color);
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
				return resolvedMode == RcMode.SOUL ? ArceuusRcArea.SOUL_ALTAR : ArceuusRcArea.BLOOD_ALTAR;
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
				return resolvedMode == RcMode.SOUL ? ArceuusRcArea.SOUL_ALTAR : ArceuusRcArea.BLOOD_ALTAR;
			default:
				return null;
		}
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

	private RotationStep inferStep(Observation obs)
	{
		InventorySnapshot inv = obs.getInventory();
		Position position = obs.getPosition();
		boolean atAltar = position.isAtAltar();
		boolean nearAltar = position.isNearAltar();
		boolean atMine = position.isAtMine();
		boolean hasFrags = inv.getFragments() > 0;
		boolean hasDark = inv.getDarkBlocks() > 0;
		boolean hasDense = inv.getDenseBlocks() > 0;
		boolean inventoryFull = inv.getEmptySlots() == 0;
		boolean fullFragmentStack = inv.getFragments() >= FULL_FRAGMENTS;

		if (atAltar)
		{
			if (hasFrags)
			{
				return RotationStep.CRAFT_FRAGMENTS;
			}
			if (hasDark)
			{
				return RotationStep.CHISEL_AT_ALTAR;
			}
			return RotationStep.RETURN_TO_MINE;
		}

		if (hasFrags && hasDark && (inventoryFull || fullFragmentStack || nearAltar))
		{
			return RotationStep.GO_ALTAR;
		}
		if (hasDense && inventoryFull)
		{
			return fullFragmentStack || hasFrags ? RotationStep.GO_DARK_SECOND : RotationStep.GO_DARK_FIRST;
		}
		if (hasDark && !fullFragmentStack)
		{
			return RotationStep.CHISEL_AND_RETURN;
		}
		if (hasFrags && !hasDark && !hasDense)
		{
			return atMine ? RotationStep.MINE_SECOND : RotationStep.RETURN_TO_MINE;
		}
		if (!atMine && hasDense && !fullFragmentStack)
		{
			return RotationStep.GO_DARK_FIRST;
		}
		return RotationStep.MINE_FIRST;
	}

	private static boolean isTripCompleteTransition(RotationStep from, RotationStep to)
	{
		return (from == RotationStep.CRAFT_REMAINING || from == RotationStep.RETURN_TO_MINE)
			&& to == RotationStep.MINE_FIRST;
	}

	private String detailFor(RotationStep step, InventorySnapshot inv)
	{
		String rune = resolvedMode == RcMode.SOUL ? "soul" : "blood";
		switch (step)
		{
			case MINE_FIRST:
				return "Fill your first inventory";
			case GO_DARK_FIRST:
				return "Click the Dark Altar to venerate all dense blocks";
			case CHISEL_AND_RETURN:
				return "Use chisel on dark blocks while running back to the mine";
			case MINE_SECOND:
				return "Fill your second inventory";
			case GO_DARK_SECOND:
				return "Venerate the second inventory at the Dark Altar";
			case GO_ALTAR:
				return "Carry fragments + dark blocks to the " + rune + " altar";
			case CRAFT_FRAGMENTS:
				return "Click the " + rune + " altar to craft your fragments";
			case CHISEL_AT_ALTAR:
				return "Chisel the remaining dark blocks into fragments";
			case CRAFT_REMAINING:
				return "Click the " + rune + " altar again for the second batch";
			case RETURN_TO_MINE:
				return "Take the shortcut back to the dense essence mine";
			default:
				return step.getLabel();
		}
	}
}
