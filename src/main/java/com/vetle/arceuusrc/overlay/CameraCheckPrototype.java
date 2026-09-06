package com.vetle.arceuusrc.overlay;

import com.vetle.arceuusrc.ArceuusRcHelperConfig;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

/**
 * PROTOTYPE, THROWAWAY (wayfinder #22): judges every Hop of the Blood Rotation from a guessed
 * Landing Tile under the live Camera Pose, using footprint tile polygons as stand-in geometry and
 * the shift trick (translate the target by player - Landing Tile, then project with the real
 * camera). Nothing here is production code; Landing Tiles are guesses until #23 measures them.
 * Everything runs on the client thread from overlay render().
 */
@Singleton
public class CameraCheckPrototype
{
	public enum GhostStyle
	{
		NONE, OUTLINE, FILLED, LABELLED
	}

	public enum PanelStyle
	{
		STATUS_ROWS, SEPARATE, SUMMARY_ONLY
	}

	public enum CauseWords
	{
		LONG, SHORT
	}

	public enum SummaryStyle
	{
		WORST, HIDDEN_ONLY
	}

	public enum UnknownStyle
	{
		QUESTION, TEXT, HIDE
	}

	public enum Verdict
	{
		CLEAR("Clear", new Color(120, 220, 120)),
		OBSTRUCTED("Obstructed", new Color(255, 168, 76)),
		HIDDEN("Hidden", new Color(255, 90, 90)),
		UNKNOWN("Unknown", new Color(160, 160, 160));

		@Getter
		private final String label;
		@Getter
		private final Color color;

		Verdict(String label, Color color)
		{
			this.label = label;
			this.color = color;
		}
	}

	public enum Cause
	{
		NONE("", ""),
		OFFSCREEN("off screen", "off"),
		CHAT("under chat", "chat"),
		SIDE("under side panel", "side"),
		MAP("under minimap", "map"),
		HUD("under HUD", "HUD"),
		FAR("beyond draw distance", "far"),
		BEHIND("behind camera", "behind");

		private final String longWord;
		private final String shortWord;

		Cause(String longWord, String shortWord)
		{
			this.longWord = longWord;
			this.shortWord = shortWord;
		}

		public String word(CauseWords style)
		{
			return style == CauseWords.SHORT ? shortWord : longWord;
		}
	}

	/** One Blood Hop: a scene-object click judged from the Landing Tile of the click before. */
	@Getter
	public static final class Hop
	{
		private final String name;
		private final WorldPoint landing;
		/** Candidate targets; the Hop takes the best one (the runestone Hop has two rocks). */
		private final List<Footprint> targets;
		private final boolean shortcut;

		Hop(String name, WorldPoint landing, boolean shortcut, Footprint... targets)
		{
			this.name = name;
			this.landing = landing;
			this.shortcut = shortcut;
			this.targets = List.of(targets);
		}
	}

	@Getter
	public static final class Footprint
	{
		private final WorldPoint sw;
		private final int size;

		Footprint(WorldPoint sw, int size)
		{
			this.sw = sw;
			this.size = size;
		}
	}

	@Getter
	public static final class Result
	{
		private final Hop hop;
		private final Verdict verdict;
		private final Cause cause;
		private final int freePercent;
		/** Predicted on-screen shape (unclipped), null when nothing projects. */
		private final Area shape;

		Result(Hop hop, Verdict verdict, Cause cause, int freePercent, Area shape)
		{
			this.hop = hop;
			this.verdict = verdict;
			this.cause = cause;
			this.freePercent = freePercent;
			this.shape = shape;
		}
	}

	// Landing Tile guesses. #23 measures the real ones.
	static final List<Hop> BLOOD_HOPS = List.of(
		new Hop("Mine", new WorldPoint(1762, 3854, 0), false,
			new Footprint(new WorldPoint(1762, 3856, 0), 5),
			new Footprint(new WorldPoint(1762, 3844, 0), 5)),
		new Hop("Scramble N", new WorldPoint(1761, 3872, 0), true,
			new Footprint(new WorldPoint(1761, 3873, 0), 1)),
		new Hop("Venerate", new WorldPoint(1718, 3878, 0), false,
			new Footprint(new WorldPoint(1715, 3882, 0), 3)),
		new Hop("Scramble S", new WorldPoint(1761, 3874, 0), true,
			new Footprint(new WorldPoint(1761, 3873, 0), 1)),
		new Hop("Far Bind", new WorldPoint(1735, 3828, 0), false,
			new Footprint(new WorldPoint(1716, 3829, 0), 4)),
		new Hop("Craft", new WorldPoint(1717, 3828, 0), false,
			new Footprint(new WorldPoint(1716, 3829, 0), 4)),
		new Hop("Scramble W", new WorldPoint(1742, 3854, 0), true,
			new Footprint(new WorldPoint(1743, 3854, 0), 1))
	);

