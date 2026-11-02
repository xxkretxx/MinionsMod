package dev.buildminions.minion;

public enum MinionPhase {
    /** No blueprint loaded. */
    IDLE,
    /** Blueprint loaded, waiting for materials / acceptance. */
    COLLECTING,
    /** Build accepted, the owner is positioning the hologram. */
    AWAITING_PLACEMENT,
    /** Flying around placing blocks. */
    BUILDING,
    /** Finished the last job. */
    DONE,
    /** Durability exhausted – playing the break animation, then pops. */
    BREAKING,
    /** Working at a Minion Workstation through its crafting queue. */
    CRAFTING;

    public static MinionPhase byId(int id) {
        MinionPhase[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, id))];
    }

    public boolean acceptsBlueprint() {
        return this == IDLE || this == COLLECTING || this == DONE;
    }
}
