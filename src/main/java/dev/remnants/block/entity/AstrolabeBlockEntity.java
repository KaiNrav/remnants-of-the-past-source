package dev.remnants.block.entity;

import dev.remnants.RemnantTracker;
import dev.remnants.RemnantsConfig;
import dev.remnants.item.EpitaphData;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModItems;
import dev.remnants.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Soul Astrolabe's heart (1.5). A focus rests on the sphere: a Spectral Compass (your nearest Remnant), a map (the
 * nearest Ossuary or Library not yet on any map, as a cartographer's map finds it), an Epitaph (the place of that death).
 * A search costs a Soul Fragment: the rings spin for {@link #SPIN} ticks and lock in line (phase 1), then a beam points
 * at the target for {@code astrolabeBeamTicks} (phase 2), with the distance over the sphere.
 */
public class AstrolabeBlockEntity extends HeartBlockEntity {
	public static final int SPIN = 40;
	public static final String REMNANT = "remnant", STRUCTURE = "ossuary", GRAVE = "grave";
	private ItemStack focus = ItemStack.EMPTY;
	@Nullable
	private Vec3 target;
	private String kind = "";

	public AstrolabeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ASTROLABE, pos, state);
	}

	@Override
	public MultiblockPattern pattern() {
		return Multiblocks.astrolabe();
	}

	@Override
	protected int phaseLength(int phase) {
		return phase == 1 ? SPIN : phase == 2 ? Math.max(20, RemnantsConfig.get().astrolabeBeamTicks) : 0;
	}

	@Override
	protected int lastPhase() {
		return 2;
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		if (phase == 2) {
			level.playSound(null, this.worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.5F);
			level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.2F);
		} else if (phase == 0) {
			this.target = null;
			this.kind = "";
		}
	}

	@Override
	protected void onPhaseTick(ServerLevel level, int phase, int ticks) {
		if (phase == 1 && ticks % 8 == 0) {
			level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F + ticks / 60.0F);
		}
	}

	@Override
	protected List<ItemStack> takeAll() {
		ItemStack out = this.focus;
		this.focus = ItemStack.EMPTY;
		this.target = null;
		return out.isEmpty() ? List.of() : List.of(out);
	}

	public ItemStack focus() {
		return this.focus;
	}

	@Nullable
	public Vec3 target() {
		return this.target;
	}

	public String kind() {
		return this.kind;
	}

	public static boolean isFocus(ItemStack s) {
		return s.is(ModItems.SPECTRAL_COMPASS) || s.is(Items.MAP) || s.is(Items.FILLED_MAP) || s.is(ModItems.EPITAPH);
	}

	/** Right-click with a focus: set it on the sphere. */
	public boolean insert(ServerLevel level, Player player, ItemStack stack) {
		if (!this.focus.isEmpty() || !isFocus(stack)) {
			return false;
		}
		this.focus = stack.copyWithCount(1);
		stack.consume(1, player);
		level.playSound(null, this.worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
		this.sync();
		return true;
	}

	/** Shift + right-click: take the focus back (the beam goes out). */
	public boolean takeFocus(ServerLevel level, Player player) {
		if (this.focus.isEmpty()) {
			return false;
		}
		give(level, player, this.worldPosition, this.focus);
		this.focus = ItemStack.EMPTY;
		if (this.phase != 0) {
			this.phase = 0;
			this.target = null;
		}
		level.playSound(null, this.worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
		this.sync();
		return true;
	}

	/** Right-click with an empty hand: search with the focus. Returns true when the rings started to turn. */
	public boolean search(ServerLevel level, ServerPlayer player) {
		if (!this.formed) {
			this.tryAssemble(level, player);
			return false;
		}
		if (this.phase != 0) {
			player.displayClientMessage(Component.translatable("message.remnants.astrolabe.busy").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (this.focus.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.remnants.astrolabe.no_focus").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		boolean creative = player.getAbilities().instabuild;
		if (!creative && player.getInventory().countItem(ModItems.SOUL_FRAGMENT) < 1) {
			player.displayClientMessage(Component.translatable("message.remnants.astrolabe.cost").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		Vec3 found = null;
		String what = "";
		if (this.focus.is(ModItems.SPECTRAL_COMPASS)) {
			RemnantTracker tracker = RemnantTracker.get(level.getServer());
			var here = tracker.nearestLiving(level.getServer(), player.getUUID(), level.dimension(), this.worldPosition);
			if (here.isPresent()) {
				found = Vec3.atCenterOf(here.get().pos());
				what = REMNANT;
			} else {
				for (Map.Entry<UUID, RemnantTracker.Location> e : tracker.remnants(player.getUUID()).entrySet()) {
					BlockPos p = e.getValue().pos();
					player.displayClientMessage(Component.translatable("message.remnants.astrolabe.elsewhere", dimName(e.getValue().dimension().location()),
						p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.AQUA), false);
					return false;
				}
			}
		} else if (this.focus.is(ModItems.EPITAPH)) {
			EpitaphData d = this.focus.get(ModComponents.EPITAPH);
			if (d != null) {
				if (d.dimension().equals(level.dimension().location().toString())) {
					found = Vec3.atCenterOf(d.pos());
					what = GRAVE;
				} else {
					player.displayClientMessage(Component.translatable("message.remnants.astrolabe.elsewhere",
						dimName(ResourceLocation.tryParse(d.dimension())), d.pos().getX(), d.pos().getY(), d.pos().getZ()).withStyle(ChatFormatting.AQUA), false);
					return false;
				}
			}
		} else {
			BlockPos p = level.findNearestMapStructure(ModTags.ASTROLABE_TARGETS, this.worldPosition,
				Math.max(1, RemnantsConfig.get().astrolabeSearchChunks), true);
			if (p != null) {
				found = Vec3.atCenterOf(p);
				what = STRUCTURE;
			}
		}
		if (found == null) {
			player.displayClientMessage(Component.translatable("message.remnants.astrolabe.nothing").withStyle(ChatFormatting.GRAY), true);
			level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F);
			return false;
		}
		if (!creative) {
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).is(ModItems.SOUL_FRAGMENT)) {
					inv.getItem(i).shrink(1);
					break;
				}
			}
		}
		this.target = found;
		this.kind = what;
		this.operator = player.getUUID();
		this.operatorName = player.getGameProfile().getName();
		this.setPhase(level, 1);
		level.sendParticles(ParticleTypes.SOUL, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.8, this.worldPosition.getZ() + 0.5,
			16, 0.4, 0.3, 0.4, 0.02);
		award(player, "star_path");
		return true;
	}

	public int distance() {
		if (this.target == null) {
			return 0;
		}
		double dx = this.target.x - (this.worldPosition.getX() + 0.5), dz = this.target.z - (this.worldPosition.getZ() + 0.5);
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	private static Component dimName(@Nullable ResourceLocation dim) {
		String key = dim == null ? "overworld" : dim.getPath();
		return Component.translatableWithFallback("epitaph.remnants.dim." + key, key);
	}

	@Override
	protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
		if (!this.focus.isEmpty()) {
			tag.put("Focus", this.focus.save(registries));
		}
		if (this.target != null) {
			tag.putDouble("TX", this.target.x);
			tag.putDouble("TY", this.target.y);
			tag.putDouble("TZ", this.target.z);
			tag.putString("Kind", this.kind);
		}
	}

	@Override
	protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
		this.focus = tag.contains("Focus") ? ItemStack.parse(registries, tag.getCompound("Focus")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		this.target = tag.contains("TX") ? new Vec3(tag.getDouble("TX"), tag.getDouble("TY"), tag.getDouble("TZ")) : null;
		this.kind = tag.getString("Kind");
	}
}