	private static final double CLEAR_FREE = 0.90;
	private static final double HIDDEN_FREE = 0.15;
	private static final double PIXEL_FLOOR = 20 * 20;
	private static final int CPU_DRAW_DISTANCE = 25;

	private static final int RESIZABLE_CLASSIC = 161;
	private static final int RESIZABLE_MODERN = 164;
	private static final int CHATAREA = 0x00a2_0022;
	private static final int[] IN_VIEWPORT_HUD = {
		0x0224_0022, 0x0224_001c, 0x0224_0020, 0x0224_001f, 0x0224_001d, 0x0224_0025,
		0x0224_0024, 0x0224_002c, 0x0224_001e, 0x0224_0028, 0x0224_002b, 0x0224_0029,
	};

	private final Client client;
	private final ArceuusRcHelperConfig config;

	@Getter
	private List<Result> results = Collections.emptyList();

	@Inject
	CameraCheckPrototype(Client client, ArceuusRcHelperConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public boolean enabled()
	{
		return config.protoCameraCheck();
	}

	/** Recompute every Hop for the current frame. Client thread only. */
	public void update()
	{
		Player player = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (player == null || wv == null)
		{
			results = Collections.emptyList();
			return;
		}
		WorldPoint playerTile = player.getWorldLocation();
		LocalPoint playerLocal = LocalPoint.fromWorld(wv, playerTile);
		if (playerLocal == null)
		{
			results = Collections.emptyList();
			return;
		}
		int plane = wv.getPlane();
		int playerHeight = Perspective.getTileHeight(client, playerLocal, plane);
		Rectangle viewport = new Rectangle(
			client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight());
		Map<Cause, Area> cover = hudCover(viewport);
		int camTileX = (client.getCameraX() >> 7) - playerLocal.getSceneX();
		int camTileY = (client.getCameraY() >> 7) - playerLocal.getSceneY();
		int drawDistance = client.isGpu() ? wv.getScene().getDrawDistance() : CPU_DRAW_DISTANCE;

		List<Result> out = new ArrayList<>();
		for (Hop hop : BLOOD_HOPS)
		{
			if (hop.shortcut && config.protoSimulateUnknown())
			{
				out.add(new Result(hop, Verdict.UNKNOWN, Cause.NONE, 0, null));
				continue;
			}
			Result best = null;
			for (Footprint target : hop.targets)
			{
				Result r = judge(hop, target, wv, plane, playerTile, playerLocal, playerHeight,
					viewport, cover, camTileX, camTileY, drawDistance);
				if (best == null || r.freePercent > best.freePercent)
				{
					best = r;
				}
			}
			out.add(best);
		}
		results = out;
	}

	private Result judge(
		Hop hop, Footprint target, WorldView wv, int plane,
		WorldPoint playerTile, LocalPoint playerLocal, int playerHeight,
		Rectangle viewport, Map<Cause, Area> cover,
		int camTileX, int camTileY, int drawDistance)
	{
		int shiftX = playerTile.getX() - hop.landing.getX();
		int shiftY = playerTile.getY() - hop.landing.getY();

		// "Not drawn": the target tile sits outside the square of half-width D around the camera tile.
		int camWorldX = hop.landing.getX() + camTileX;
		int camWorldY = hop.landing.getY() + camTileY;
		int centreX = target.sw.getX() + target.size / 2;
		int centreY = target.sw.getY() + target.size / 2;
		if (Math.abs(centreX - camWorldX) >= drawDistance || Math.abs(centreY - camWorldY) >= drawDistance)
		{
			return new Result(hop, Verdict.HIDDEN, Cause.FAR, 0, null);
		}

		// Height: keep the target's rise over its Landing Tile when both are in scene, else flat.
		int z = playerHeight + heightDelta(wv, plane, target, hop.landing);

		Area shape = new Area();
		for (int dx = 0; dx < target.size; dx++)
		{
			for (int dy = 0; dy < target.size; dy++)
			{
				WorldPoint tile = new WorldPoint(target.sw.getX() + dx + shiftX, target.sw.getY() + dy + shiftY, plane);
				Polygon poly = tilePoly(playerLocal, playerTile, tile, z);
				if (poly != null)
				{
					shape.add(new Area(poly));
				}
			}
		}
		double total = area(shape);
		if (total <= 0)
		{
			return new Result(hop, Verdict.HIDDEN, Cause.BEHIND, 0, null);
		}

		Area free = new Area(shape);
		free.intersect(new Area(viewport));
		double offscreen = total - area(free);
		Map<Cause, Double> lost = new EnumMap<>(Cause.class);
		lost.put(Cause.OFFSCREEN, offscreen);
		for (Map.Entry<Cause, Area> e : cover.entrySet())
		{
			double before = area(free);
			free.subtract(e.getValue());
			lost.put(e.getKey(), before - area(free));
		}
		double freeArea = area(free);
		double ratio = freeArea / total;
		int percent = (int) Math.round(ratio * 100);

		Verdict verdict;
		if (ratio >= CLEAR_FREE)
		{
			verdict = Verdict.CLEAR;
		}
		else if (ratio < HIDDEN_FREE || freeArea < PIXEL_FLOOR)
		{
			verdict = Verdict.HIDDEN;
		}
		else
		{
			verdict = Verdict.OBSTRUCTED;
		}
		Cause cause = Cause.NONE;
		if (verdict != Verdict.CLEAR)
		{
			double worst = 0;
			for (Map.Entry<Cause, Double> e : lost.entrySet())
			{
				if (e.getValue() > worst)
				{
					worst = e.getValue();
					cause = e.getKey();
				}
			}
		}
		return new Result(hop, verdict, cause, percent, shape);
	}

	private int heightDelta(WorldView wv, int plane, Footprint target, WorldPoint landing)
	{
		LocalPoint t = LocalPoint.fromWorld(wv, target.sw);
		LocalPoint l = LocalPoint.fromWorld(wv, landing);
		if (t == null || l == null)
		{
			return 0;
		}
		return Perspective.getTileHeight(client, t, plane) - Perspective.getTileHeight(client, l, plane);
	}

	/** Polygon of a tile that may lie outside the scene, built from local offsets off the player. */
	private Polygon tilePoly(LocalPoint playerLocal, WorldPoint playerTile, WorldPoint tile, int z)
	{
		int cx = playerLocal.getX() + (tile.getX() - playerTile.getX()) * Perspective.LOCAL_TILE_SIZE;
		int cy = playerLocal.getY() + (tile.getY() - playerTile.getY()) * Perspective.LOCAL_TILE_SIZE;
		int h = Perspective.LOCAL_HALF_TILE_SIZE;
		Point a = Perspective.localToCanvas(client, cx - h, cy - h, z);
		Point b = Perspective.localToCanvas(client, cx + h, cy - h, z);
		Point c = Perspective.localToCanvas(client, cx + h, cy + h, z);
		Point d = Perspective.localToCanvas(client, cx - h, cy + h, z);
		if (a == null || b == null || c == null || d == null)
		{
			return null;
		}
		Polygon poly = new Polygon();
		poly.addPoint(a.getX(), a.getY());
		poly.addPoint(b.getX(), b.getY());
		poly.addPoint(c.getX(), c.getY());
		poly.addPoint(d.getX(), d.getY());
		return poly;
	}

	/** Cover rects by cause for the current layout; widgets read live on the client thread. */
	private Map<Cause, Area> hudCover(Rectangle viewport)
	{
		Map<Cause, Area> cover = new EnumMap<>(Cause.class);
		int layout = client.getTopLevelInterfaceId();
		if (layout == RESIZABLE_CLASSIC)
		{
			cover.put(Cause.CHAT, chatCover(0x00a1_0060));
			cover.put(Cause.SIDE, widgetArea(0x00a1_0061));
			cover.put(Cause.MAP, widgetArea(0x00a1_005f));
		}
		else if (layout == RESIZABLE_MODERN)
		{
			cover.put(Cause.CHAT, chatCover(0x00a4_005d));
			Area side = widgetArea(0x00a4_005e);
			side.add(widgetArea(0x00a4_005f));
			side.add(widgetArea(0x00a4_0060));
			cover.put(Cause.SIDE, side);
			cover.put(Cause.MAP, widgetArea(0x00a4_005c));
		}
		Area hud = new Area();
		double viewportArea = viewport.getWidth() * viewport.getHeight();
		for (int id : IN_VIEWPORT_HUD)
		{
			Rectangle r = widgetBounds(id);
			// A container spanning most of the viewport is a layer, not cover.
			if (r != null && r.getWidth() * r.getHeight() < viewportArea * 0.5)
			{
				hud.add(new Area(r));
			}
		}
		cover.put(Cause.HUD, hud);
		return cover;
	}

	private Area chatCover(int containerId)
	{
		Area chat = widgetArea(containerId);
		Widget chatArea = client.getWidget(CHATAREA);
		if (chatArea != null && chatArea.isSelfHidden())
		{
			chat.subtract(new Area(chatArea.getBounds()));
		}
		return chat;
	}

	private Area widgetArea(int id)
	{
		Rectangle r = widgetBounds(id);
		return r == null ? new Area() : new Area(r);
	}

	private Rectangle widgetBounds(int id)
	{
		Widget w = client.getWidget(id);
		if (w == null || w.isHidden())
		{
			return null;
		}
		return w.getBounds();
	}

	/** Shoelace over the flattened outline; holes come out negative, so this is signed-correct for Area. */
	static double area(Area a)
	{
		if (a == null || a.isEmpty())
		{
			return 0;
		}
		double sum = 0;
		double[] c = new double[6];
		double startX = 0;
		double startY = 0;
		double prevX = 0;
		double prevY = 0;
		for (PathIterator it = a.getPathIterator(null, 0.5); !it.isDone(); it.next())
		{
			int type = it.currentSegment(c);
			if (type == PathIterator.SEG_MOVETO)
			{
				startX = c[0];
				startY = c[1];
				prevX = c[0];
				prevY = c[1];
			}
			else if (type == PathIterator.SEG_LINETO)
			{
				sum += prevX * c[1] - c[0] * prevY;
				prevX = c[0];
				prevY = c[1];
			}
			else if (type == PathIterator.SEG_CLOSE)
			{
				sum += prevX * startY - startX * prevY;
				prevX = startX;
				prevY = startY;
			}
		}
		return Math.abs(sum) / 2;
	}

	/** Summary row text: what the Status Panel shows during play. */
	public String summary(SummaryStyle style)
	{
		int hidden = 0;
		int obstructed = 0;
		int unknown = 0;
		for (Result r : results)
		{
			switch (r.verdict)
			{
				case HIDDEN:
					hidden++;
					break;
				case OBSTRUCTED:
					obstructed++;
					break;
				case UNKNOWN:
					unknown++;
					break;
				default:
					break;
			}
		}
		if (hidden > 0)
		{
			return hidden + " Hidden";
		}
		if (obstructed > 0 && style == SummaryStyle.WORST)
		{
			return obstructed + " Obstructed";
		}
		if (unknown > 0 && style == SummaryStyle.WORST)
		{
			return unknown + " Unknown";
		}
		return "OK";
	}

	public Color summaryColor(SummaryStyle style)
	{
		boolean obstructed = false;
		boolean unknown = false;
		for (Result r : results)
		{
			if (r.verdict == Verdict.HIDDEN)
			{
				return Verdict.HIDDEN.color;
			}
			obstructed |= r.verdict == Verdict.OBSTRUCTED;
			unknown |= r.verdict == Verdict.UNKNOWN;
		}
		if (style == SummaryStyle.WORST && obstructed)
		{
			return Verdict.OBSTRUCTED.color;
		}
		if (style == SummaryStyle.WORST && unknown)
		{
			return Verdict.UNKNOWN.color;
		}
		return Verdict.CLEAR.color;
	}
}
