package dev.remnants.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.remnants.RemnantsMod;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

/**
 * Keyframe animations authored in Blockbench space (assets/remnants_of_the_past/animations/*.json)
 * and played on vanilla {@link ModelPart}s. Sampling is Blockbench's own (Blockbench 5.2 GeneralAnimator.interpolate):
 * step / linear / catmull-rom / Bezier between keyframes, catmull-rom neighbours wrapping round in looping clips;
 * rotations are added to the rest pose, positions offset the bone, scales multiply it. Reloads with F3+T.
 *
 * <p>Format {@code remnants_of_the_past:keyframes/2} (a superset of /1): a key is {@code [t, x, y, z, interp]}, a Bezier key adds its
 * handles {@code [.., "bezier", ltx, lty, ltz, lvx, lvy, lvz, rtx, rty, rtz, rvx, rvy, rvz]}; channels rotation, position,
 * scale; a clip may carry {@code "events": [[t, "name", strength], ...]} (glint, impact, step, ...) that the models read
 * with {@link #events} to fire cosmetic effects on the frame they belong to.
 */
public final class BoneAnimations {
	private static final Map<ResourceLocation, BoneAnimations> LOADED = new HashMap<>();
	private static int generation;
	private static final byte STEP = 0, LINEAR = 1, CATMULL = 2, BEZIER = 3;
	private static final int ROT = 0, POS = 1, SCALE = 2;

	private final Map<String, String> parents;
	private final Map<String, Clip> clips;
	public final int version;

	private BoneAnimations(Map<String, String> parents, Map<String, Clip> clips, int version) {
		this.parents = parents;
		this.clips = clips;
		this.version = version;
	}

