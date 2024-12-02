package com.christophergammage.shopaholic;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.server.command.TextComponentHelper;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ShopaholicMod.MODID)
public class ShopaholicMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "shopaholic";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int RANGE = 150;

    private static String currentWarp = "";

    private static Screen warpsScreen;

    private static boolean exportOnEmpty = false;

    static final private Map<BlockPos, ShopChest> allChests = new HashMap<>();

    static final private Set<String> allWarps = new LinkedHashSet<>();

    static final private Deque<String> scheduledScans = new ArrayDeque<>();

    public ShopaholicMod() {
        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class EventScheduler {
        static private int ticks = 0;

        @SubscribeEvent
        public static void onClientTickEvent(TickEvent.ClientTickEvent tickEvent) {
            ticks++;
            if ((ticks % 150 == 0) && !scheduledScans.isEmpty()) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    String scan = scheduledScans.peekFirst();
                    player.sendSystemMessage(Component.literal(
                            "processing " + scan + " " + scheduledScans.size() + " remaining"));
                    if (!currentWarp.equals(scan)) {
                        currentWarp = scan;
                        player.sendSystemMessage(Component.literal("warping to " + scan));
                        runPlayerCommand(player, "warp " + scan);
                    } else {
                        String scanStr = scheduledScans.removeFirst();
                        player.sendSystemMessage(
                                Component.literal("scanning " + scan + "(" + scanStr + ")"));
                        ShopaholicScanCommand.scan(null);
                    }
                }
            }
            if (exportOnEmpty && scheduledScans.isEmpty()) {
                ExportShopsCommand.export(null);
                exportOnEmpty = false;
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class WarpDetector {
        @SubscribeEvent
        public static void screenOpen(ScreenEvent.Opening screenEvent) {
            Screen screen = screenEvent.getScreen();
            Player player = Minecraft.getInstance().player;
            if (player != null && screen.getTitle().getString().equals("Public Warps")) {
                player.sendSystemMessage(Component.literal("public warps opened"));
                readWarpsFromScreen(screen);
            }
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }

        static private void readWarpsFromScreen(Screen screen) {
            Player player = Minecraft.getInstance().player;
            if (player != null &&
                    screen.getTitle().getString().equals("Public Warps")) {
                player.sendSystemMessage(Component.literal("public warps clicked"));
                if (screen instanceof ContainerScreen chestGui) {
                    var items = chestGui.getMenu().getItems();
                    player.sendSystemMessage(Component.literal("found " + items.size() + " items"));
                    int index = 0;
                    for (var item : items) {
                        index++;
                        if (index > 45) {
                            break;
                        } // skip menu items
                        if (item.getHoverName().getSiblings().size() > 0) {
                            Component comp = item.getHoverName().getSiblings().get(0);
                            if (comp.getContents() instanceof PlainTextContents.LiteralContents lit) {
                                player.sendSystemMessage(Component.literal(
                                        "found warp " + index + " : " + lit.text()));
                                allWarps.add(lit.text());
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public static void screenClicked(ScreenEvent.MouseButtonPressed event) {
            readWarpsFromScreen(event.getScreen());
        }

        @SubscribeEvent
        public static void screenClosed(ScreenEvent.Closing screenEvent) {
            Screen screen = screenEvent.getScreen();
            Player player = Minecraft.getInstance().player;
            if (player != null && screen.getTitle().getString().equals("Public Warps")) {
                player.sendSystemMessage(Component.literal("public warps closed"));
                warpsScreen = null;
            }
        }
    }


    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientCommands {
        @SubscribeEvent
        public static void registerCommands(RegisterClientCommandsEvent event) {
            LOGGER.info("Registering client commands");
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            ShopaholicScanCommand.register(dispatcher);
            ExportShopsCommand.register(dispatcher);
            ScanAllCommand.register(dispatcher);
            CancelCommand.register(dispatcher);
        }
    }

    private static class CancelCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("cancel")
                    .executes(ShopaholicMod.CancelCommand::cancel));
        }

        public static int cancel(CommandContext<CommandSourceStack> context) {
            scheduledScans.clear();
            exportOnEmpty = false;
            return 0;
        }
    }

    private static class ScanAllCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("scan_all")
                    .executes(ScanAllCommand::scanAll));
        }

        public static int scanAll(CommandContext<CommandSourceStack> context) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        "Scanning all " + allWarps.size() + " warps"));
                scheduledScans.addAll(allWarps);
                exportOnEmpty = true;
            }
            return 0;
        }

    }

    public static void runPlayerCommand(LocalPlayer player, String command) {
        if (player != null) {
            player.connection.sendCommand(command);
            ClientCommandHandler.runCommand(command);
            //PlayerChatMessage chatMessage = PlayerChatMessage.unsigned(player.getUUID(), command);
            //CommandSourceStack stack = player.createCommandSourceStack()
            //        .withSuppressedOutput().withPermission(4);
            //stack.sendChatMessage(
            //        new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));
        }
    }

    private static class ShopaholicScanCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("scan")
                    .executes(ShopaholicScanCommand::scan));
        }

        public static int scan(CommandContext<CommandSourceStack> context) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                BlockPos playerPos = player.getOnPos();
                int count = 0;
                Level world = player.level();
                Collection<SignBlockEntity> signs =
                        Scanner.scan(world, player.getOnPos(), RANGE, ShopaholicScanCommand::isSign).stream()
                                .map(blockEntity -> (SignBlockEntity) blockEntity).toList();
                for (SignBlockEntity sign : signs) {
                    ShopChest shopChest = new ShopChest(currentWarp, sign);
                    if (shopChest.isShop()) {
                        count++;
                        allChests.put(shopChest.getBlockPos(), shopChest);
                    } else {
                        //player.sendSystemMessage(Component.literal("not a shop chest: " + shopChest.getError()));
                        //for (Component c : sign.getText(true).getMessages(true)) {
                        //    player.sendSystemMessage(Component.literal(c.getString()));
                        //}
                    }
                }
                player.sendSystemMessage(
                        Component.literal("Scanned " + count + " chests for warp " + currentWarp));
            }
            return 0;
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

    private static class ExportShopsCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(
                    Commands.literal("export")
                            .executes(ExportShopsCommand::export));
        }

        public static int export(CommandContext<CommandSourceStack> context) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                CsvWriter.writeShops(player, allChests);
            }
            return 1; // Success
        }
    }
}
