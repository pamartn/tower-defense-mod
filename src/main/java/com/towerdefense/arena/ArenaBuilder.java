package com.towerdefense.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Seam for arena generation. The real implementation is {@link ArenaManager};
 * tests inject {@link #noop()} to skip expensive block-placement.
 */
@FunctionalInterface
public interface ArenaBuilder {
    void generate(ServerLevel world, BlockPos origin);

    static ArenaBuilder noop() {
        return (w, o) -> {};
    }
}
