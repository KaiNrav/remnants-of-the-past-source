package dev.remnants.entity.ai;

import java.util.EnumSet;

/**
 * One boss move. Every move has a readable telegraph (pose, particles, sound) before it lands, so each one can
 * be dodged, interrupted or punished by a player who pays attention. Like the reference bosses (one skill at a
 * time, a wind-up, then the model held still for a while), big moves end in a {@link #recovery} window in which
 * the Remnant stands catching its breath: that is when you hit back.
 */
public abstract class Ability {
	public final String name;
	public final int cooldown;
	/** Ticks the Remnant stands still after the move finishes. */
	protected int recovery;
	/** Lowest echo rank (1 Faint .. 4 Great, from the gear its owner died in) that knows this move. */
	protected int rank = 2;
	private final int minPhase;
	private final EnumSet<CombatStyle> styles;

	protected Ability(String name, int cooldown, int minPhase, CombatStyle... styles) {
		this.name = name;
		this.cooldown = cooldown;
		this.minPhase = minPhase;
		this.styles = styles.length == 0 ? EnumSet.allOf(CombatStyle.class) : EnumSet.of(styles[0], styles);
	}

	/** Empowered Remnants unlock every move one phase earlier; weak echoes only know the moves of their rank. */
	public boolean allows(RemnantBrain b) {
		int phase = b.phase() + (b.boss.isEmpowered() ? 1 : 0);
		return this.styles.contains(b.style()) && phase >= this.minPhase && b.boss.getEchoRank() >= this.rank;
	}

	/** How much this move fits the situation right now; at or below 0 means "not usable". */
	public abstract double score(RemnantBrain b);

	public abstract int duration(RemnantBrain b);

	public void start(RemnantBrain b) {
	}

	public abstract void tick(RemnantBrain b, int t);

	public int recovery(RemnantBrain b) {
		return this.recovery;
	}

	public void stop(RemnantBrain b) {
	}

	/** While true the brain keeps moving (but not attacking) during this move. */
	public boolean allowsMovement(int t) {
		return false;
	}
}
