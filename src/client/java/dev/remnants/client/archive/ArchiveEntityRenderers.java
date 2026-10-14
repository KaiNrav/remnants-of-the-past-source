package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.block.CryptEyeBlock;
import dev.remnants.client.craft.CraftFx;
import dev.remnants.entity.ArchiveEyeEntity;
import dev.remnants.entity.ArchivePageEntity;
import dev.remnants.entity.ArchiveRuneEntity;
import dev.remnants.entity.ArchiveStarEntity;
import dev.remnants.entity.FlyingTomeEntity;
import dev.remnants.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The renderers of the Archivist's fight: its Pages, flying tomes, runes, stars and the Eyes of the Archive. */
public final class ArchiveEntityRenderers {
	public static final ResourceLocation PAGE = RemnantsMod.id("textures/entity/archive_page.png");
	public static final ResourceLocation STAR = RemnantsMod.id("textures/entity/archive/star.png");
	public static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");

	/** A Page: its keyframed model. */
	public static final class Page extends MobRenderer<ArchivePageEntity, ArchivePageModel> {
		public Page(EntityRendererProvider.Context ctx) {
			super(ctx, new ArchivePageModel(ctx.bakeLayer(ArchivePageModel.LAYER)), 0.25F);
		}

		@Override
		public ResourceLocation getTextureLocation(ArchivePageEntity entity) {
			return PAGE;
		}

		@Override
		protected float getFlipDegrees(ArchivePageEntity entity) {
			return 0.0F;
		}

		@Override
		protected float getWhiteOverlayProgress(ArchivePageEntity entity, float partial) {
			return dev.remnants.client.fx.CombatFx.flash(entity, partial);
		}
	}

	/** A flying tome: the enchanting table's book, open and flapping, spinning end over end as it flies. */
	public static final class Tome extends EntityRenderer<FlyingTomeEntity> {
		private final BookModel book;

		public Tome(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.book = new BookModel(ctx.bakeLayer(ModelLayers.BOOK));
		}

		@Override
		public void render(FlyingTomeEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			float t = e.tickCount + partial;
			pose.pushPose();
			pose.translate(0, 0.25, 0);
			pose.mulPose(Axis.YP.rotationDegrees(t * (e.isReturning() ? -30.0F : 30.0F)));
			pose.mulPose(Axis.ZP.rotationDegrees(80.0F));
			float flip = Mth.frac(t * 0.08F);
			this.book.setupAnim(t, Mth.clamp(flip, 0, 1), Mth.clamp(1 - flip, 0, 1), 1.1F);
			this.book.renderToBuffer(pose, buffers.getBuffer(RenderType.entitySolid(BOOK)), light, OverlayTexture.NO_OVERLAY, -1);
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(FlyingTomeEntity entity) {
			return BOOK;
		}
	}

	/** A rune of the spiral: a glowing glyph facing the camera. */
	public static final class Rune extends EntityRenderer<ArchiveRuneEntity> {
		public Rune(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(ArchiveRuneEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			int rune = Math.floorMod(e.getId(), CraftFx.RUNE_COUNT);
			VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.RUNES));
			CraftFx.billboard(vc, pose, 0, 0.15F, 0, 0.6F, rune / 8.0F, 0, (rune + 1) / 8.0F, 1, 0xF08FF5EC, LightTexture.FULL_BRIGHT);
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(ArchiveRuneEntity entity) {
			return CraftFx.RUNES;
		}
	}

	/** A star on the floor: it grows, turns and brightens over its fuse, flickering just before it bursts. */
	public static final class Star extends EntityRenderer<ArchiveStarEntity> {
		public Star(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(ArchiveStarEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			float f = e.fuse(partial);
			float size = (float) ArchiveStarEntity.RADIUS * 2.0F * (0.35F + 0.65F * CraftFx.ease(f));
			int alpha = (int) (120 + 120 * f * (f > 0.8F ? 0.5F + 0.5F * Mth.sin((e.tickCount + partial) * 2.0F) : 1.0F));
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + partial) * 3.0F));
			VertexConsumer vc = buffers.getBuffer(CraftFx.glow(STAR));
			CraftFx.flat(vc, pose, 0, 0.02F, 0, size, 0, 0, 1, 1, alpha << 24 | 0xBFF8F2, LightTexture.FULL_BRIGHT);
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(ArchiveStarEntity entity) {
			return STAR;
		}
	}

	/** An Eye of the Archive: a Crypt Eye block hung on the wall, looking at the hall; shut when struck. */
	public static final class Eye extends EntityRenderer<ArchiveEyeEntity> {
		public Eye(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(ArchiveEyeEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			BlockState state = ModBlocks.CRYPT_EYE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(CryptEyeBlock.STRUCK, !e.isOpen());
			pose.pushPose();
			pose.translate(0, 0.45, 0);
			pose.mulPose(Axis.YP.rotationDegrees(180.0F - e.getYRot()));
			pose.scale(0.9F, 0.9F, 0.9F);
			pose.translate(-0.5, -0.5, -0.5);
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, e.isOpen() ? LightTexture.FULL_BRIGHT : light,
				OverlayTexture.NO_OVERLAY);
			pose.popPose();
			if (e.isOpen()) {
				float t = e.tickCount + partial;
				int a = (int) (60 + 40 * Mth.sin(t * 0.15F));
				CraftFx.billboard(buffers.getBuffer(CraftFx.glow(STAR)), pose, 0, 0.45F, 0, 1.6F, 0, 0, 1, 1, a << 24 | 0x6FE8E2,
					LightTexture.FULL_BRIGHT);
			}
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(ArchiveEyeEntity entity) {
			return STAR;
		}
	}

	private ArchiveEntityRenderers() {
	}
}
