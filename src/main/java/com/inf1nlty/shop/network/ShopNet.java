package com.inf1nlty.shop.network;

import btw.BTWAddon;
import com.inf1nlty.shop.client.gui.GuiShop;
import com.inf1nlty.shop.client.state.ShopClientData;
import com.inf1nlty.shop.client.state.SystemShopClientCatalog;
import com.inf1nlty.shop.client.gui.GuiGlobalShop;
import com.inf1nlty.shop.client.gui.GuiGlobalShop.GlobalListingClient;
import com.inf1nlty.shop.client.gui.GuiMailbox;
import com.inf1nlty.shop.inventory.ContainerShopPlayer;
import com.inf1nlty.shop.inventory.ContainerMailbox;
import net.minecraft.src.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified network channel.
 * Handles both buy/sell orders and mailbox fulfillment.
 */
public final class ShopNet {

    public static String CHANNEL;

    private ShopNet() {}

    public static void register(BTWAddon addon) {
        CHANNEL = addon.getModID() + "|Shop";
        addon.registerPacketHandler(CHANNEL, (packet, player) -> {
            if (packet == null || packet.data == null) return;
            if (player.worldObj.isRemote) client(packet);
            else if (player instanceof EntityPlayerMP mp) ShopNetServer.handlePacket(packet, mp);
        });
    }

