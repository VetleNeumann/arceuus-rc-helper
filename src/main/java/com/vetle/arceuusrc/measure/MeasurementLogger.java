package com.vetle.arceuusrc.measure;

import com.vetle.arceuusrc.Helper;
import com.vetle.arceuusrc.RotationStep;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Hop;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Result;
import com.vetle.arceuusrc.overlay.CameraCheckPrototype.Target;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.MenuAction;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

/**
 * THROWAWAY (wayfinder #23): logs the numbers the Sightline engine and its thresholds need, so
 * the camera model research can be confirmed or corrected from one in-game session. Every line
 * starts with {@code MEASURE <KIND>} followed by {@code key=value} pairs, so the log can be
 * grepped and split with awk. Nothing here is production code.
 *
 * <p>Line kinds:
 *
 * <ul>
 *   <li>{@code SETUP} once per login: layout, canvas, viewport, GPU, draw distance, zoom varcs,
 *       Camera plugin zoom limits.
 *   <li>{@code CAM} every game tick where the camera, scale, zoom varc or player tile changed:
 *       raw camera numbers plus the measured arm length, follow height and scale next to what
 *       the research formulas predict.
 *   <li>{@code STEP} at every Rotation Step transition, with the player tile.
 *   <li>{@code CLICK} at every scene-object click: option, object id, player tile (the Landing
 *       Tile of the Hop before), and the real clickbox bounds and area in px.
 *   <li>{@code OBJ} when a Blood target spawns: id, SW tile, size, orientation, model height.
 *   <li>{@code HEIGHTS} after every scene load: tile heights at every Landing Tile and target.
 *   <li>{@code SELFCHECK} while the player stands on a Landing Tile: predicted ghost bounds vs
 *       the real clickbox bounds, centroid delta in px and overlap ratio.
 * </ul>
 */
@Slf4j
@Singleton
public class MeasurementLogger
{
	/** Object ids of every Blood Rotation target, from docs/research/target-geometry.md. */
	private static final Set<Integer> TARGET_IDS = Set.of(
		27978, // Blood Altar
		27979, // Dark Altar
		8981, 10796, // Dense Runestones, base
		34741, // Rocks, 69 north
		27984, 27985, 27986 // Rocks, 73 west: top, bottom, middle
	);

	private static final int VARC_ZOOM_73 = 73;
	private static final int VARC_ZOOM_74 = 74;
	private static final int[] LOGIN_ZOOM_VARCS = {1338, 1339, 1340, 1341};
	private static final int CPU_DRAW_DISTANCE = 25;
	private static final double REFERENCE_VIEWPORT_HEIGHT = 334.0;

	private final Client client;
	private final ConfigManager configManager;
	private final Helper helper;
	private final CameraCheckPrototype prototype;

	private final Map<String, TileObject> targets = new HashMap<>();

	private int tick;
	/** LOGGED_IN fires before the interface loads, so SETUP waits for the first tick with a layout. */
	private boolean setupPending;
	private RotationStep lastStep = RotationStep.IDLE;
	private String lastCamKey = "";
	private WorldPoint lastSelfCheckTile;

