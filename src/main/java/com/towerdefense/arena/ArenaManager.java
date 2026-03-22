package com.towerdefense.arena;

import com.towerdefense.game.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

public class ArenaManager implements ArenaBuilder {

    private static int size() { return GameConfig.ARENA_SIZE(); }
    private static int depth() { return GameConfig.STAND_DEPTH(); }

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private static void place(ServerLevel w, BlockPos p, Block b) {
        w.setBlock(p, b.defaultBlockState(), FLAGS);
    }

    private static void place(ServerLevel w, BlockPos p, BlockState s) {
        w.setBlock(p, s, FLAGS);
    }

    // ──────────────────────────────────────────
    //  Main entry
    // ──────────────────────────────────────────

    public void generate(ServerLevel world, BlockPos origin) {
        GameConfig.arenaOrigin = origin;

        clearVolume(world, origin);
        generateFloor(world, origin);
        generateGeneratorSpots(world, origin);
        generateInnerWall(world, origin);
        generateStands(world, origin);
        generateTopRing(world, origin);
        generateTorches(world, origin);
        generateVelarium(world, origin);
        generateEntrances(world, origin);
        generateDecorations(world, origin);
    }

    private void clearVolume(ServerLevel world, BlockPos origin) {
        int margin = depth() + 4;
        for (int x = -margin; x <= size() + margin; x++) {
            for (int z = -margin; z <= size() + margin; z++) {
                place(world, origin.offset(x, -1, z), Blocks.BEDROCK);
                for (int dy = 0; dy <= GameConfig.STAND_HEIGHT() + 15; dy++) {
                    place(world, origin.offset(x, dy, z), Blocks.AIR);
                }
            }
        }
    }

    // ──────────────────────────────────────────
    //  Floor
    // ──────────────────────────────────────────

