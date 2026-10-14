package dev.remnants.entity;

/**
 * A mob whose look target is synced to clients, so the client can turn the head straight at it every frame
 * (client/anim/HeadLook) instead of trusting vanilla's head yaw: that arrives a byte per tick, lags the body by a
 * different number of ticks, and two server writers (LookControl and the mob's own facing code) fight over it.
 */
public interface LookTarget {
	/** Entity id of what the mob looks at, or -1. */
	int lookTargetId();
}
