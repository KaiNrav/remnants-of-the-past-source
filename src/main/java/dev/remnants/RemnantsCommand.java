package dev.remnants;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /remnants list                    your unclaimed Remnants and death streak
 * /remnants reload                  reload config/remnants_of_the_past.json (op)
 * /remnants kit melee|ranged        a shiny diamond kit for "Minecraft but..." takes (op)
 * /remnants summon melee|ranged     a display-only Remnant of yourself, 4 blocks ahead, carrying nothing (op)
 * /remnants empower                 your next Remnant is guaranteed Empowered (op, for testing)
 * /remnants phase 2|3               push the nearest Remnant into that phase (op, for testing)
 * /remnants codex                   a fresh Soul Codex
 * /remnants resonance [add <n>]     your Soul Resonance; add points (op, for testing)
 * /remnants graves [radius]         Forgotten Graves (their points of interest) around you: count, density, nearest (op)
 */
public final class RemnantsCommand {
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("remnants")
			.then(Commands.literal("list").executes(RemnantsCommand::list))
			.then(Commands.literal("codex").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				// anyone may ask for a lost Codex, but only for one (no stacks of books flooding a server)
				ItemStack codex = new ItemStack(dev.remnants.registry.ModItems.SOUL_CODEX);
				if (p.getInventory().contains(codex) && !ctx.getSource().hasPermission(2)) {
					return 0;
				}
				p.getInventory().add(codex);
				return 1;
			}))
			.then(Commands.literal("resonance").executes(RemnantsCommand::resonance)
				.then(Commands.literal("add").requires(s -> s.hasPermission(2))
					.then(Commands.argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000)).executes(ctx -> {
						ServerPlayer p = ctx.getSource().getPlayerOrException();
						SoulResonance.award(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "points"), "");
						return 1;
					}))))
			.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(ctx -> {
				RemnantsConfig.load();
				ctx.getSource().sendSuccess(() -> Component.translatable("message.remnants.reloaded"), true);
				return 1;
			}))
			.then(Commands.literal("kit").requires(s -> s.hasPermission(2))
				.then(Commands.literal("melee").executes(ctx -> kit(ctx, false)))
				.then(Commands.literal("ranged").executes(ctx -> kit(ctx, true))))
			.then(Commands.literal("empower").requires(s -> s.hasPermission(2)).executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				DeathHandler.FORCE_EMPOWER.add(p.getUUID());
				ctx.getSource().sendSuccess(() -> Component.translatable("message.remnants.force_empower").withStyle(ChatFormatting.LIGHT_PURPLE), false);
				return 1;
			}))
			.then(Commands.literal("phase").requires(s -> s.hasPermission(2))
				.then(Commands.literal("2").executes(ctx -> phase(ctx, 0.5F)))
				.then(Commands.literal("3").executes(ctx -> phase(ctx, 0.2F))))
			.then(king())
			.then(Commands.literal("graves").requires(src -> src.hasPermission(2)).executes(ctx -> graves(ctx, 128))
				.then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(16, 256))
					.executes(ctx -> graves(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
			.then(Commands.literal("summon").requires(s -> s.hasPermission(2))
				.then(Commands.literal("melee").executes(ctx -> summon(ctx, false)))
				.then(Commands.literal("ranged").executes(ctx -> summon(ctx, true)))));
	}

	/** Counts the grave POIs in loaded / saved chunks within a square of the given radius (worldgen check). */
	private static int graves(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		ServerLevel level = p.serverLevel();
		net.minecraft.core.BlockPos at = p.blockPosition();
		List<net.minecraft.core.BlockPos> found = level.getPoiManager().getInSquare(h -> h.is(dev.remnants.registry.ModWorldgen.GRAVE_POI), at, radius,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
			.sorted(java.util.Comparator.comparingDouble(b -> b.distSqr(at))).toList();
		int chunks = (2 * radius / 16 + 1) * (2 * radius / 16 + 1);
		StringBuilder near = new StringBuilder();
		for (int i = 0; i < Math.min(3, found.size()); i++) {
			net.minecraft.core.BlockPos b = found.get(i);
			near.append(i == 0 ? "" : "; ").append(b.getX()).append(' ').append(b.getY()).append(' ').append(b.getZ())
				.append(" (").append((int) Math.sqrt(b.distSqr(at))).append(')');
		}
		String density = found.isEmpty() ? "-" : String.format(java.util.Locale.ROOT, "%.1f", chunks / (double) found.size());
		ctx.getSource().sendSuccess(() -> Component.translatable("message.remnants.graves.count", found.size(), chunks, density, near.toString()), false);
		return found.size();
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RemnantTracker tracker = RemnantTracker.get(ctx.getSource().getServer());
		Map<UUID, RemnantTracker.Location> remnants = tracker.remnants(player.getUUID());
		ctx.getSource().sendSuccess(() -> Component.translatable("message.remnants.list.header", remnants.size(), tracker.streak(player.getUUID()))
			.withStyle(ChatFormatting.AQUA), false);
		remnants.values().forEach(loc -> ctx.getSource().sendSuccess(() -> Component.literal(String.format(" • %s  %d %d %d  (tier %d)",
			loc.dimension().location().getPath(), loc.pos().getX(), loc.pos().getY(), loc.pos().getZ(), loc.tier() + 1)).withStyle(ChatFormatting.GRAY), false));
		return remnants.size();
	}

	private static int resonance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RemnantTracker.Resonance r = RemnantTracker.get(ctx.getSource().getServer()).resonance(player.getUUID());
		int rank = SoulResonance.rankOf(r.points());
		ctx.getSource().sendSuccess(() -> Component.translatable("message.remnants.resonance.status", r.points(), rank,
			Component.translatable("resonance.remnants.rank." + rank), r.laidOwn(), r.laidForgotten(), r.kingsSlain()).withStyle(ChatFormatting.AQUA), false);
		return r.points();
	}

	private static int kit(CommandContext<CommandSourceStack> ctx, boolean ranged) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Registry<Enchantment> ench = player.serverLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(Items.DIAMOND_HELMET, ench, Enchantments.PROTECTION, 2));
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(Items.DIAMOND_CHESTPLATE, ench, Enchantments.PROTECTION, 3));
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(Items.DIAMOND_LEGGINGS, ench, Enchantments.PROTECTION, 2));
		player.setItemSlot(EquipmentSlot.FEET, enchanted(Items.DIAMOND_BOOTS, ench, Enchantments.FEATHER_FALLING, 3));
		player.getInventory().selected = 0;
		player.getInventory().setItem(0, ranged ? enchanted(Items.BOW, ench, Enchantments.POWER, 3) : enchanted(Items.DIAMOND_SWORD, ench, Enchantments.SHARPNESS, 3));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ranged ? Items.ARROW : Items.SHIELD, ranged ? 64 : 1));
		for (ItemStack extra : List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.GOLDEN_APPLE, 6),
			new ItemStack(Items.COOKED_BEEF, 32), new ItemStack(Items.DIAMOND, 24), new ItemStack(Items.ENDER_PEARL, 8))) {
			player.getInventory().add(extra);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("message.remnants.kit", player.getDisplayName()), true);
		return 1;
	}

	private static ItemStack enchanted(net.minecraft.world.item.Item item, Registry<Enchantment> registry, ResourceKey<Enchantment> key, int lvl) {
		ItemStack stack = new ItemStack(item);
		Holder<Enchantment> holder = registry.getHolderOrThrow(key);
		stack.enchant(holder, lvl);
		return stack;
	}

	private static int summon(CommandContext<CommandSourceStack> ctx, boolean ranged) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		RemnantEntity remnant = ModEntities.REMNANT.create(level);
		if (remnant == null) {
			return 0;
		}
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
		remnant.moveTo(ahead.x, player.getY(), ahead.z, player.getYRot() + 180.0F, 0.0F);
		RemnantEntity.EquipmentSnapshot look = RemnantEntity.EquipmentSnapshot.of(player);
		if (ranged != (look.mainHand().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) {
			look = new RemnantEntity.EquipmentSnapshot(look.head(), look.chest(), look.legs(), look.feet(),
				new ItemStack(ranged ? Items.BOW : Items.IRON_SWORD), look.offHand());
		}
		remnant.rise(player, List.of(), look, RemnantTracker.get(level.getServer()).streak(player.getUUID()), false);
		level.addFreshEntity(remnant);
		return 1;
	}

	/** /remnants king <action> | cast <wail|beam|call> | intro : make the nearest Soul King perform it (animation testing). */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> king() {
		var root = Commands.literal("king").requires(s -> s.hasPermission(2));
		for (dev.remnants.entity.SoulKingEntity.Action a : dev.remnants.entity.SoulKingEntity.Action.values()) {
			root.then(Commands.literal(a.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> withKing(ctx, (k, p) -> k.debugAction(a, p))));
		}
		var cast = Commands.literal("cast");
		String[] casts = {"wail", "call", "beam"};
		for (int i = 0; i < casts.length; i++) {
			int id = i + 1;
			cast.then(Commands.literal(casts[i]).executes(ctx -> withKing(ctx, (k, p) -> k.debugCast(id))));
		}
		return root.then(cast).then(Commands.literal("intro").executes(ctx -> withKing(ctx, (k, p) -> k.debugIntro())));
	}

	private static int withKing(CommandContext<CommandSourceStack> ctx,
			java.util.function.BiConsumer<dev.remnants.entity.SoulKingEntity, ServerPlayer> action) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		dev.remnants.entity.SoulKingEntity king = player.serverLevel().getNearestEntity(dev.remnants.entity.SoulKingEntity.class,
			net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight(), player,
			player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(64));
		if (king == null) {
			ctx.getSource().sendFailure(Component.literal("No Soul King within 64 blocks"));
			return 0;
		}
		action.accept(king, player);
		return 1;
	}

	private static int phase(CommandContext<CommandSourceStack> ctx, float healthFraction) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RemnantEntity nearest = player.serverLevel().getNearestEntity(RemnantEntity.class,
			net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight(), player,
			player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(48));
		if (nearest == null || nearest.isIllusion()) {
			ctx.getSource().sendFailure(Component.translatable("message.remnants.compass.none"));
			return 0;
		}
		nearest.setHealth(nearest.getMaxHealth() * healthFraction);
		return 1;
	}

	private RemnantsCommand() {
	}
}
