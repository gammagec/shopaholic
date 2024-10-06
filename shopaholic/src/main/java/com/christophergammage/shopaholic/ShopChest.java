package com.christophergammage.shopaholic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public class ShopChest {
    private BlockPos blockPos;
    private String owner;
    private boolean valid = true;

    enum ShopState {
        SELLING("selling"),
        BUYING("buying"),
        OUT_OF_STOCK("out of stock");
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
    double price = 0;

    private int quantity = 0;

    public ShopChest(SignBlockEntity sign) {
        this.blockPos = sign.getBlockPos();
        Component[] messages = sign.getText(true).getMessages(true);
        if (messages.length < 4) {
            valid = false;
            return;
        }
        this.owner = messages[0].getString();
        String state = messages[1].getString();
        if (state.equals("Out of Stock")) {
            shopState = ShopState.OUT_OF_STOCK;
        } else if (state.startsWith("Selling")) {
            shopState = ShopState.SELLING;
            String[] split = state.split(" ");
            if (split.length < 2) {
                valid = false;
                return;
            }
            try {
                quantity = Integer.parseInt(split[1]);
            } catch (Exception e) {
                valid = false;
                return;
            }
        } else if (state.startsWith("Buying")) {
            shopState = ShopState.BUYING;
        } else {
            valid = false;
            return;
        }
        item = messages[2].getString();
        String priceStr = messages[3].getString().replaceAll(",", "");
        if (!priceStr.startsWith("$")) {
            valid = false;
            return;
        }
        if (priceStr.endsWith("each")) {
            try {
                price = Double.valueOf(priceStr.substring(1, priceStr.length() - 5));
            } catch (Exception e) {
                valid = false;
            }
        } else {
            valid = false;
        }
    }
    public String toCsv(String warp) {
        String str = warp + "," +
                owner + "," +
                shopState.toString() + "," +
                quantity + "," +
                item + "," +
                price + "\n";
        return str;
    }

    public String toString() {
        String str = "ShopChest\n" +
                "Owner: " + owner + "\n" +
                "State: " + shopState.toString() + "\n" +
                "Quantity: " + quantity + "\n" +
                "Item: " + item + "\n" +
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
