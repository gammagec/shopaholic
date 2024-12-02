package com.christophergammage.shopaholic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class CsvWriter {
    static public void writeShops(Player player, Map<BlockPos, ShopChest> shops) {
        String fileName = "shops.csv";
        if (player == null) {
            return;
        }
        File gameDir = Minecraft.getInstance().gameDirectory;
        Path path = Paths.get(gameDir.toString(), fileName);
        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }
            try (FileWriter writer = new FileWriter(path.toFile(), true)) {
                String message = "Exporting shops";
                player.sendSystemMessage(Component.literal(message));
                int count = 0;
                writer.append("Warp, Owner, BlockPos, State, Quantity, Item, Price\n");
                for (var shop : shops.values()) {
                    ++count;
                    writer.append(shop.toCsv());
                }
                message = "Exported " + count + " shops";
                player.sendSystemMessage(Component.literal(message));
            } catch (Exception e) {
                String message = "error: " + e;
                player.sendSystemMessage(Component.literal(message));
                return;
            }
        } catch (Exception e) {
            String message = "error: " + e;
            player.sendSystemMessage(Component.literal(message));
            return;
        }
        return;
    }
}
