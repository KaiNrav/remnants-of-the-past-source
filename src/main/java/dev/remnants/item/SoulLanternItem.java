package dev.remnants.item;

import dev.remnants.PathBranch;
import dev.remnants.RemnantsConfig;
import dev.remnants.SoulAllies;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Soul Lantern: right-click a monster worn down to {@code lanternCatchHealth} (a quarter) of its health and it is
 * caught inside (not a boss, never a Remnant); the lantern glows with its colour. Right-click a block (or the air) to set
 * it free as a soul ally for {@code lanternAllyTicks} (twice as long for a Keeper), see {@link SoulAllies}. One prisoner
 * per lantern.
 */
public class SoulLanternItem extends LoreItem {
	/** Never caught: anything in this tag (the vanilla bosses and ours are in it by default). */
	public static final TagKey<EntityType<?>> UNCATCHABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(dev.remnants.RemnantsMod.MOD_ID, "lantern_immune"));

	public SoulLanternItem(Properties properties) {
		super(properties, 2);
	}

	public static boolean holds(ItemStack stack) {
		return stack.has(ModComponents.CAPTURED);
	}

	@Nullable
	public static EntityType<?> heldType(ItemStack stack) {
		CustomData data = stack.get(ModComponents.CAPTURED);
		if (data == null) {
			return null;
		}
		ResourceLocation id = ResourceLocation.tryParse(data.copyTag().getString("id"));
		return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
	}

	public enum Catch {
		CAUGHT, FULL, HEALTHY, IMMUNE
	}

	public static Catch canCatch(ItemStack lantern, LivingEntity target) {
		if (holds(lantern)) {
			return Catch.FULL;
		}
		if (!(target instanceof Mob) || !(target instanceof Enemy) || target instanceof RemnantEntity || target.getType().is(UNCATCHABLE)
			|| SoulAllies.isAlly(target) || !target.canChangeDimensions(target.level(), target.level())) {
			return Catch.IMMUNE;
		}
		if (target.getHealth() > target.getMaxHealth() * RemnantsConfig.get().lanternCatchHealth) {
			return Catch.HEALTHY;
		}
		return Catch.CAUGHT;
	}

	/** Catches {@code target} into the lantern (server). */
	public static Catch capture(ServerPlayer player, ItemStack lantern, LivingEntity target) {
		Catch c = canCatch(lantern, target);
		if (c != Catch.CAUGHT) {
			player.displayClientMessage(Component.translatable("message.remnants.lantern." + c.name().toLowerCase(java.util.Locale.ROOT),
				target.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
			return c;
		}
		CompoundTag tag = new CompoundTag();
		target.saveWithoutId(tag);
		tag.putString("id", EntityType.getKey(target.getType()).toString());
		tag.remove("UUID");
		tag.remove("Passengers");
		lantern.set(ModComponents.CAPTURED, CustomData.of(tag));
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 30, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 1.4F);
		level.playSound(null, player.blockPosition(), SoundEvents.LANTERN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
		target.ejectPassengers();
		target.stopRiding();
		target.discard();
		player.displayClientMessage(Component.translatable("message.remnants.lantern.caught", target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
		return Catch.CAUGHT;
	}

	/** Releases the prisoner at {@code at} as an ally of {@code player}; null if there was none (or it could not be made). */
	@Nullable
	public static Mob release(ServerPlayer player, ItemStack lantern, Vec3 at) {
		CustomData data = lantern.get(ModComponents.CAPTURED);
		if (data == null) {
			return null;
		}
		ServerLevel level = player.serverLevel();
		CompoundTag tag = data.copyTag();
		Entity e = EntityType.loadEntityRecursive(tag, level, made -> {
			made.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
			return made;
		});
		if (!(e instanceof Mob mob)) {
			return null;
		}
		mob.setUUID(UUID.randomUUID());
		mob.setHealth(mob.getMaxHealth());
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		lantern.remove(ModComponents.CAPTURED);
		int ticks = RemnantsConfig.get().lanternAllyTicks * (PathBranch.is(player, PathBranch.Branch.KEEPER) ? 2 : 1);
		SoulAllies.bind(mob, player, ticks);
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y + mob.getBbHeight() * 0.5, at.z, 30, 0.3, 0.6, 0.3, 0.05);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 12, 0.4, 0.1, 0.4, 0.02);
		level.playSound(null, BlockPos.containing(at), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
		player.displayClientMessage(Component.translatable("message.remnants.lantern.released", mob.getDisplayName(), ticks / 20)
			.withStyle(ChatFormatting.AQUA), true);
		HeartBlockEntity.award(player, "lantern_ally");
		return mob;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (holds(stack)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			capture(sp, stack, target);
			player.getCooldowns().addCooldown(this, 10);
		}
		return InteractionResult.sidedSuccess(player.level().isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		ItemStack stack = ctx.getItemInHand();
		if (!holds(stack) || ctx.getPlayer() == null) {
			return InteractionResult.PASS;
		}
		if (ctx.getPlayer() instanceof ServerPlayer sp) {
			BlockPos p = ctx.getClickedPos().relative(ctx.getClickedFace());
			if (release(sp, stack, Vec3.atBottomCenterOf(p)) != null) {
				sp.getCooldowns().addCooldown(this, 100);
			}
		}
		return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!holds(stack)) {
			return InteractionResultHolder.pass(stack);
		}
		if (player instanceof ServerPlayer sp) {
			Vec3 front = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(2.0));
			if (release(sp, stack, front) != null) {
				sp.getCooldowns().addCooldown(this, 100);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		EntityType<?> type = heldType(stack);
		if (type != null) {
			tooltip.add(Component.translatable("item.remnants_of_the_past.soul_lantern_trap.holds", type.getDescription()).withStyle(ChatFormatting.AQUA));
			tooltip.add(Component.translatable("item.remnants_of_the_past.soul_lantern_trap.release").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			super.appendHoverText(stack, context, tooltip, flag);
		}
	}
}
