package dev.remnants.item;

import dev.remnants.entity.PhantomArrowEntity;
import dev.remnants.entity.SpectralWeaponEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Soul King's Arsenal: one weapon, four forms (sword, bow, trident, mace), each with its own 3D model, stats
 * and special. Shift + right-click cycles the form.
 * <ul>
 *   <li>Sword: fast heavy blade with a sweep; right-click fires a Soul Slash (a flying spectral sword).</li>
 *   <li>Bow: endless - draws without ammo or wear and fires straight-flying phantom arrows; a full draw fires three.</li>
 *   <li>Trident: hold and release to hurl a spectral trident that calls lightning where it lands.</li>
 *   <li>Mace: vanilla smash damage from falling; right-click is a Soul Leap that sets the smash up.</li>
 * </ul>
 */
public class ArsenalItem extends Item {
	public static final int SWORD = 0, BOW = 1, TRIDENT = 2, MACE = 3;
	private static final String MODE = "arsenal_mode";
	private static final String[] NAMES = {"sword", "bow", "trident", "mace"};
	//                                       sword  bow   trident mace
	// balanced against netherite (sword 8 / 1.6 per s = 12.8 dps): the sword is ~12 % better, the rest vanilla-like
	private static final double[] DAMAGE = {9.0, 1.0, 9.0, 6.5};
	private static final double[] SPEED = {-2.4, 0.0, -2.9, -3.4};
	private static final int[] SPECIAL_COOLDOWN = {60, 0, 60, 80};
	private static final float SLASH_DAMAGE = 8.0F;
	private static final float THROW_DAMAGE = 10.0F;
	/** Phantom arrow base damage (x launch speed, like vanilla): a full draw fires three at 2.5, otherwise one at 2.0. */
	private static final double ARROW_FULL = 2.5, ARROW_PARTIAL = 2.0;
	/** Awakening (Spectral IV, forged in the Soul Crucible with a second Heart): melee forms hit harder, specials by 20 %. */
	public static final int AWAKENED_LEVEL = 4;
	public static final double AWAKENED_DAMAGE = 2.0;
	public static final float AWAKENED_SPECIALS = 1.2F;

	public ArsenalItem(Properties properties) {
		super(properties.attributes(modifiers(SWORD, false)));
	}

	public static boolean isAwakened(ItemStack stack) {
		return SpectralForge.level(stack) >= AWAKENED_LEVEL;
	}

	private static float specials(ItemStack stack) {
		return isAwakened(stack) ? AWAKENED_SPECIALS : 1.0F;
	}

