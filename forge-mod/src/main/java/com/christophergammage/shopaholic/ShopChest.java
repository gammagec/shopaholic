package com.christophergammage.shopaholic;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.slf4j.Logger;

import java.util.Optional;

public class ShopChest {
    private BlockPos blockPos;
    private String owner;
    private boolean valid = true;

    private static final Logger LOGGER = LogUtils.getLogger();

    enum ShopState {
        SELLING("selling"),
        BUYING("buying"),
        OUT_OF_STOCK("out of stock"),
        OUT_OF_SPACE("out of space");
        final String text;

        ShopState(String text) {
            this.text = text;
        }

        public String toString() {
            return text;
        }
    }

    private ShopState shopState = null;
    private String item = null;
    private String id = null;
    double price = 0;

    private String error = "";

    private int quantity = 0;

    String warp;

    public ShopChest(String warp, SignBlockEntity sign) {
        this.warp = warp;
        this.blockPos = sign.getBlockPos();
        Component[] messages = sign.getText(true).getMessages(true);
        if (messages.length < 4) {
            valid = false;
            error = "only " + messages.length + " messages";
            return;
        }
        this.owner = messages[0].getString();
        String state = messages[1].getString();
        if (state.equals("Out of Stock")) {
            shopState = ShopState.OUT_OF_STOCK;
        } else if (state.startsWith("Out of Space")) {
            shopState = ShopState.OUT_OF_SPACE;
        } else if (state.startsWith("Selling")) {
            shopState = ShopState.SELLING;
            String[] split = state.split(" ");
            if (split.length < 2) {
                valid = false;
                error = "selling split len " + split.length;
                return;
            }
            try {
                quantity = Integer.parseInt(split[1]);
            } catch (Exception e) {
                valid = false;
                error = "exception on quantity: " + e;
                return;
            }
        } else if (state.startsWith("Buying")) {
            shopState = ShopState.BUYING;
            String[] split = state.split(" ");
            if (split.length < 2) {
                valid = false;
                error = "buy split len: " + split.length;
                return;
            }
            try {
                quantity = Integer.parseInt(split[1]);
            } catch (Exception e) {
                valid = false;
                error = "buy quantity exception: " + e;
                return;
            }
        } else {
            valid = false;
            error = "unknown state: " + state;
            return;
        }
        item = messages[2].getString();
        if (messages[2].getSiblings().size() > 0) {
            var contents = messages[2].getSiblings().get(0).getContents();
            if (contents instanceof TranslatableContents) {
                id = ((TranslatableContents) contents).getKey();
            } else {
                id = "custom";
            }
        } else {
            valid = false;
            error = "get id: msg siblings sz 0";
            return;
        }
        String priceStr = messages[3].getString().replaceAll(",", "");
        if (!priceStr.startsWith("$")) {
            valid = false;
            error = "price doesn't start with $";
            return;
        }
        if (priceStr.endsWith("each")) {
            try {
                price = Double.parseDouble(priceStr.substring(1, priceStr.length() - 5));
            } catch (Exception e) {
                valid = false;
                error = "parsing price double: " + e;
                return;
            }
        } else {
            valid = false;
            error = "price doesn't end with each: " + priceStr;
            return;
        }
        Level world = sign.getLevel();
        if (world == null) {
            valid = false;
            error = "couldn't get world";
            return;
        }

        BlockState blockState = sign.getBlockState();
        Optional<Direction> direction = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING);
        if (direction.isPresent()) {
            BlockPos chestPos = blockPos.offset(direction.get().getOpposite().getNormal());
            BlockEntity chestEntity = world.getBlockEntity(chestPos);
            LOGGER.info("chest shop: {}", this);
        }
        LOGGER.info("chest shop: {}", this);
    }

    public String toCsv() {
        return warp + "," +
                owner + "," +
                blockPosStr() + "," +
                shopState.toString() + "," +
                quantity + "," +
                item + "," +
                price + "\n";
    }

    public String getError() {
        return error;
    }

    private String blockPosStr() {
        return blockPos.getX() + ":" + blockPos.getY() + ":" + blockPos.getZ();
    }

    public String toString() {
        String str = "ShopChest\n" +
                "Owner: " + owner + "\n" +
                "State: " + shopState.toString() + "\n" +
                "Quantity: " + quantity + "\n" +
                "Item: " + item + "\n" +
                "Id: " + id + "\n" +
                "Price: $" + price + "\n";
        return str;
    }

    public boolean isShop() {
        return valid;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }
}