	@Inject
	MeasurementLogger(Client client, ConfigManager configManager, Helper helper, CameraCheckPrototype prototype)
	{
		this.client = client;
		this.configManager = configManager;
		this.helper = helper;
		this.prototype = prototype;
	}

	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			targets.clear();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			setupPending = true;
			logHeights();
		}
	}

	public void onGameTick(GameTick event)
	{
		tick++;
		Player player = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (player == null || wv == null)
		{
			return;
		}
		WorldPoint tile = player.getWorldLocation();
		if (client.getScale() == 0 || client.getGameState() != GameState.LOGGED_IN)
		{
			return; // login screen or loading: the camera numbers are meaningless there
		}
		if (setupPending && client.getTopLevelInterfaceId() != -1 && client.getViewportHeight() > 0)
		{
			setupPending = false;
			logSetup();
		}

		RotationStep step = helper.getCurrentStep();
		if (step != lastStep)
		{
			log.info("MEASURE STEP tick={} from={} to={} tile={}", tick, lastStep, step, wp(tile));
			lastStep = step;
		}

		String camKey = client.getCameraX() + "," + client.getCameraY() + "," + client.getCameraZ() + ","
			+ client.getCameraPitch() + "," + client.getCameraYaw() + "," + client.getScale() + ","
			+ client.getVarcIntValue(VARC_ZOOM_74) + "," + wp(tile);
		boolean camChanged = !camKey.equals(lastCamKey);
		if (camChanged)
		{
			lastCamKey = camKey;
			log.info("MEASURE CAM tick={} {}", tick, cameraSample(player, wv));
		}

		selfCheck(tile, camChanged);
	}

	/** Camera plugin ("zoom" group) changes: relaxer and zoom limits flip mid-session. */
	public void onConfigChanged(ConfigChanged event)
	{
		if ("zoom".equals(event.getGroup()))
		{
			log.info("MEASURE CONFIG tick={} group={} key={} old={} new={}", tick, event.getGroup(),
				event.getKey(), event.getOldValue(), event.getNewValue());
		}
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		Player player = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (player == null || wv == null)
		{
			return;
		}
		WorldPoint tile = player.getWorldLocation();
		if (action == MenuAction.WALK)
		{
			log.info("MEASURE WALK tick={} tile={} step={}", tick, wp(tile), lastStep);
			return;
		}
		if (!isGameObjectAction(action))
		{
			return;
		}
		TileObject clicked = findObject(wv, event.getParam0(), event.getParam1(), event.getId());
		String box = clicked == null ? "clickbox=none" : clickbox(clicked.getClickbox());
		log.info("MEASURE CLICK tick={} option=\"{}\" target=\"{}\" id={} scene={},{} tile={} step={} {} {}",
			tick, event.getMenuOption(), event.getMenuTarget(), event.getId(),
			event.getParam0(), event.getParam1(), wp(tile), lastStep, box, cameraSample(player, wv));
	}

	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject go = event.getGameObject();
		if (!TARGET_IDS.contains(go.getId()))
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		Point min = go.getSceneMinLocation();
		WorldPoint sw = wv == null || min == null ? null : WorldPoint.fromScene(wv, min.getX(), min.getY(), go.getPlane());
		targets.put(key(go), go);
		log.info("MEASURE OBJ kind=GameObject id={} world={} sw={} size={}x{} orientation={} modelOrientation={} config={} local={},{} modelHeight={} {}",
			go.getId(), wp(go.getWorldLocation()), wp(sw), go.sizeX(), go.sizeY(), go.getOrientation(),
			go.getModelOrientation(), go.getConfig(), go.getLocalLocation().getX(), go.getLocalLocation().getY(),
			modelHeight(go.getRenderable()), clickbox(go.getClickbox()));
	}

	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		targets.remove(key(event.getGameObject()));
	}

	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		GroundObject go = event.getGroundObject();
		if (!TARGET_IDS.contains(go.getId()))
		{
			return;
		}
		targets.put(key(go), go);
		log.info("MEASURE OBJ kind=GroundObject id={} world={} config={} local={},{} modelHeight={} {}",
			go.getId(), wp(go.getWorldLocation()), go.getConfig(), go.getLocalLocation().getX(),
			go.getLocalLocation().getY(), modelHeight(go.getRenderable()), clickbox(go.getClickbox()));
	}

	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		targets.remove(key(event.getGroundObject()));
	}

	private void logSetup()
	{
		WorldView wv = client.getTopLevelWorldView();
		int drawDistance = client.isGpu() && wv != null ? wv.getScene().getDrawDistance() : CPU_DRAW_DISTANCE;
		StringBuilder varcs = new StringBuilder();
		for (int id : LOGIN_ZOOM_VARCS)
		{
			varcs.append(" varc").append(id).append('=').append(client.getVarcIntValue(id));
		}
		log.info("MEASURE SETUP layout={} canvas={}x{} real={}x{} stretched={} stretchedFast={} viewport={},{} {}x{} gpu={} drawDistance={} cameraMode={} oculus={} zoom3d={} varc73={} varc74={}{} outerLimit={} innerLimit={} relaxCameraPitch={}",
			client.getTopLevelInterfaceId(), client.getCanvasWidth(), client.getCanvasHeight(),
			client.getRealDimensions().width, client.getRealDimensions().height,
			client.isStretchedEnabled(), client.isStretchedFast(),
			client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight(),
			client.isGpu(), drawDistance, client.getCameraMode(), client.getOculusOrbState(), client.get3dZoom(),
			client.getVarcIntValue(VARC_ZOOM_73), client.getVarcIntValue(VARC_ZOOM_74), varcs,
			configManager.getConfiguration("zoom", "outerLimit"),
			configManager.getConfiguration("zoom", "innerLimit"),
			configManager.getConfiguration("zoom", "relaxCameraPitch"));
	}

	private void logHeights()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		int plane = wv.getPlane();
		StringBuilder sb = new StringBuilder();
		for (Hop hop : CameraCheckPrototype.BLOOD_HOPS)
		{
			sb.append(' ').append(hop.getName().replace(' ', '_')).append(".landing=").append(height(wv, plane, hop.getLanding()));
			int i = 0;
			for (Target target : hop.getTargets())
			{
				sb.append(' ').append(hop.getName().replace(' ', '_')).append(".target").append(i++)
					.append('=').append(height(wv, plane, target.getSw()));
			}
		}
		log.info("MEASURE HEIGHTS base={},{} plane={}{}", wv.getBaseX(), wv.getBaseY(), plane, sb);
	}

	/** Compare the prototype's ghost with the real clickbox while the player stands on a Landing Tile. */
	private void selfCheck(WorldPoint tile, boolean camChanged)
	{
		boolean onLanding = false;
		for (Hop hop : CameraCheckPrototype.BLOOD_HOPS)
		{
			if (near(hop.getLanding(), tile))
			{
				onLanding = true;
				break;
			}
		}
		if (!onLanding)
		{
			lastSelfCheckTile = null;
			return;
		}
		// One report per arrival on a Landing Tile, plus one per camera change while standing there.
		boolean fresh = !tile.equals(lastSelfCheckTile);
		lastSelfCheckTile = tile;
		if (!fresh && !camChanged)
		{
			return;
		}
		prototype.update();
		for (Result result : prototype.getResults())
		{
			Hop hop = result.getHop();
			if (!near(hop.getLanding(), tile))
			{
				continue;
			}
			for (Target target : hop.getTargets())
			{
				TileObject real = nearestTarget(target.getSw(), target.getSize());
				String realBox = real == null ? "real=none" : "realId=" + real.getId() + " " + clickbox(real.getClickbox()).replace("clickbox", "real");
				String predBox = result.getShape() == null ? "pred=none" : clickbox(result.getShape()).replace("clickbox", "pred");
				String delta = "";
				if (real != null && real.getClickbox() != null && result.getShape() != null)
				{
					Area a = new Area(result.getShape());
					Area b = new Area(real.getClickbox());
					Rectangle ra = a.getBounds();
					Rectangle rb = b.getBounds();
					double dx = ra.getCenterX() - rb.getCenterX();
					double dy = ra.getCenterY() - rb.getCenterY();
					Area inter = new Area(a);
					inter.intersect(b);
					Area union = new Area(a);
					union.add(b);
					double overlap = area(union) == 0 ? 0 : area(inter) / area(union);
					delta = String.format(Locale.ROOT, " dCentre=%.1f,%.1f dist=%.1f overlap=%.2f",
						dx, dy, Math.hypot(dx, dy), overlap);
				}
				log.info("MEASURE SELFCHECK tick={} hop=\"{}\" landing={} tile={} targetSw={} verdict={} cause={} free={}% {} {}{}",
					tick, hop.getName(), wp(hop.getLanding()), wp(tile), wp(target.getSw()), result.getVerdict(),
					result.getCause(), result.getFreePercent(), predBox, realBox, delta);
			}
		}
	}

	private String cameraSample(Player player, WorldView wv)
	{
		LocalPoint local = player.getLocalLocation();
		int plane = wv.getPlane();
		int tileHeight = Perspective.getTileHeight(client, local, plane);
		int cx = client.getCameraX();
		int cy = client.getCameraY();
		int cz = client.getCameraZ();
		float fx = client.getCameraFocalPointX();
		float fy = client.getCameraFocalPointY();
		float fz = client.getCameraFocalPointZ();
		int pitch = client.getCameraPitch();
		int varc74 = client.getVarcIntValue(VARC_ZOOM_74);
		int viewportHeight = client.getViewportHeight();

		double armMeasured = Math.sqrt((cx - fx) * (cx - fx) + (cy - fy) * (cy - fy) + (cz - fz) * (cz - fz));
		double armFlatMeasured = Math.hypot(cx - fx, cy - fy);
		// Research formula (JAU14 pitch): D = 3/8 * pitch + 600, scaled by the viewport-height factor.
		double armPredicted = 3.0 / 8.0 * pitch + 600.0;
		double heightFactor = viewportHeight / REFERENCE_VIEWPORT_HEIGHT;
		double scalePredicted = viewportHeight * Math.pow(2.0, 7.0 + varc74 / 256.0) / REFERENCE_VIEWPORT_HEIGHT;
		double followHeight = fz - tileHeight;
		double focalResidualX = fx - local.getX();
		double focalResidualY = fy - local.getY();

		return String.format(Locale.ROOT,
			"tile=%s local=%d,%d scene=%d,%d tileHeight=%d cam=%d,%d,%d camFp=%.1f,%.1f,%.1f focal=%.1f,%.1f,%.1f"
				+ " pitch=%d pitchTarget=%d yaw=%d yawTarget=%d scale=%d zoom3d=%d varc73=%d varc74=%d"
				+ " viewport=%dx%d canvas=%dx%d"
				+ " armMeasured=%.1f armFlat=%.1f armPredicted=%.1f armRatio=%.3f heightFactor=%.3f"
				+ " followHeight=%.1f scalePredicted=%.1f scaleRatio=%.3f focalResidual=%.1f,%.1f",
			wp(player.getWorldLocation()), local.getX(), local.getY(), local.getSceneX(), local.getSceneY(), tileHeight,
			cx, cy, cz, client.getCameraFpX(), client.getCameraFpY(), client.getCameraFpZ(), fx, fy, fz,
			pitch, client.getCameraPitchTarget(), client.getCameraYaw(), client.getCameraYawTarget(),
			client.getScale(), client.get3dZoom(), client.getVarcIntValue(VARC_ZOOM_73), varc74,
			client.getViewportWidth(), viewportHeight, client.getCanvasWidth(), client.getCanvasHeight(),
			armMeasured, armFlatMeasured, armPredicted, armPredicted == 0 ? 0 : armMeasured / armPredicted, heightFactor,
			followHeight, scalePredicted, scalePredicted == 0 ? 0 : client.getScale() / scalePredicted,
			focalResidualX, focalResidualY);
	}

	private TileObject findObject(WorldView wv, int sceneX, int sceneY, int id)
	{
		Tile[][][] tiles = wv.getScene().getTiles();
		int plane = wv.getPlane();
		if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length || sceneY >= tiles[plane][sceneX].length)
		{
			return null;
		}
		Tile t = tiles[plane][sceneX][sceneY];
		if (t == null)
		{
			return null;
		}
		GroundObject ground = t.getGroundObject();
		if (ground != null && ground.getId() == id)
		{
			return ground;
		}
		GameObject[] objects = t.getGameObjects();
		if (objects != null)
		{
			for (GameObject go : objects)
			{
				if (go != null && go.getId() == id)
				{
					return go;
				}
			}
		}
		return null;
	}

	/** The tracked target whose tile lies within two tiles of the footprint centre, else null. */
	private TileObject nearestTarget(WorldPoint sw, int size)
	{
		int cx = sw.getX() + size / 2;
		int cy = sw.getY() + size / 2;
		TileObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (TileObject o : targets.values())
		{
			WorldPoint w = o.getWorldLocation();
			int d = Math.max(Math.abs(w.getX() - cx), Math.abs(w.getY() - cy));
			if (d < bestDist)
			{
				bestDist = d;
				best = o;
			}
		}
		return bestDist <= Math.max(2, size) ? best : null;
	}

	private int height(WorldView wv, int plane, WorldPoint point)
	{
		LocalPoint lp = LocalPoint.fromWorld(wv, point);
		return lp == null ? Integer.MIN_VALUE : Perspective.getTileHeight(client, lp, plane);
	}

	private static boolean isGameObjectAction(MenuAction action)
	{
		return action == MenuAction.GAME_OBJECT_FIRST_OPTION
			|| action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION
			|| action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION
			|| action == MenuAction.ITEM_USE_ON_GAME_OBJECT
			|| action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
	}

	/** Within one tile (Chebyshev), same plane: Landing Tiles drift by a tile between Trips. */
	private static boolean near(WorldPoint a, WorldPoint b)
	{
		return a.getPlane() == b.getPlane()
			&& Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1;
	}

	private static String key(TileObject o)
	{
		return o.getId() + "@" + wp(o.getWorldLocation());
	}

	private static String wp(WorldPoint p)
	{
		return p == null ? "null" : p.getX() + "," + p.getY() + "," + p.getPlane();
	}

	private static int modelHeight(Renderable r)
	{
		if (r == null)
		{
			return -1;
		}
		Model m = r instanceof Model ? (Model) r : r.getModel();
		return m == null ? r.getModelHeight() : m.getModelHeight();
	}

	private static String clickbox(Shape shape)
	{
		if (shape == null)
		{
			return "clickbox=none";
		}
		Rectangle b = shape.getBounds();
		return String.format(Locale.ROOT, "clickbox=%d,%d %dx%d clickboxArea=%.0f",
			b.x, b.y, b.width, b.height, area(new Area(shape)));
	}

	/** Shoelace area over a flattened path; each closed subpath counted with its own sign. */
	private static double area(Area a)
	{
		double total = 0;
		PathIterator it = a.getPathIterator(null, 0.5);
		double[] c = new double[6];
		List<double[]> ring = new ArrayList<>();
		while (!it.isDone())
		{
			int type = it.currentSegment(c);
			if (type == PathIterator.SEG_MOVETO)
			{
				total += Math.abs(ringArea(ring));
				ring.clear();
				ring.add(new double[]{c[0], c[1]});
			}
			else if (type == PathIterator.SEG_LINETO)
			{
				ring.add(new double[]{c[0], c[1]});
			}
			else if (type == PathIterator.SEG_CLOSE)
			{
				total += Math.abs(ringArea(ring));
				ring.clear();
			}
			it.next();
		}
		total += Math.abs(ringArea(ring));
		return total;
	}

	private static double ringArea(List<double[]> ring)
	{
		double s = 0;
		for (int i = 0, n = ring.size(); i < n; i++)
		{
			double[] p = ring.get(i);
			double[] q = ring.get((i + 1) % n);
			s += p[0] * q[1] - q[0] * p[1];
		}
		return s / 2;
	}
}