    private void generateFloor(ServerLevel world, BlockPos origin) {
        int midZ = GameConfig.getMidlineZ();

        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                BlockPos p = origin.offset(x, 0, z);
                boolean isBorder = x <= 1 || x >= size() - 2 || z <= 1 || z >= size() - 2;
                boolean isCornerAccent = (x <= 3 && z <= 3) || (x <= 3 && z >= size() - 4)
                        || (x >= size() - 4 && z <= 3) || (x >= size() - 4 && z >= size() - 4);

                boolean isLight = !isBorder && (x % 6 == 3) && (z % 6 == 3);

                if (z == midZ - 1 || z == midZ) {
                    place(world, p, Blocks.GOLD_BLOCK);
                } else if (isLight) {
                    place(world, p, Blocks.SEA_LANTERN);
                } else if (isCornerAccent) {
                    place(world, p, Blocks.CUT_SANDSTONE);
                } else if (isBorder) {
                    place(world, p, Blocks.SMOOTH_SANDSTONE);
                } else {
                    place(world, p, Blocks.SMOOTH_SANDSTONE);
                }
            }
        }

    }

    // ──────────────────────────────────────────
    //  Generator spots (glowstone pads)
    // ──────────────────────────────────────────

    /**
     * Places designated glowstone generator pads for each team.
     * Income generators can only be placed on these spots.
     * Layout per team:
     *   - 2 spots near the nexus (safe)
     *   - 2 spots on the sides of the arena (moderate)
     *   - 1 spot in the middle of each half (risky, close to midline)
     */
    private void generateGeneratorSpots(ServerLevel world, BlockPos origin) {
        int s = size();
        int mid = GameConfig.getMidlineZ();
        int cx = s / 2;

        // Team 1 spots (z = 0..mid-1), nexus at z=2
        int[][] t1 = {
            { cx - 4, 4  },   // near nexus left
            { cx + 4, 4  },   // near nexus right
            { 4,      s / 8 },  // side left
            { s - 5,  s / 8 },  // side right
            { cx,     mid - 5 } // risky center
        };

        // Team 2 spots (z = mid..s-1), nexus at z=s-3 — mirrored
        int[][] t2 = {
            { cx - 4, s - 5  },
            { cx + 4, s - 5  },
            { 4,      s - s / 8 - 1 },
            { s - 5,  s - s / 8 - 1 },
            { cx,     mid + 4 }
        };

        for (int[] xz : t1) placeGeneratorPad(world, origin, xz[0], xz[1]);
        for (int[] xz : t2) placeGeneratorPad(world, origin, xz[0], xz[1]);
    }

    /** Places a glowstone cross (+) to mark an income generator pad. */
    private void placeGeneratorPad(ServerLevel world, BlockPos origin, int x, int z) {
        place(world, origin.offset(x,     0, z    ), Blocks.GLOWSTONE);
        place(world, origin.offset(x + 1, 0, z    ), Blocks.GLOWSTONE);
        place(world, origin.offset(x - 1, 0, z    ), Blocks.GLOWSTONE);
        place(world, origin.offset(x,     0, z + 1), Blocks.GLOWSTONE);
        place(world, origin.offset(x,     0, z - 1), Blocks.GLOWSTONE);
    }

    // ──────────────────────────────────────────
    //  Inner wall with arches
    // ──────────────────────────────────────────

    private void generateInnerWall(ServerLevel world, BlockPos origin) {
        int wallH = ArenaConstants.INNER_WALL_HEIGHT;
        int archSpacing = ArenaConstants.ARCH_SPACING;
        int archWidth = ArenaConstants.ARCH_WIDTH;

        for (int i = 0; i < 4; i++) {
            boolean isXWall = (i == 0 || i == 1);
            int length = size() + 2;

            for (int t = 0; t < length; t++) {
                int[] off = offsetForSide(i, t - 1, 0);
                int wx = off[0], wz = off[1];

                boolean inArch = false;
                int posInWall = t;
                int archCenter = archSpacing / 2;
                int relToArch = ((posInWall - archCenter) % archSpacing + archSpacing) % archSpacing;
                int distFromArchCenter = Math.abs(relToArch - archSpacing / 2);

                if (distFromArchCenter <= archWidth / 2 && posInWall >= ArenaConstants.ARCH_EDGE_MARGIN && posInWall < length - ArenaConstants.ARCH_EDGE_MARGIN) {
                    inArch = true;
                }

                BlockPos base = origin.offset(wx, 0, wz);

                place(world, base, Blocks.BEDROCK);

                for (int dy = 1; dy <= wallH; dy++) {
                    BlockPos bp = base.above(dy);
                    if (inArch && dy <= 3) {
                        if (dy == 3) {
                            place(world, bp, stairTop(isXWall ? Direction.SOUTH : Direction.EAST));
                        } else {
                            place(world, bp, Blocks.AIR);
                        }
                    } else {
                        if (dy == wallH) {
                            place(world, bp, Blocks.CUT_SANDSTONE);
                        } else if (dy == wallH - 1) {
                            place(world, bp, Blocks.CHISELED_SANDSTONE);
                        } else {
                            place(world, bp, Blocks.SMOOTH_SANDSTONE);
                        }
                    }
                }

                boolean isColumn = !inArch && (relToArch == 0 || relToArch == archSpacing - 1
                        || posInWall == 0 || posInWall == length - 1);
                if (isColumn) {
                    for (int dy = 1; dy <= wallH; dy++) {
                        place(world, base.above(dy), Blocks.QUARTZ_PILLAR);
                    }
                    place(world, base.above(wallH + 1), Blocks.SMOOTH_SANDSTONE_SLAB);
                }
            }
        }
    }

    // ──────────────────────────────────────────
    //  Tiered stands
    // ──────────────────────────────────────────

    private void generateStands(ServerLevel world, BlockPos origin) {
        for (int row = 0; row < ArenaConstants.STAND_ROWS; row++) {
            int seatY = ArenaConstants.INNER_WALL_HEIGHT + 1 + row;
            int outward = ArenaConstants.RING_BASE_OFFSET + row;

            for (int side = 0; side < 4; side++) {
                buildBleacherRow(world, origin, side, outward, seatY);
            }
        }
    }

    private void buildBleacherRow(ServerLevel world, BlockPos origin, int side, int outward, int seatY) {
        int len = size() + 2 * outward;

        for (int t = 0; t < len; t++) {
            int[] off = offsetForSide(side, t, outward);
            int wx = off[0], wz = off[1];

            BlockPos base = origin.offset(wx, 0, wz);
            place(world, base, Blocks.BEDROCK);

            for (int fill = 1; fill < seatY; fill++) {
                BlockPos fp = origin.offset(wx, fill, wz);
                if (world.getBlockState(fp).isAir()) {
                    place(world, fp, Blocks.SANDSTONE);
                }
            }

            Direction facing = switch (side) {
                case 0 -> Direction.SOUTH;
                case 1 -> Direction.NORTH;
                case 2 -> Direction.EAST;
                default -> Direction.WEST;
            };
            place(world, origin.offset(wx, seatY, wz), stairBottom(facing));
        }
    }

    // ──────────────────────────────────────────
    //  Top ring: parapet with merlons
    // ──────────────────────────────────────────

    private void generateTopRing(ServerLevel world, BlockPos origin) {
        int topY = ArenaConstants.STAND_TOP_Y;
        int ringOffset = ArenaConstants.STAND_RING_OFFSET;

        for (int side = 0; side < 4; side++) {
            int len = size() + 2 * ringOffset;

            for (int t = 0; t < len; t++) {
                int[] off = offsetForSide(side, t, ringOffset);
                int wx = off[0], wz = off[1];

                BlockPos base = origin.offset(wx, 0, wz);
                place(world, base, Blocks.BEDROCK);
                for (int fill = 1; fill < topY; fill++) {
                    BlockPos fp = origin.offset(wx, fill, wz);
                    if (world.getBlockState(fp).isAir()) {
                        place(world, fp, Blocks.SANDSTONE);
                    }
                }

                place(world, origin.offset(wx, topY, wz), Blocks.SMOOTH_SANDSTONE);

                boolean isColumnPos = (t % ArenaConstants.COLUMN_SPACING == 0);
                boolean isMerlon = (t % ArenaConstants.MERLON_SPACING == 0) && !isColumnPos;

                if (isColumnPos) {
                    for (int cy = 1; cy <= ArenaConstants.PARAPET_PILLAR_HEIGHT; cy++) {
                        place(world, origin.offset(wx, topY + cy, wz), Blocks.QUARTZ_PILLAR);
                    }
                    place(world, origin.offset(wx, topY + ArenaConstants.PARAPET_PILLAR_HEIGHT + 1, wz), Blocks.SMOOTH_SANDSTONE_SLAB);
                } else if (isMerlon) {
                    place(world, origin.offset(wx, topY + 1, wz), Blocks.SANDSTONE_WALL);
                    place(world, origin.offset(wx, topY + 2, wz), Blocks.SANDSTONE_WALL);
                } else {
                    place(world, origin.offset(wx, topY + 1, wz), Blocks.SANDSTONE_WALL);
                }
            }
        }

        generateCornerTowers(world, origin, topY, ringOffset);
    }

    private void generateCornerTowers(ServerLevel world, BlockPos origin, int topY, int ringOffset) {
        int[][] corners = {
            { -ringOffset - 1, -ringOffset - 1 },
            { -ringOffset - 1, size() + ringOffset },
            { size() + ringOffset, -ringOffset - 1 },
            { size() + ringOffset, size() + ringOffset }
        };

        for (int[] c : corners) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 2; dz++) {
                    int wx = c[0] + dx;
                    int wz = c[1] + dz;

                    place(world, origin.offset(wx, 0, wz), Blocks.BEDROCK);
                    for (int dy = 1; dy <= topY + ArenaConstants.LANTERN_ABOVE_TOP; dy++) {
                        boolean isEdge = dx == 0 || dx == 2 || dz == 0 || dz == 2;
                        if (isEdge) {
                            place(world, origin.offset(wx, dy, wz), Blocks.SMOOTH_SANDSTONE);
                        } else {
                            place(world, origin.offset(wx, dy, wz), Blocks.SANDSTONE);
                        }
                    }

                    place(world, origin.offset(wx, topY + 7, wz), Blocks.CUT_SANDSTONE);

                    boolean isCornerEdge = (dx == 0 || dx == 2) && (dz == 0 || dz == 2);
                    if (isCornerEdge) {
                        place(world, origin.offset(wx, topY + 8, wz), Blocks.SANDSTONE_WALL);
                    }
                }
            }

            place(world, origin.offset(c[0] + 1, topY + 8, c[1] + 1), Blocks.LANTERN);
        }
    }

    // ──────────────────────────────────────────
    //  Torches along the stands
    // ──────────────────────────────────────────

    private void generateTorches(ServerLevel world, BlockPos origin) {
        for (int row = 0; row < ArenaConstants.STAND_ROWS; row += 2) {
            int seatY = ArenaConstants.INNER_WALL_HEIGHT + 1 + row;
            int outward = ArenaConstants.RING_BASE_OFFSET + row;

            for (int side = 0; side < 4; side++) {
                int len = size() + 2 * outward;
                for (int t = 0; t < len; t += ArenaConstants.TORCH_SPACING) {
                    int[] off = offsetForSide(side, t, outward);
                    int wx = off[0], wz = off[1];
                    BlockPos torchBase = origin.offset(wx, seatY + 1, wz);
                    place(world, torchBase, Blocks.OAK_FENCE);
                    place(world, torchBase.above(), Blocks.TORCH);
                }
            }
        }
    }

    // ──────────────────────────────────────────
    //  Velarium (Roman canopy)
    // ──────────────────────────────────────────

    private void generateVelarium(ServerLevel world, BlockPos origin) {
        int topY = ArenaConstants.STAND_TOP_Y;
        int canopyY = topY + ArenaConstants.CANOPY_ABOVE_TOP;
        int ringOffset = ArenaConstants.STAND_RING_OFFSET;
        int canopyDepth = ArenaConstants.CANOPY_DEPTH;

        for (int side = 0; side < 4; side++) {
            int len = size() + 2 * ringOffset;
            for (int t = 0; t < len; t++) {
                for (int d = 0; d < canopyDepth; d++) {
                    int wx, wz;
                    switch (side) {
                        case 0 -> { wx = t - ringOffset; wz = -ringOffset + d; }
                        case 1 -> { wx = t - ringOffset; wz = size() + ringOffset - d; }
                        case 2 -> { wx = -ringOffset + d; wz = t - ringOffset; }
                        default -> { wx = size() + ringOffset - d; wz = t - ringOffset; }
                    }
                    place(world, origin.offset(wx, canopyY, wz), Blocks.RED_WOOL);
                }
            }
        }

        for (int side = 0; side < 4; side++) {
            int len = size() + 2 * ringOffset;
            for (int t = 0; t < len; t += ArenaConstants.VELARIUM_FENCE_SPACING) {
                // Fence poles sit at the outer edge (outward = ringOffset - 1 in the standard formula,
                // but the wz/wx for case 0 is -ringOffset not -1-ringOffset, so we build it inline).
                int wx, wz;
                switch (side) {
                    case 0 -> { wx = t - ringOffset; wz = -ringOffset; }
                    case 1 -> { wx = t - ringOffset; wz = size() + ringOffset; }
                    case 2 -> { wx = -ringOffset; wz = t - ringOffset; }
                    default -> { wx = size() + ringOffset; wz = t - ringOffset; }
                }
                for (int ry = topY + 1; ry < canopyY; ry++) {
                    place(world, origin.offset(wx, ry, wz), Blocks.OAK_FENCE);
                }
            }
        }
    }

    // ──────────────────────────────────────────
    //  Grand entrances (N, S, E, W)
    // ──────────────────────────────────────────

    private void generateEntrances(ServerLevel world, BlockPos origin) {
        int midX = size() / 2;
        int midZ = size() / 2;
        int topY = ArenaConstants.ENTRANCE_TOP_Y;
        int totalDepth = ArenaConstants.RING_BASE_OFFSET + ArenaConstants.ENTRANCE_TIERS * ArenaConstants.ENTRANCE_TIER_DEPTH;
        int gateWidth = ArenaConstants.GATE_WIDTH;
        int gateHeight = topY - 2;

        int[][] gateConfigs = {
            { midX, 0, 0, -1 },  // north: centered on X, cuts through -Z side
            { midX, 0, 1,  1 },  // south: centered on X, cuts through +Z side
            { 0, midZ, -1, 0 },  // west: centered on Z, cuts through -X side
            { 0, midZ,  1, 0 },  // east: centered on Z, cuts through +X side
        };

        for (int[] gc : gateConfigs) {
            int centerA = gc[0] + gc[1];
            int dirX = gc[2];
            int dirZ = gc[3];

            boolean isNS = dirZ != 0;

            for (int a = -gateWidth / 2; a <= gateWidth / 2; a++) {
                int zStart = isNS ? (dirZ < 0 ? -1 : size()) : 0;
                int xStart = isNS ? 0 : (dirX < 0 ? -1 : size());

                for (int d = 0; d <= totalDepth + 2; d++) {
                    int wx, wz;
                    if (isNS) {
                        wx = centerA + a;
                        wz = zStart + dirZ * -d;
                    } else {
                        wz = centerA + a;
                        wx = xStart + dirX * -d;
                    }

                    for (int dy = 1; dy <= gateHeight; dy++) {
                        BlockPos bp = origin.offset(wx, dy, wz);
                        if (!world.getBlockState(bp).isAir()) {
                            place(world, bp, Blocks.AIR);
                        }
                    }
                }
            }

            for (int pillarSide = -1; pillarSide <= 1; pillarSide += 2) {
                int colA = centerA + pillarSide * (gateWidth / 2 + 1);

                for (int d = 0; d <= totalDepth + 2; d++) {
                    int wx, wz;
                    if (isNS) {
                        wx = colA;
                        wz = zStart(isNS, dirZ, dirX) + (isNS ? dirZ : dirX) * -d;
                    } else {
                        wz = colA;
                        wx = zStart(isNS, dirZ, dirX) + (isNS ? dirZ : dirX) * -d;
                    }

                    if (d == 0 || d == totalDepth + 2) {
                        for (int dy = 1; dy <= gateHeight + 2; dy++) {
                            place(world, origin.offset(wx, dy, wz), Blocks.QUARTZ_PILLAR);
                        }
                        place(world, origin.offset(wx, gateHeight + 3, wz), Blocks.CHISELED_SANDSTONE);
                    }
                }
            }

            for (int a = -gateWidth / 2 - 1; a <= gateWidth / 2 + 1; a++) {
                int wx, wz;
                if (isNS) {
                    wx = centerA + a;
                    wz = dirZ < 0 ? -1 : size();
                } else {
                    wz = centerA + a;
                    wx = dirX < 0 ? -1 : size();
                }

                for (int dy = gateHeight + 1; dy <= gateHeight + 3; dy++) {
                    place(world, origin.offset(wx, dy, wz), Blocks.CUT_SANDSTONE);
                }
            }
        }
    }

    private int zStart(boolean isNS, int dirZ, int dirX) {
        if (isNS) return dirZ < 0 ? -1 : size();
        return dirX < 0 ? -1 : size();
    }

    // ──────────────────────────────────────────
    //  Decorations
    // ──────────────────────────────────────────

    private void generateDecorations(ServerLevel world, BlockPos origin) {
        int topY = ArenaConstants.ENTRANCE_TOP_Y;

        placeIronBarRailings(world, origin);
        placeLanterns(world, origin, topY);
        placeVipBoxes(world, origin, topY);
        placeBanners(world, origin, ArenaConstants.INNER_WALL_HEIGHT);
    }

    private void placeIronBarRailings(ServerLevel world, BlockPos origin) {
        int railY = ArenaConstants.RAIL_HEIGHT;

        for (int x = -1; x <= size(); x++) {
            place(world, origin.offset(x, railY, -1), Blocks.IRON_BARS);
            place(world, origin.offset(x, railY, size()), Blocks.IRON_BARS);
        }
        for (int z = -1; z <= size(); z++) {
            place(world, origin.offset(-1, railY, z), Blocks.IRON_BARS);
            place(world, origin.offset(size(), railY, z), Blocks.IRON_BARS);
        }
    }

    private void placeLanterns(ServerLevel world, BlockPos origin, int topY) {
        // Lanterns sit at the outer edge of the entrance structure
        // (totalDepth = RING_BASE_OFFSET + ENTRANCE_TIERS * ENTRANCE_TIER_DEPTH = 14)
        int ringOffset = ArenaConstants.RING_BASE_OFFSET + ArenaConstants.ENTRANCE_TIERS * ArenaConstants.ENTRANCE_TIER_DEPTH;

        for (int side = 0; side < 4; side++) {
            int len = size() + 2 * ringOffset;
            for (int t = 0; t < len; t++) {
                if (t % ArenaConstants.COLUMN_SPACING != 0) continue;

                int[] off = offsetForSide(side, t, ringOffset);
                int wx = off[0], wz = off[1];

                place(world, origin.offset(wx, topY + ArenaConstants.LANTERN_ABOVE_TOP, wz), Blocks.LANTERN);
            }
        }
    }

    private void placeVipBoxes(ServerLevel world, BlockPos origin, int topY) {
        int midX = size() / 2;
        int vipY = topY - 2;
        int vipDepth = 2 + 4 * 3;

        int[][] vipCenters = {
            { midX, -vipDepth + 3 },
            { midX, size() + vipDepth - 4 },
        };

        for (int[] vc : vipCenters) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int wx = vc[0] + dx;
                    int wz = vc[1] + dz;
                    place(world, origin.offset(wx, vipY, wz), Blocks.RED_WOOL);
                    place(world, origin.offset(wx, vipY + 1, wz), Blocks.AIR);
                    place(world, origin.offset(wx, vipY + 2, wz), Blocks.AIR);
                }
            }

            for (int dx = -3; dx <= 3; dx++) {
                place(world, origin.offset(vc[0] + dx, vipY + 1, vc[1] - 2), Blocks.SANDSTONE_WALL);
                place(world, origin.offset(vc[0] + dx, vipY + 1, vc[1] + 2), Blocks.SANDSTONE_WALL);
            }

            place(world, origin.offset(vc[0] - 4, vipY + 1, vc[1]), Blocks.QUARTZ_PILLAR);
            place(world, origin.offset(vc[0] - 4, vipY + 2, vc[1]), Blocks.QUARTZ_PILLAR);
            place(world, origin.offset(vc[0] - 4, vipY + 3, vc[1]), Blocks.CHISELED_SANDSTONE);
            place(world, origin.offset(vc[0] + 4, vipY + 1, vc[1]), Blocks.QUARTZ_PILLAR);
            place(world, origin.offset(vc[0] + 4, vipY + 2, vc[1]), Blocks.QUARTZ_PILLAR);
            place(world, origin.offset(vc[0] + 4, vipY + 3, vc[1]), Blocks.CHISELED_SANDSTONE);
        }
    }

    private void placeBanners(ServerLevel world, BlockPos origin, int wallH) {
        int bannerY = wallH + 1;

        for (int side = 0; side < 4; side++) {
            int len = size() + 2;
            for (int t = 0; t < len; t++) {
                if (t % ArenaConstants.BANNER_SPACING != 0) continue;

                int wx, wz;
                Direction facing;
                switch (side) {
                    case 0 -> { wx = t - 1; wz = 0; facing = Direction.SOUTH; }
                    case 1 -> { wx = t - 1; wz = size() - 1; facing = Direction.NORTH; }
                    case 2 -> { wx = 0; wz = t - 1; facing = Direction.EAST; }
                    default -> { wx = size() - 1; wz = t - 1; facing = Direction.WEST; }
                }

                BlockState banner = Blocks.RED_WALL_BANNER.defaultBlockState()
                        .setValue(WallBannerBlock.FACING, facing);
                place(world, origin.offset(wx, bannerY, wz), banner);
            }
        }
    }

    // ──────────────────────────────────────────
    //  Side-offset helper
    // ──────────────────────────────────────────

    /**
     * Returns {@code [wx, wz]} for the given arena side and along-side position.
     * The four sides are numbered: 0 = north, 1 = south, 2 = west, 3 = east.
     *
     * @param side    side index (0–3)
     * @param t       position along the side (0-based)
     * @param outward outward distance from the arena edge (0 = flush with inner wall)
     */
    private int[] offsetForSide(int side, int t, int outward) {
        return switch (side) {
            case 0 -> new int[]{ t - outward,       -1 - outward      }; // north
            case 1 -> new int[]{ t - outward,        size() + outward  }; // south
            case 2 -> new int[]{ -1 - outward,       t - outward       }; // west
            default -> new int[]{ size() + outward,  t - outward       }; // east
        };
    }

    // ──────────────────────────────────────────
    //  Stair helpers
    // ──────────────────────────────────────────

    private static BlockState stairBottom(Direction facing) {
        return Blocks.SANDSTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    private static BlockState stairTop(Direction facing) {
        return Blocks.SANDSTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    // ──────────────────────────────────────────
    //  Clear arena (player-placed blocks only)
    // ──────────────────────────────────────────

    public void clearArena(ServerLevel world) {
        BlockPos origin = GameConfig.arenaOrigin;

        for (int x = 0; x < size(); x++) {
            for (int z = 0; z < size(); z++) {
                for (int dy = 1; dy <= 25; dy++) {
                    BlockPos p = origin.offset(x, dy, z);
                    if (!world.getBlockState(p).isAir()) {
                        world.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────
    //  Player confinement
    // ──────────────────────────────────────────

    private void confineToHalf(Entity entity, int side) {
        BlockPos pos = entity.blockPosition();
        if (!GameConfig.isInsideArena(pos) || !GameConfig.isInsidePlayerHalf(pos, side)) {
            BlockPos spawn = side == 1 ? GameConfig.getPlayer1SpawnPoint() : GameConfig.getPlayer2SpawnPoint();
            entity.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }
    }

    public void confinePlayer(ServerPlayer player, int side) {
        confineToHalf(player, side);
    }

    /** Confine entity (e.g. AI villager) to team half. */
    public void confineEntity(Entity entity, int side) {
        confineToHalf(entity, side);
    }
}
