package com.christophergammage.shopaholic;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ShopaholicMod.MODID)
public class ShopaholicMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "shopaholic";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean isScannerEnabled = false;

    private static String currentWarp = "";
    private static Map<String, Map<BlockPos, ShopChest>> shopChests = new HashMap<>();

    public ShopaholicMod() {
        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientCommands {
        @SubscribeEvent
        public static void registerCommands(RegisterClientCommandsEvent event) {
            LOGGER.info("Registering client commands");
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            ToggleShopaholicCommand.register(dispatcher);
            SetShopWarpCommand.register(dispatcher);
            ExportShopsCommand.register(dispatcher);
        }
    }

    private static class SetShopWarpCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(
                    Commands.literal("shopaholic_warp_name")
                            .then(Commands.argument("warpName", StringArgumentType.greedyString())
                            .executes(SetShopWarpCommand::setShopWarp)));
        }

        private static int setShopWarp(CommandContext<CommandSourceStack> context) {
            currentWarp = StringArgumentType.getString(context, "warpName");
            String message = "Shop warp set to : " + currentWarp;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
            return 1; // Success
        }
    }

    private static class ExportShopsCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(
                    Commands.literal("shopaholic_export")
                            .executes(ExportShopsCommand::export));
        }

        public static int export(CommandContext<CommandSourceStack> context) {
            String fileName = "shops.csv";
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return 0;
            }
            File gameDir = Minecraft.getInstance().gameDirectory;
            Path path = Paths.get(gameDir.toString(), fileName);
            try {
                if (!Files.exists(path)) {
                    Files.createFile(path);
                    try (FileWriter writer = new FileWriter(path.toFile(), true)) {
                        String message = "Exporting shops";
                        player.sendSystemMessage(Component.literal(message));
                        int count = 0;
                        writer.append("Warp, Owner, State, Quantity, Item, Price\n");
                        for (var entry : shopChests.entrySet()) {
                            String warp = entry.getKey();
                            for (ShopChest shop : entry.getValue().values()) {
                                ++count;
                                writer.append(shop.toCsv(warp));
                            }
                        }
                        message = "Exported " + count + " shops";
                        player.sendSystemMessage(Component.literal(message));
                    } catch (Exception e) {
                        String message = "error: " + e;
                        player.sendSystemMessage(Component.literal(message));
                        return 0;
                    }
                } else {
                    String message = "file already exists";
                    player.sendSystemMessage(Component.literal(message));
                }
            } catch (Exception e) {
                String message = "error: " + e;
                player.sendSystemMessage(Component.literal(message));
                return 0;
            }
            return 1; // Success
        }
    }

    private static class ToggleShopaholicCommand {

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("shopaholic_toggle")
                    .executes(ToggleShopaholicCommand::toggleScanner));
        }

        private static int toggleScanner(CommandContext<CommandSourceStack> context) {
            isScannerEnabled = !isScannerEnabled;
            String message = isScannerEnabled ? "Sign scanner enabled!" : "Sign scanner disabled";
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
            return 1; // Success
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientSignDetection {

        private static BlockPos lastScanPos = null;

        @SubscribeEvent
        public static void move(PlayerEvent playerEvent) {
            if (!isScannerEnabled) {
               return;
            }
            Player player = playerEvent.getEntity();
            Player localPlayer = Minecraft.getInstance().player;
            if (player != null) {
                if (localPlayer != player) {
                    return;
                }
                BlockPos playerPos = player.getOnPos();
                if (!playerPos.equals(lastScanPos)) {
                    int count = 0;
                    lastScanPos = playerPos;
                    Level world = player.level();
                    Collection<SignBlockEntity> signs =
                            Scanner.scan(world, player.getOnPos(), 50, ClientSignDetection::isSign).stream()
                                    .map(blockEntity -> (SignBlockEntity)blockEntity).toList();
                    for (SignBlockEntity sign : signs) {
                        ShopChest shopChest = new ShopChest(sign);
                        if (shopChest.isShop()) {
                            count++;
                            shopChests.computeIfAbsent(currentWarp, k -> new HashMap<>());
                            shopChests.get(currentWarp).put(sign.getBlockPos(), new ShopChest(sign));
                        } else {
                            localPlayer.sendSystemMessage(Component.literal("not a shop chest"));
                            for (Component c : sign.getText(true).getMessages(true)) {
                                localPlayer.sendSystemMessage(Component.literal(c.getString()));
                            }
                        }
                    }
                    localPlayer.sendSystemMessage(Component.literal("found " + count + " chest shops"));
                }
            }
        }

        static boolean isSign(Block block) {
            return block == Blocks.OAK_WALL_SIGN
                    || block == Blocks.SPRUCE_WALL_SIGN
                    || block == Blocks.BIRCH_WALL_SIGN
                    || block == Blocks.ACACIA_WALL_SIGN
                    || block == Blocks.DARK_OAK_WALL_SIGN
                    || block == Blocks.CRIMSON_WALL_SIGN
                    || block == Blocks.WARPED_WALL_SIGN;
        }
    }
}