	public static BoneAnimations get(ResourceLocation id) {
		BoneAnimations set = LOADED.get(id);
		return set != null ? set : new BoneAnimations(Map.of(), Map.of(), -1);
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return RemnantsMod.id("bone_animations");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				LOADED.clear();
				generation++;
				manager.listResources("animations", path -> path.getPath().endsWith(".json")).forEach((file, res) -> {
					String p = file.getPath();
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), p.substring("animations/".length(), p.length() - 5));
					try {
						LOADED.put(id, parse(res));
					} catch (Exception ex) {
						RemnantsMod.LOGGER.error("Bad bone animation file {}", file, ex);
					}
				});
			}
		});
	}

	private static BoneAnimations parse(Resource res) throws Exception {
		try (Reader r = res.openAsReader()) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			Map<String, String> parents = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("bones").entrySet()) {
				parents.put(e.getKey(), e.getValue().isJsonNull() ? null : e.getValue().getAsString());
			}
			Map<String, Clip> clips = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
				clips.put(e.getKey(), Clip.parse(e.getValue().getAsJsonObject()));
			}
			return new BoneAnimations(parents, clips, generation);
		}
	}

	public boolean has(String clip) {
		return this.clips.containsKey(clip);
	}

	public float length(String clip) {
		Clip c = this.clips.get(clip);
		return c == null ? 0 : c.length;
	}

	/** Adds clip at time {@code seconds} with {@code weight} into {@code pose}. */
	public void add(String clip, float seconds, float weight, Pose pose) {
		Clip c = this.clips.get(clip);
		if (c == null || weight <= 0.001F) {
			return;
		}
		float t = c.loop && c.length > 0 ? seconds % c.length : seconds;
		for (Track tr : c.tracks) {
			tr.sample(t, c.loop, pose.scratch);
			float[] acc = pose.get(tr.bone, tr.channel);
			if (tr.channel == SCALE) {
				acc[0] += (pose.scratch[0] - 1.0F) * weight;
				acc[1] += (pose.scratch[1] - 1.0F) * weight;
				acc[2] += (pose.scratch[2] - 1.0F) * weight;
			} else {
				acc[0] += pose.scratch[0] * weight;
				acc[1] += pose.scratch[1] * weight;
				acc[2] += pose.scratch[2] * weight;
			}
		}
	}

	/** Adds a clip played so that its whole length spans {@code ticks} ticks ({@code elapsed} of them gone): wind-ups
	 * whose real length the server decides (enraged, combos) still reach their loaded pose exactly on the strike tick. */
	public void addFitted(String clip, float elapsed, float ticks, float weight, Pose pose) {
		Clip c = this.clips.get(clip);
		if (c == null) {
			return;
		}
		float f = ticks > 0 ? Mth.clamp(elapsed / ticks, 0.0F, 1.0F) : 1.0F;
		this.add(clip, f * c.length, weight, pose);
	}

	/** Calls {@code sink} for every event of {@code clip} whose time lies in (from, to] seconds (looping clips wrap). */
	public void events(String clip, float from, float to, EventSink sink) {
		Clip c = this.clips.get(clip);
		if (c == null || c.events.length == 0 || to <= from) {
			return;
		}
		for (Event ev : c.events) {
			if (c.loop && c.length > 0) {
				float base = (float) Math.floor(from / c.length) * c.length;
				for (float k = base; k <= to; k += c.length) {
					float at = k + ev.time;
					if (at > from && at <= to) {
						sink.fire(ev.name, ev.strength);
					}
				}
			} else if (ev.time > from && ev.time <= to) {
				sink.fire(ev.name, ev.strength);
			}
		}
	}

	@FunctionalInterface
	public interface EventSink {
		void fire(String name, float strength);
	}

	/** Resolves every animated bone name to its ModelPart below {@code meshRoot} (the part holding "root"). */
	public Map<String, ModelPart> resolve(ModelPart meshRoot) {
		Map<String, ModelPart> out = new HashMap<>();
		for (String bone : this.parents.keySet()) {
			Optional<ModelPart> p = find(meshRoot, bone, out);
			p.ifPresent(part -> out.put(bone, part));
		}
		return out;
	}

	private Optional<ModelPart> find(ModelPart meshRoot, String bone, Map<String, ModelPart> cache) {
		if (cache.containsKey(bone)) {
			return Optional.of(cache.get(bone));
		}
		String parent = this.parents.get(bone);
		ModelPart base = parent == null ? meshRoot : find(meshRoot, parent, cache).orElse(null);
		if (base == null || !base.hasChild(bone)) {
			return Optional.empty();
		}
		ModelPart p = base.getChild(bone);
		cache.put(bone, p);
		return Optional.of(p);
	}

	/** Accumulated rotation (degrees) / position (pixels) / scale offset (scale - 1) per bone, in Blockbench space. */
	public static final class Pose {
		final Map<String, float[]> rot = new HashMap<>();
		final Map<String, float[]> pos = new HashMap<>();
		final Map<String, float[]> scale = new HashMap<>();
		final float[] scratch = new float[3];

		float[] get(String bone, int channel) {
			Map<String, float[]> m = channel == POS ? this.pos : channel == SCALE ? this.scale : this.rot;
			return m.computeIfAbsent(bone, k -> new float[3]);
		}

		/** The accumulated rotation of {@code bone} in degrees (Blockbench space), or null when nothing moved it. */
		public float[] rotation(String bone) {
			return this.rot.get(bone);
		}

		public void applyTo(Map<String, ModelPart> parts) {
			this.rot.forEach((bone, r) -> {
				ModelPart p = parts.get(bone);
				if (p != null) {
					p.xRot -= r[0] * Mth.DEG_TO_RAD;
					p.yRot -= r[1] * Mth.DEG_TO_RAD;
					p.zRot += r[2] * Mth.DEG_TO_RAD;
				}
			});
			this.pos.forEach((bone, v) -> {
				ModelPart p = parts.get(bone);
				if (p != null) {
					p.x -= v[0];
					p.y -= v[1];
					p.z += v[2];
				}
			});
			this.scale.forEach((bone, s) -> {
				ModelPart p = parts.get(bone);
				if (p != null) {
					p.xScale *= Math.max(0.01F, 1.0F + s[0]);
					p.yScale *= Math.max(0.01F, 1.0F + s[1]);
					p.zScale *= Math.max(0.01F, 1.0F + s[2]);
				}
			});
		}
	}

	private record Event(float time, String name, float strength) {
	}

	private record Clip(float length, boolean loop, Track[] tracks, Event[] events) {
		static Clip parse(JsonObject o) {
			List<Track> tracks = new ArrayList<>();
			for (Map.Entry<String, JsonElement> b : o.getAsJsonObject("bones").entrySet()) {
				for (Map.Entry<String, JsonElement> ch : b.getValue().getAsJsonObject().entrySet()) {
					JsonArray keys = ch.getValue().getAsJsonArray();
					int n = keys.size();
					float[] t = new float[n];
					float[][] v = new float[n][3];
					float[][] h = new float[n][];
					byte[] interp = new byte[n];
					for (int i = 0; i < n; i++) {
						JsonArray k = keys.get(i).getAsJsonArray();
						t[i] = k.get(0).getAsFloat();
						v[i][0] = k.get(1).getAsFloat();
						v[i][1] = k.get(2).getAsFloat();
						v[i][2] = k.get(3).getAsFloat();
						String m = k.get(4).getAsString();
						interp[i] = switch (m) {
							case "step" -> STEP;
							case "catmullrom" -> CATMULL;
							case "bezier" -> BEZIER;
							default -> LINEAR;
						};
						if (interp[i] == BEZIER && k.size() >= 17) {
							h[i] = new float[12];   // left time xyz, left value xyz, right time xyz, right value xyz
							for (int j = 0; j < 12; j++) {
								h[i][j] = k.get(5 + j).getAsFloat();
							}
						}
					}
					int channel = switch (ch.getKey()) {
						case "position" -> POS;
						case "scale" -> SCALE;
						default -> ROT;
					};
					tracks.add(new Track(b.getKey(), channel, t, v, h, interp));
				}
			}
			List<Event> events = new ArrayList<>();
			if (o.has("events")) {
				for (JsonElement e : o.getAsJsonArray("events")) {
					JsonArray a = e.getAsJsonArray();
					events.add(new Event(a.get(0).getAsFloat(), a.get(1).getAsString(), a.size() > 2 ? a.get(2).getAsFloat() : 1.0F));
				}
			}
			return new Clip(o.get("length").getAsFloat(), o.has("loop") && o.get("loop").getAsBoolean(),
				tracks.toArray(new Track[0]), events.toArray(new Event[0]));
		}
	}

	private record Track(String bone, int channel, float[] t, float[][] v, float[][] h, byte[] interp) {
		void sample(float time, boolean loop, float[] out) {
			int n = this.t.length;
			if (time <= this.t[0]) {
				copy(this.v[0], out);
				return;
			}
			if (time >= this.t[n - 1]) {
				copy(this.v[n - 1], out);
				return;
			}
			int i = 0;
			while (i < n - 2 && time > this.t[i + 1]) {
				i++;
			}
			float span = Math.max(1.0E-6F, this.t[i + 1] - this.t[i]);
			float f = (time - this.t[i]) / span;
			byte a = this.interp[i], b = this.interp[i + 1];
			if (a == STEP) {
				copy(this.v[i], out);
			} else if (a == LINEAR && (b == LINEAR || b == STEP)) {
				for (int j = 0; j < 3; j++) {
					out[j] = this.v[i][j] + (this.v[i + 1][j] - this.v[i][j]) * f;
				}
			} else if (a == CATMULL || b == CATMULL) {
				// Blockbench: missing neighbours wrap round in looping clips (>= 3 keys), else repeat the end key
				int ip = i - 1, in = i + 2;
				if (ip < 0) {
					ip = loop && n >= 3 ? n - 2 : 0;
				}
				if (in > n - 1) {
					in = loop && n >= 3 ? 1 : n - 1;
				}
				float[] p0 = this.v[ip], p1 = this.v[i], p2 = this.v[i + 1], p3 = this.v[in];
				for (int j = 0; j < 3; j++) {
					out[j] = catmull(p0[j], p1[j], p2[j], p3[j], f);
				}
			} else {
				float[] hb = this.h[i] != null ? this.h[i] : DEFAULT_HANDLES, ha = this.h[i + 1] != null ? this.h[i + 1] : DEFAULT_HANDLES;
				for (int j = 0; j < 3; j++) {
					float rt = Mth.clamp(hb[6 + j], 0.0F, span), lt = Mth.clamp(ha[j], -span, 0.0F);
					out[j] = bezier(this.t[i], this.v[i][j], this.t[i] + rt, this.v[i][j] + hb[9 + j],
						this.t[i + 1] + lt, this.v[i + 1][j] + ha[3 + j], this.t[i + 1], this.v[i + 1][j], time);
				}
			}
		}

		/** What Blockbench gives a key without handles of its own: +-0.1 s, flat. */
		private static final float[] DEFAULT_HANDLES = {-0.1F, -0.1F, -0.1F, 0, 0, 0, 0.1F, 0.1F, 0.1F, 0, 0, 0};

		private static void copy(float[] from, float[] to) {
			to[0] = from[0];
			to[1] = from[1];
			to[2] = from[2];
		}

		private static float catmull(float p0, float p1, float p2, float p3, float t) {
			float t2 = t * t, t3 = t2 * t;
			return 0.5F * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
		}

		/** A cubic Bezier in (time, value) space, solved for the curve parameter whose time is {@code at}. */
		private static float bezier(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float at) {
			float lo = 0.0F, hi = 1.0F, u = 0.5F;
			for (int k = 0; k < 24; k++) {
				u = (lo + hi) * 0.5F;
				float x = cubic(x0, x1, x2, x3, u);
				if (x < at) {
					lo = u;
				} else {
					hi = u;
				}
			}
			return cubic(y0, y1, y2, y3, u);
		}

		private static float cubic(float a, float b, float c, float d, float u) {
			float m = 1.0F - u;
			return m * m * m * a + 3 * m * m * u * b + 3 * m * u * u * c + u * u * u * d;
		}
	}
}
