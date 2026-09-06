package com.vetle.arceuusrc;

import com.vetle.arceuusrc.game.SceneTracker;
import com.vetle.arceuusrc.game.ShortestPathBridge;
import java.awt.Color;
import java.time.Instant;
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
	private final Reminders reminders;
	private final SceneTracker sceneTracker;
	private final RcPathRouter pathRouter;
	private final ShortestPathBridge shortestPathBridge;
	private final Rotation rotation = new Rotation();

	/** What the overlays draw this tick. */
	@Getter
	private Guidance guidance = Guidance.none();

	private NextAction currentAction = NextAction.idle();

	@Getter
	private InventorySnapshot snapshot = InventorySnapshot.empty();

	@Getter
	private RcMode resolvedMode = RcMode.BLOOD;

	private List<Reminder> activeReminders = List.of();

	@Inject
	Helper(
		ArceuusRcHelperConfig config,
		Reminders reminders,
		SceneTracker sceneTracker,
		RcPathRouter pathRouter,
		ShortestPathBridge shortestPathBridge)
	{
		this.config = config;
		this.reminders = reminders;
		this.sceneTracker = sceneTracker;
		this.pathRouter = pathRouter;
		this.shortestPathBridge = shortestPathBridge;
	}

	public int getTripsCompleted()
	{
		return rotation.tripsCompleted();
	}

	public Integer getBloodEssenceCharges()
	{
		return reminders.bloodEssenceCharges();
	}

	public void reset()
	{
		guidance = Guidance.none();
		currentAction = NextAction.idle();
		activeReminders = List.of();
		rotation.reset();
		pathRouter.reset();
		shortestPathBridge.clear();
	}

	public void update(Observation obs)
	{
		activeReminders = reminders.evaluate(obs, Instant.now());
		if (obs.isInArceuus())
		{
			resolvedMode = obs.getRune();
			snapshot = obs.getInventory();
			RotationStep step = rotation.advance(obs);
			if (config.enableHelper())
			{
				currentAction = nextAction(obs, step);
			}
			else
			{
				clearAction();
			}
		}
		else
		{
			clearAction();
		}
		guidance = Guidance.decide(config, currentAction, activeReminders);
	}

	private NextAction nextAction(Observation obs, RotationStep step)
	{

		Position position = obs.getPosition();
		WorldPoint start = position.getTile();
		boolean atMine = position.isAtMine();
		TileObject destination = destinationObject(step, obs);
		WorldPoint end = pathEnd(destination, step, atMine);
		Color color = colorFor(step);
		List<WorldPoint> path = resolvePath(obs, start, end, step, color);
		RcPathRouter.ClickTarget click = pathRouter.nextClick(step, destination, path, start, atMine);
		return new NextAction(
			step,
			step.detail(resolvedMode),
			path,
			click.getObject(),
			click.getTile(),
			color);
	}

	/**
	 * The Path Source choice, made once. Plugin Lines returns the tiles this plugin draws;
	 * Shortest Path is handed the target and draws its own line, in which case the tiles come
	 * back empty. Two concrete adapters rather than one interface: ADR-0007.
	 */
	private List<WorldPoint> resolvePath(Observation obs, WorldPoint start, WorldPoint end, RotationStep step, Color color)
	{
		boolean ownPath = !config.pathDisplay().isOff() && !shortestPathBridge.isDriving();
		List<WorldPoint> path = pathRouter.pathTo(
			obs.getWorldView(),
			start,
			end,
			step,
			obs.getAgility(),
			resolvedMode,
			obs.getPosition().isAtMine(),
			ownPath);
		shortestPathBridge.update(start, shortestPathTarget(end, step), color, obs.getTick());
		return path;
	}

	private void clearAction()
	{
		currentAction = NextAction.idle();
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
		WorldPoint stand = standTile(step);
		return stand != null ? stand : end;
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
		return standTile(step);
	}

	/** The walkable tile to head for on a Step, when no scene object is known for it. */
	private WorldPoint standTile(RotationStep step)
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
