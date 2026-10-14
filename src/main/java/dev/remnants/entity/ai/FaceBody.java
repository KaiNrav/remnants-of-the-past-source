package dev.remnants.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * Body yaw = the mob's yaw, on both sides. For mobs whose own code turns them to face what they fight (setYRot every
 * tick): vanilla's control lets the body trail up to 75 degrees behind the head and only catch up after the head has
 * been still for half a second, and on the client it does that from lagged yaw packets, so the model faced one way
 * while the server (and every hit check) faced another. The head is left alone: client/anim/HeadLook aims it.
 */
public final class FaceBody extends BodyRotationControl {
	private final Mob mob;

	public FaceBody(Mob mob) {
		super(mob);
		this.mob = mob;
	}

	/** Most a body turns in a tick on the client: a snap (an attack's first tick, a blink) becomes a quick, smooth turn. */
	private static final float CLIENT_TURN = 20.0F;

	@Override
	public void clientTick() {
		if (this.mob.level().isClientSide) {
			this.mob.yBodyRot = net.minecraft.util.Mth.approachDegrees(this.mob.yBodyRot, this.mob.getYRot(), CLIENT_TURN);
		} else {
			this.mob.yBodyRot = this.mob.getYRot();
		}
	}
}