	/** An awakened copy: Spectral level IV, +1 to every enchantment it carries, the melee bonus, fully repaired. */
	public static ItemStack awaken(ItemStack original, net.minecraft.core.HolderLookup.Provider registries) {
		ItemStack stack = original.copy();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SpectralForge.TAG, AWAKENED_LEVEL));
		net.minecraft.world.item.enchantment.ItemEnchantments current = stack.getEnchantments();
		net.minecraft.world.item.enchantment.ItemEnchantments.Mutable raised = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(current);
		for (var entry : current.entrySet()) {
			raised.set(entry.getKey(), entry.getIntValue() + 1);
		}
		stack.set(DataComponents.ENCHANTMENTS, raised.toImmutable());
		setMode(stack, mode(stack));
		stack.setDamageValue(0);
		return stack;
	}

	public static int mode(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? SWORD : Math.floorMod(data.copyTag().getInt(MODE), 4);
	}

	/** A one-off Arsenal stack showing the given form: what thrown / flying copies of the weapon carry so their renderer
	 * can draw the form's 3D model (a vanilla stand-in item would show a netherite sword / vanilla trident). */
	public static ItemStack display(int mode) {
		ItemStack stack = new ItemStack(dev.remnants.registry.ModItems.SOUL_KING_ARSENAL);
		setMode(stack, mode);
		return stack;
	}

	public static void setMode(ItemStack stack, int mode) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE, mode));
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers(mode, isAwakened(stack)));
		ForgeQuality.refresh(stack);   // the forge quality's bonus follows the form's own damage
	}

	private static ItemAttributeModifiers modifiers(int mode, boolean awakened) {
		double damage = DAMAGE[mode] + (awakened && mode != BOW ? AWAKENED_DAMAGE : 0.0);
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, SPEED[mode], AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
	}

	private static boolean specialReady(ItemStack stack, Level level, int mode) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null || level.getGameTime() >= data.copyTag().getLong("cd" + mode);
	}

	private static void startSpecialCooldown(ItemStack stack, Level level, int mode) {
		long ready = level.getGameTime() + SPECIAL_COOLDOWN[mode];
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong("cd" + mode, ready));
	}

	// ---- right click -------------------------------------------------------------------------------

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int mode = mode(stack);
		if (player.isShiftKeyDown()) {
			int next = (mode + 1) % 4;
			if (!level.isClientSide) {
				setMode(stack, next);
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F + next * 0.2F);
				level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
				((ServerLevel) level).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 12, 0.4, 0.5, 0.4, 0.03);
				player.displayClientMessage(Component.translatable("item.remnants_of_the_past.arsenal.form." + NAMES[next]).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		switch (mode) {
			case BOW, TRIDENT -> {
				player.startUsingItem(hand);
				return InteractionResultHolder.consume(stack);
			}
			case SWORD -> {
				if (!specialReady(stack, level, SWORD)) {
					return InteractionResultHolder.pass(stack);
				}
				if (!level.isClientSide) {
					startSpecialCooldown(stack, level, SWORD);
					Vec3 dir = player.getLookAngle();
					SpectralWeaponEntity slash = SpectralWeaponEntity.launched(level, player, display(SWORD),
						player.getEyePosition().add(dir.scale(0.8)), dir.scale(1.8), SLASH_DAMAGE * specials(stack), false);
					level.addFreshEntity(slash);
					level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
				}
				player.swing(hand);
				return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
			}
			case MACE -> {
				if (!specialReady(stack, level, MACE)) {
					return InteractionResultHolder.pass(stack);
				}
				if (!level.isClientSide) {
					startSpecialCooldown(stack, level, MACE);
					Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
					player.setDeltaMovement(look.x * 0.6, 1.15, look.z * 0.6);
					player.hurtMarked = true;
					player.currentImpulseImpactPos = player.position();
					player.setIgnoreFallDamageFromCurrentImpulse(true);
					level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.8F);
					((ServerLevel) level).sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(), player.getZ(), 15, 0.4, 0.1, 0.4, 0.05);
				}
				return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
			}
			default -> {
				return InteractionResultHolder.pass(stack);
			}
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		int mode = mode(stack);
		return mode == BOW || mode == TRIDENT ? 72000 : 0;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return switch (mode(stack)) {
			case BOW -> UseAnim.BOW;
			case TRIDENT -> UseAnim.SPEAR;
			default -> UseAnim.NONE;
		};
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
		if (!(user instanceof Player player) || level.isClientSide) {
			return;
		}
		int used = this.getUseDuration(stack, user) - timeLeft;
		if (mode(stack) == BOW) {
			// endless: no ammo, no wear - every shot is a phantom arrow conjured from the bow itself
			float power = BowItem.getPowerForTime(used);
			if (power < 0.1F) {
				return;
			}
			int arrows = power >= 1.0F ? 3 : 1;
			for (int i = 0; i < arrows; i++) {
				PhantomArrowEntity arrow = new PhantomArrowEntity(level, player, new ItemStack(Items.ARROW), stack.copyWithCount(1));
				arrow.makeGhost();
				arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + (i - (arrows - 1) / 2.0F) * 6.0F, 0.0F, power * 3.2F, 1.0F);
				// no crit flag: its particle stream flies straight back into the shooter's camera; a full draw hits harder instead
				arrow.setBaseDamage((power >= 1.0F ? ARROW_FULL : ARROW_PARTIAL) * specials(stack));
				level.addFreshEntity(arrow);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F + power * 0.4F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.8F + power * 0.5F);
			player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
		} else if (mode(stack) == TRIDENT && used >= 10 && specialReady(stack, level, TRIDENT)) {
			startSpecialCooldown(stack, level, TRIDENT);
			Vec3 dir = player.getLookAngle();
			level.addFreshEntity(SpectralWeaponEntity.launched(level, player, display(TRIDENT),
				player.getEyePosition().add(dir.scale(0.8)), dir.scale(2.5), THROW_DAMAGE * specials(stack), true));
			level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
			stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
	}

	// ---- melee -------------------------------------------------------------------------------------

	/** Mace form uses the vanilla mace smash formula (more damage the further you fell). */
	@Override
	public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
		if (source.getDirectEntity() instanceof LivingEntity attacker && mode(attacker.getMainHandItem()) == MACE && MaceItem.canSmashAttack(attacker)) {
			float fall = attacker.fallDistance;
			return fall <= 3.0F ? 4.0F * fall : fall <= 8.0F ? 12.0F + 2.0F * (fall - 3.0F) : 22.0F + fall - 8.0F;
		}
		return 0.0F;
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		Level level = attacker.level();
		int mode = mode(stack);
		if (mode == MACE && attacker instanceof ServerPlayer player && MaceItem.canSmashAttack(player)) {
			player.setDeltaMovement(player.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0.01));
			player.hurtMarked = true;
			level.playSound(null, player.blockPosition(), player.fallDistance > 5 ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND,
				SoundSource.PLAYERS, 1.0F, 1.0F);
			level.levelEvent(2013, target.getOnPos(), 750);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5), e -> e != player && e != target && !e.isAlliedTo(player))) {
				Vec3 push = e.position().subtract(target.position()).normalize().scale(0.8);
				e.push(push.x, 0.6, push.z);
				e.hurtMarked = true;
			}
		} else if (mode == SWORD && level instanceof ServerLevel server) {
			// sweep: nearby enemies take 40 % of the hit
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.5, 0.25, 1.5),
				e -> e != attacker && e != target && !e.isAlliedTo(attacker) && !(e instanceof Player))) {
				e.hurt(attacker.damageSources().mobAttack(attacker), (float) DAMAGE[SWORD] * 0.4F);
			}
			server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
		}
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		if (mode(stack) == MACE && MaceItem.canSmashAttack(attacker)) {
			attacker.resetFallDistance();
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String form = NAMES[mode(stack)];
		tooltip.add(Component.translatable("item.remnants_of_the_past.arsenal.current", Component.translatable("item.remnants_of_the_past.arsenal.form." + form))
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.remnants_of_the_past.arsenal.special." + form).withStyle(ChatFormatting.GRAY));
		if (isAwakened(stack)) {
			tooltip.add(Component.translatable("item.remnants_of_the_past.arsenal.awakened").withStyle(ChatFormatting.AQUA));
		}
		tooltip.add(Component.translatable("item.remnants_of_the_past.arsenal.switch").withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(dev.remnants.registry.ModItems.CONDENSED_SOUL);
	}
}
