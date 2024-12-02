package com.christophergammage.shopaholic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class Scanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    static public Collection<BlockEntity> scan(Level world, BlockPos startPos, int radius, Predicate<Block> filter) {
        List<BlockEntity> results = new ArrayList<>();
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                for (int z = -radius; z <= radius; ++z) {
                    BlockPos pos = startPos.offset(x, y, z);
                    if (filter.test(world.getBlockState(pos).getBlock())) {
                        //BlockEntity above = world.getBlockEntity(pos.offset(0, 1, 0));
                        //LOGGER.info("block above is {}\n", above.getBlockState().getBlock().getName());
                        results.add(world.getBlockEntity(pos));
                    }
                }
            }
        }
        return results;
    }
}
