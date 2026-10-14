package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.RemnantsMod;
import dev.remnants.item.ArsenalItem;
import dev.remnants.registry.ModItems;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Items with two faces: the flat pixel icon in inventories, item frames and on the ground, the full 3D model in hand
 * and on the head. The item's own model is {@code builtin/entity}; this renderer picks one of two extra baked models
 * (registered through the model loading plugin, since no item references them directly).
 */
public final class TwoFaceItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private static final String[] FORMS = {"sword", "bow", "trident", "mace"};

	private final Function<ItemStack, ResourceLocation> icon;
	private final Function<ItemStack, ResourceLocation> model3d;

	private TwoFaceItemRenderer(Function<ItemStack, ResourceLocation> icon, Function<ItemStack, ResourceLocation> model3d) {
		this.icon = icon;
		this.model3d = model3d;
	}

	public static void register() {
		ModelLoadingPlugin.register(ctx -> {
			for (String form : FORMS) {
				ctx.addModels(RemnantsMod.id("item/arsenal_" + form + "_icon"), RemnantsMod.id("item/arsenal_" + form + "_3d"));
			}
			for (int stage = 0; stage < 3; stage++) {
				ctx.addModels(RemnantsMod.id("item/arsenal_bow_3d_pull" + stage));
			}
			ctx.addModels(RemnantsMod.id("item/remnant_crown_icon"), RemnantsMod.id("item/remnant_crown_3d"));
			ctx.addModels(RemnantsMod.id("item/wardens_gauntlet_icon"), RemnantsMod.id("item/wardens_gauntlet_3d"));
		});
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.SOUL_KING_ARSENAL, new TwoFaceItemRenderer(
			s -> RemnantsMod.id("item/arsenal_" + FORMS[ArsenalItem.mode(s)] + "_icon"),
			TwoFaceItemRenderer::arsenal3d));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.REMNANT_CROWN, new TwoFaceItemRenderer(
			s -> RemnantsMod.id("item/remnant_crown_icon"), s -> RemnantsMod.id("item/remnant_crown_3d")));
		// the Warden's own gauntlet, worn: its 3D model sits on the hand, round the grip
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.WARDENS_GAUNTLET, new TwoFaceItemRenderer(
			s -> RemnantsMod.id("item/wardens_gauntlet_icon"), s -> RemnantsMod.id("item/wardens_gauntlet_3d")));
	}

	/** The bow form swaps in a drawn model (string pulled back, phantom arrow nocked) while someone is drawing it. */
	private static ResourceLocation arsenal3d(ItemStack stack) {
		int mode = ArsenalItem.mode(stack);
		if (mode == ArsenalItem.BOW) {
			LivingEntity user = drawing(stack);
			if (user != null) {
				float pull = user.getTicksUsingItem() / 20.0F;
				int stage = pull < 0.65F ? 0 : pull < 0.9F ? 1 : 2;
				return RemnantsMod.id("item/arsenal_bow_3d_pull" + stage);
			}
		}
		return model3d(mode);
	}

	/** The 3D in-hand model of one Arsenal form (also drawn by the flying spectral copies). */
	public static ResourceLocation model3d(int mode) {
		return RemnantsMod.id("item/arsenal_" + FORMS[mode] + "_3d");
	}

	/** The same stack without the enchantment glint: its purple sheen washes the 3D models' palette out (bone turns
	 * lilac, gold pink), so only the flat inventory icon keeps the legendary shimmer. */
	public static ItemStack unglinted(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		return copy;
	}

	/** Who is drawing this very stack: the hand renderers pass the holder's own stack instance, so identity is enough;
	 * the local player's first-person copy may be a different instance with the same contents. */
	@Nullable
	private static LivingEntity drawing(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.isUsingItem()) {
			ItemStack used = mc.player.getUseItem();
			if (used == stack || ItemStack.isSameItemSameComponents(used, stack)) {
				return mc.player;
			}
		}
		if (mc.level != null) {
			for (Player p : mc.level.players()) {
				if (p.isUsingItem() && p.getUseItem() == stack) {
					return p;
				}
			}
		}
		return null;
	}

	private static boolean flat(ItemDisplayContext mode) {
		return mode == ItemDisplayContext.GUI || mode == ItemDisplayContext.GROUND || mode == ItemDisplayContext.FIXED
			|| mode == ItemDisplayContext.NONE;
	}

	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Minecraft mc = Minecraft.getInstance();
		boolean flat = flat(mode);
		ResourceLocation id = (flat ? this.icon : this.model3d).apply(stack);
		BakedModel model = mc.getModelManager().getModel(id);
		if (model == null || model == mc.getModelManager().getMissingModel()) {
			return;
		}
		boolean left = mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		pose.pushPose();
		// the builtin/entity wrapper already moved to the block corner; the inner render applies its own transforms
		pose.translate(0.5F, 0.5F, 0.5F);
		mc.getItemRenderer().render(flat ? stack : unglinted(stack), mode, left, pose, buffers, light, overlay, model);
		pose.popPose();
	}
}