    private static void client(Packet250CustomPayload packet) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data))) {
            byte a = in.readByte();
            switch (a) {
                case 1 -> {
                    in.readBoolean();
                    in.readUTF();
                    ShopClientData.balance = in.readInt();
                }
                case 4 -> { // system open + catalog
                    int windowId = in.readInt();
                    ShopClientData.balance = in.readInt();
                    int count = in.readInt();
                    List<SystemShopClientCatalog.Entry> list = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        SystemShopClientCatalog.Entry e = new SystemShopClientCatalog.Entry();
                        e.itemID = in.readInt();
                        e.meta = in.readInt();
                        e.buyTenths = in.readInt();
                        e.sellTenths = in.readInt();
                        list.add(e);
                    }
                    SystemShopClientCatalog.set(list);
                    Minecraft mc = Minecraft.getMinecraft();
                    ContainerShopPlayer c = new ContainerShopPlayer(mc.thePlayer.inventory);
                    c.windowId = windowId;
                    mc.thePlayer.openContainer = c;
                    mc.displayGuiScreen(new GuiShop(mc.thePlayer, c));
                }
                case 5 -> {
                    Minecraft mc = Minecraft.getMinecraft();
                    EntityPlayer p = mc.thePlayer;
                    if (p != null) {
                        ItemStack[] main = p.inventory.mainInventory;
                        for (int i = 0; i < main.length; i++) {
                            short id = in.readShort();
                            if (id < 0) main[i] = null;
                            else {
                                int size = in.readByte();
                                int dmg = in.readShort();
                                Item item = id < Item.itemsList.length ? Item.itemsList[id] : null;
                                ItemStack stack = item != null ? new ItemStack(item, size, dmg) : null;
                                boolean hasNbt = in.readBoolean();
                                if (hasNbt && stack != null) {
                                    int nbtLen = in.readShort();
                                    byte[] nbt = new byte[nbtLen];
                                    in.readFully(nbt);
                                    stack.stackTagCompound = CompressedStreamTools.readCompressed(new ByteArrayInputStream(nbt));
                                }
                                main[i] = stack;
                            }
                        }
                    }
                }
                case 7 -> {
                    boolean isOpenRequest = in.readBoolean();
                    int windowId = in.readInt();
                    ShopClientData.balance = in.readInt();
                    int cnt = in.readInt();
                    List<GlobalListingClient> snapshot = new ArrayList<>(cnt);
                    for (int i = 0; i < cnt; i++) {
                        GlobalListingClient c = new GlobalListingClient();
                        c.listingId = in.readInt();
                        c.itemId = in.readInt();
                        c.meta = in.readInt();
                        c.amount = in.readInt();
                        c.priceTenths = in.readInt();
                        c.owner = in.readUTF();
                        c.isBuyOrder = in.readBoolean();
                        boolean hasNbt = in.readBoolean();
                        if (hasNbt) {
                            int len = in.readUnsignedShort();
                            byte[] data = new byte[len];
                            in.readFully(data);
                            c.nbtCompressed = data;
                        }
                        snapshot.add(c);
                    }
                    Minecraft mc = Minecraft.getMinecraft();
                    GuiGlobalShop.setSnapshot(snapshot);
                    if (mc.currentScreen instanceof GuiGlobalShop shopGui) {
                        shopGui.refreshListings();
                    } else if (isOpenRequest) {
                        ContainerShopPlayer container = new ContainerShopPlayer(mc.thePlayer.inventory);
                        container.windowId = windowId;
                        mc.thePlayer.openContainer = container;
                        mc.displayGuiScreen(new GuiGlobalShop(mc.thePlayer, container));
                    }
                }
                case 14 -> {
                    int windowId = in.readInt();
                    Minecraft mc = Minecraft.getMinecraft();
                    InventoryBasic mailboxInv = new InventoryBasic("Mailbox", false, 133);
                    for (int i = 0; i < 133; i++) {
                        short id = in.readShort();
                        if (id < 0) {
                            mailboxInv.setInventorySlotContents(i, null);
                        } else {
                            int size = in.readByte();
                            int dmg = in.readShort();
                            boolean hasNbt = in.readBoolean();
                            ItemStack stack = new ItemStack(Item.itemsList[id], size, dmg);
                            if (hasNbt) {
                                int nbtLen = in.readShort();
                                byte[] nbt = new byte[nbtLen];
                                in.readFully(nbt);
                                stack.stackTagCompound = CompressedStreamTools.readCompressed(new ByteArrayInputStream(nbt));
                            }
                            mailboxInv.setInventorySlotContents(i, stack);
                        }
                    }
                    ContainerMailbox container = new ContainerMailbox(mc.thePlayer.inventory, mailboxInv);
                    container.windowId = windowId;
                    mc.thePlayer.openContainer = container;
                    mc.displayGuiScreen(new GuiMailbox(mc.thePlayer, container));
                }
                case 20 ->
                    ShopClientData.balance = in.readInt();
                default -> {}
            }
        } catch (Exception ignored) {}
    }

    // Client -> Server

    public static void sendPurchaseRequest(int itemID, int meta, int count) {
        writePacket(out -> {
            out.writeByte(2);
            out.writeInt(itemID);
            out.writeInt(meta);
            out.writeInt(count);
        });
    }

    public static void sendSellRequest(int itemID, int count, int slotIndex) {
        writePacket(out -> {
            out.writeByte(3);
            out.writeInt(itemID);
            out.writeInt(count);
            out.writeInt(slotIndex);
        });
    }

    public static void sendOpenRequest() {
        writePacket(out -> out.writeByte(6));
    }

    public static void sendGlobalOpenRequest() {
        writePacket(out -> out.writeByte(8));
    }

    public static void sendGlobalBuy(int listingId, int count) {
        writePacket(out -> {
            out.writeByte(9);
            out.writeInt(listingId);
            out.writeInt(count);
        });
    }

    public static void sendGlobalList(int itemId, int meta, int amount, int priceTenths) {
        writePacket(out -> {
            out.writeByte(10);
            out.writeInt(itemId);
            out.writeInt(meta);
            out.writeInt(amount);
            out.writeInt(priceTenths);
        });
    }

    public static void sendGlobalUnlist(int listingId) {
        writePacket(out -> {
            out.writeByte(11);
            out.writeInt(listingId);
        });
    }

    public static void sendMailboxOpen() {
        writePacket(out -> out.writeByte(12));
    }

    public static void sendSellToBuyOrder(int listingId, int count) {
        writePacket(out -> {
            out.writeByte(13);
            out.writeInt(listingId);
            out.writeInt(count);
        });
    }

    private interface Writer { void write(DataOutputStream out) throws Exception; }

    private static void writePacket(Writer w) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            w.write(out);
            Packet250CustomPayload p = new Packet250CustomPayload(CHANNEL, bos.toByteArray());
            Minecraft.getMinecraft().thePlayer.sendQueue.addToSendQueue(p);
        } catch (Exception ignored) {}
    }
}