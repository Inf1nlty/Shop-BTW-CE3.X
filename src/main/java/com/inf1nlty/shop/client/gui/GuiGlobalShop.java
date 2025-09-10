package com.inf1nlty.shop.client.gui;

import com.inf1nlty.shop.client.state.ShopClientData;
import com.inf1nlty.shop.inventory.ContainerShopPlayer;
import com.inf1nlty.shop.network.ShopNet;
import com.inf1nlty.shop.util.Money;
import emi.dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.src.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Global marketplace GUI (fixed 19 x 7 grid).
 * Displays both sell and buy orders.
 * Sell listings are shown with green border, buy orders with blue border.
 */
public class GuiGlobalShop extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation("shop:textures/gui/shop_gui.png");

    private static final int GUI_W = 356;
    private static final int GUI_H = 240;

    private static final int GRID_BG_W = 356;
    private static final int GRID_BG_H = 150;

    private static final int PLAYER_BG_X = 90;
    private static final int PLAYER_BG_Y = 150;
    private static final int PLAYER_BG_W = 176;
    private static final int PLAYER_BG_H = 90;

    private static final int LISTING_COLS = 19;
    private static final int LISTING_ROWS = 7;
    private static final int LISTING_START_X = 8;
    private static final int LISTING_START_Y = 18;
    private static final int SLOT_SIZE = 18;

    private static final int PLAYER_LABEL_X = 97;
    private static final int PLAYER_LABEL_Y = 145;

    private static final int HOVER_COLOR = 0x40FFFFFF;
    private static final int SELL_BORDER_COLOR = 0xFFE000; // Yellow
    private static final int BUY_BORDER_COLOR = 0x6034DB34; // Green

    private static final int BTN_PREV = 200;
    private static final int BTN_NEXT = 201;

    private static final int TITLE_X = 15;
    private static final int TITLE_Y = 5;
    private static final int PAGE_Y = 5;

    private static final int BTN_UNLIST_BASE = 1000;

    private static final String KEY_TITLE = "globalshop.title";
    private static final String KEY_PAGE = "shop.page";
    private static final String KEY_INVENTORY = "shop.inventory";
    private static final String KEY_MONEY = "shop.money";

    private int totalPages;
    private int currentPage;

    private ItemStack hoveredStack;
    private GlobalListingClient hoveredListing;

    public static final List<GlobalListingClient> CLIENT_LISTINGS = new ArrayList<>();

    public GuiGlobalShop(EntityPlayer player, ContainerShopPlayer container) {
        super(container);
        xSize = GUI_W;
        ySize = GUI_H;
    }

    public static void setSnapshot(List<GlobalListingClient> snapshot) {
        CLIENT_LISTINGS.clear();
        CLIENT_LISTINGS.addAll(snapshot);
        for (GlobalListingClient c : CLIENT_LISTINGS) {
            if (c.nbtCompressed != null && c.nbtCompressed.length > 0) {
                try {
                    c.nbt = CompressedStreamTools.readCompressed(new ByteArrayInputStream(c.nbtCompressed));
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        ShopClientData.inGlobalShop = true;
        ShopClientData.inShop = false;

        int cap = capacityPerPage();
        totalPages = Math.max(1, (CLIENT_LISTINGS.size() + cap - 1) / cap);
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        buttonList.clear();
        int gl = (width - xSize) / 2;
        int gt = (height - ySize) / 2;
        buttonList.add(new TinyButton(BTN_PREV, gl + 4, gt + TITLE_Y - 1, "<"));
        buttonList.add(new TinyButton(BTN_NEXT, gl + xSize - 14, gt + TITLE_Y - 1, ">"));
        updateButtons();
    }

    private int capacityPerPage() {
        return LISTING_COLS * LISTING_ROWS; // 133
    }

    @Override
    public void onGuiClosed() {
        ShopClientData.inGlobalShop = false;
        super.onGuiClosed();
    }

    private void updateButtons() {
        for (Object o : buttonList) {
            if (o instanceof TinyButton b) {
                if (b.id == BTN_PREV) b.enabled = currentPage > 0;
                if (b.id == BTN_NEXT) b.enabled = currentPage < totalPages - 1;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int cap = capacityPerPage();
        if (button.id == BTN_PREV && currentPage > 0) {
            currentPage--;
            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);
            updateButtons();
        } else if (button.id == BTN_NEXT && currentPage < totalPages - 1) {
            currentPage++;
            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            updateButtons();
        } else if (button.id >= BTN_UNLIST_BASE) {
            int local = button.id - BTN_UNLIST_BASE;
            int globalIndex = currentPage * cap + local;
            if (globalIndex >= 0 && globalIndex < CLIENT_LISTINGS.size()) {
                GlobalListingClient lc = CLIENT_LISTINGS.get(globalIndex);
                if (lc.owner != null && lc.owner.equals(mc.thePlayer.username)) {
                    ShopNet.sendGlobalUnlist(lc.listingId);
                    mc.sndManager.playSoundFX("random.pop", 1.0F, 1.0F);
//                    ShopNet.sendGlobalRefreshRequest();
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        updateHoveredListing(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partial);
        if (theSlot == null && hoveredStack != null) {
            drawItemStackTooltip(hoveredStack, mouseX, mouseY);
        }
    }

    private void updateHoveredListing(int mouseXAbs, int mouseYAbs) {
        hoveredStack = null;
        hoveredListing = null;
        int cap = capacityPerPage();
        int gl = (width - xSize) / 2;
        int gt = (height - ySize) / 2;
        int relX = mouseXAbs - gl;
        int relY = mouseYAbs - gt;
        int local = getHoverLocalIndex(relX, relY);
        if (local < 0) return;
        int globalIndex = currentPage * cap + local;
        if (globalIndex < 0 || globalIndex >= CLIENT_LISTINGS.size()) return;
        GlobalListingClient lc = CLIENT_LISTINGS.get(globalIndex);
        Item item = (lc.itemId >= 0 && lc.itemId < Item.itemsList.length) ? Item.itemsList[lc.itemId] : null;
        if (item == null) return;
        int display = Math.min(item.getItemStackLimit(), lc.amount);
        ItemStack stack = new ItemStack(item, display, lc.meta);
        if (lc.nbt != null) stack.stackTagCompound = (NBTTagCompound) lc.nbt.copy();
        if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
        stack.stackTagCompound.setInteger("GShopPriceTenths", lc.priceTenths);
        stack.stackTagCompound.setInteger("GShopAmount", lc.amount);
        stack.stackTagCompound.setString("GShopSeller", lc.owner);
        hoveredStack = stack;
        hoveredListing = lc;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1,1,1,1);
        mc.renderEngine.bindTexture(TEX);
        int gl = (width - xSize)/2;
        int gt = (height - ySize)/2;
        blit(gl, gt, 0, 0, GRID_BG_W, GRID_BG_H);
        blit(gl + PLAYER_BG_X, gt + PLAYER_BG_Y, PLAYER_BG_X, PLAYER_BG_Y, PLAYER_BG_W, PLAYER_BG_H);
        renderListingItems(gl, gt, mouseX, mouseY);
    }

    @SuppressWarnings("unchecked")
    private void renderListingItems(int gl, int gt, int mouseXAbs, int mouseYAbs) {
        buttonList.removeIf(b -> b instanceof TinyButton && ((TinyButton) b).isUnlist);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);

        int cap = capacityPerPage();
        int startIndex = currentPage * cap;
        int endIndex = Math.min(CLIENT_LISTINGS.size(), startIndex + cap);

        int relMouseX = mouseXAbs - gl;
        int relMouseY = mouseYAbs - gt;
        int hoverLocal = getHoverLocalIndex(relMouseX, relMouseY);

        for (int idx = startIndex; idx < endIndex; idx++) {
            GlobalListingClient lc = CLIENT_LISTINGS.get(idx);
            int local = idx - startIndex;
            int row = local / LISTING_COLS;
            int col = local % LISTING_COLS;
            int sx = gl + LISTING_START_X + col * SLOT_SIZE;
            int sy = gt + LISTING_START_Y + row * SLOT_SIZE;
            Item item = (lc.itemId >= 0 && lc.itemId < Item.itemsList.length) ? Item.itemsList[lc.itemId] : null;
            if (item == null) continue;
            int display = Math.min(item.getItemStackLimit(), lc.amount);
            ItemStack stack = new ItemStack(item, display, lc.meta);
            if (lc.nbt != null) stack.stackTagCompound = (NBTTagCompound) lc.nbt.copy();
            itemRenderer.renderItemAndEffectIntoGUI(fontRenderer, mc.renderEngine, stack, sx, sy);
            itemRenderer.renderItemOverlayIntoGUI(fontRenderer, mc.renderEngine, stack, sx, sy);

            // Draw border to indicate type
            int borderColor = lc.isBuyOrder ? BUY_BORDER_COLOR : SELL_BORDER_COLOR;
            drawRect(sx - 1, sy - 1, sx + 17, sy + 17, borderColor);

            if (local == hoverLocal) {
                GL11.glDisable(GL11.GL_LIGHTING);
                drawGradientRect(sx, sy, sx + 16, sy + 16, HOVER_COLOR, HOVER_COLOR);
                GL11.glEnable(GL11.GL_LIGHTING);
            }
            if (lc.amount == -1) {
                String amtStr = "∞";
                int x = sx + 16 - fontRenderer.getStringWidth(amtStr);
                int y = sy + 10;
                fontRenderer.drawStringWithShadow(amtStr, x, y, 0xFFFFFF);
            }
            if (lc.owner != null && lc.owner.equals(mc.thePlayer.username)) {
                int btnId = BTN_UNLIST_BASE + local;
                int unlistBtnWidth = 4;
                int unlistBtnHeight = 4;
                int btnX = sx + SLOT_SIZE - unlistBtnWidth - 1;
                int btnY = sy + 0;
                TinyButton unlistBtn = new TinyButton(btnId, btnX, btnY, unlistBtnWidth, unlistBtnHeight, "X", true);
                unlistBtn.enabled = true;
                unlistBtn.drawButton(mc, mouseXAbs, mouseYAbs);
                buttonList.add(unlistBtn);
            }
        }

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GL11.glPopMatrix();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void drawItemStackTooltip(ItemStack par1ItemStack, int par2, int par3) {
        EmiScreenManager.lastStackTooltipRendered = par1ItemStack;
        List var4 = par1ItemStack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
        for(int var5 = 0; var5 < var4.size(); ++var5) {
            if (var5 == 0) {
                String var10002 = Integer.toHexString(par1ItemStack.getRarity().rarityColor);
                var4.set(var5, "§" + var10002 + (String)var4.get(var5));
            } else {
                EnumChatFormatting var6 = EnumChatFormatting.GRAY;
                var4.set(var5, var6 + (String)var4.get(var5));
            }
        }
        if (ShopClientData.inGlobalShop && hoveredListing != null) {
            int price = hoveredListing.priceTenths;
            int amount = hoveredListing.amount;
            String owner = hoveredListing.owner;
            boolean isBuyOrder = hoveredListing.isBuyOrder;
            String typeLbl = isBuyOrder ? I18n.getString("gshop.buyorder") : I18n.getString("gshop.sellorder");
            var4.add("§9" + typeLbl); {
                String priceLbl = I18n.getString("gshop.price");
                var4.add("§e" + priceLbl + ": §f" + Money.format(price));
            }
            String amountLbl = I18n.getString("gshop.amount");
            var4.add("§b" + amountLbl + ": §f" + (amount == -1 ? "∞" : amount));
            String ownerLbl = isBuyOrder ? I18n.getString("gshop.buyer") : I18n.getString("gshop.seller");
            var4.add((isBuyOrder ? "§b" : "§d") + ownerLbl + ": §f" + owner);
        }
        this.func_102021_a(var4, par2, par3);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseXRel, int mouseYRel) {
        String title = I18n.getString(KEY_TITLE);
        fontRenderer.drawString(title, TITLE_X, TITLE_Y, 0x404040);

        String pageStr = I18n.getString(KEY_PAGE)
                .replace("{page}", String.valueOf(currentPage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        fontRenderer.drawString(pageStr, (xSize - fontRenderer.getStringWidth(pageStr)) / 2, PAGE_Y, 0x606060);

        fontRenderer.drawString(I18n.getString(KEY_INVENTORY), PLAYER_LABEL_X, PLAYER_LABEL_Y, 0x404040);

        String money = I18n.getString(KEY_MONEY) + ": " + Money.format(ShopClientData.balance);
        int moneyX = PLAYER_BG_X + (PLAYER_BG_W - fontRenderer.getStringWidth(money)) / 2;
        fontRenderer.drawString(money, moneyX, PLAYER_LABEL_Y, 0xFF3498DB);
    }

    @Override
    protected void mouseClicked(int mouseXAbs, int mouseYAbs, int button) {
        for (Object o : buttonList) {
            if (o instanceof TinyButton b && b.mousePressed(mc, mouseXAbs, mouseYAbs)) {
                actionPerformed(b);
                return;
            }
        }
        int cap = capacityPerPage();
        int gl = (width - xSize)/2;
        int gt = (height - ySize)/2;
        int relX = mouseXAbs - gl;
        int relY = mouseYAbs - gt;
        int local = getHoverLocalIndex(relX, relY);
        if (local >= 0 && button == 0) {
            int globalIndex = currentPage * cap + local;
            if (globalIndex < CLIENT_LISTINGS.size()) {
                GlobalListingClient lc = CLIENT_LISTINGS.get(globalIndex);
                if (lc.isBuyOrder) {
                    // Selling to buy order
                    ItemStack hand = mc.thePlayer.inventory.getCurrentItem();
                    int count = isShiftKeyDown() ? (hand != null ? hand.stackSize : 1) : 1;
                    ShopNet.sendSellToBuyOrder(lc.listingId, count);
                } else {
                    // Buying from sell listing
                    Item item = Item.itemsList[lc.itemId];
                    int stackLimit = item != null ? item.getItemStackLimit() : 64;
                    int count = isShiftKeyDown() ? Math.min(stackLimit, lc.amount) : 1;
                    ShopNet.sendGlobalBuy(lc.listingId, count);
                }
                return;
            }
        }
        super.mouseClicked(mouseXAbs, mouseYAbs, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getDWheel();
        if (wheel != 0) {
            if (wheel > 0 && currentPage > 0) {
                currentPage--;
                updateButtons();
            } else if (wheel < 0 && currentPage < totalPages - 1) {
                currentPage++;
                updateButtons();
            }
        }
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindInventory.keyCode) {
            mc.thePlayer.closeScreen();
            return;
        }
        super.keyTyped(c, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int getHoverLocalIndex(int relX, int relY) {
        int x = relX - LISTING_START_X;
        int y = relY - LISTING_START_Y;
        if (x < 0 || y < 0) return -1;
        int col = x / SLOT_SIZE;
        int row = y / SLOT_SIZE;
        if (col >= LISTING_COLS || row >= LISTING_ROWS) return -1;
        return row * LISTING_COLS + col;
    }

    private void blit(int x, int y, int u, int v, int w, int h) {
        float fW = 1F / 512F;
        float fH = 1F / 512F;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x,     y + h, zLevel, u * fW,       (v + h) * fH);
        t.addVertexWithUV(x + w, y + h, zLevel, (u + w) * fW, (v + h) * fH);
        t.addVertexWithUV(x + w, y,     zLevel, (u + w) * fW, v * fH);
        t.addVertexWithUV(x,     y,     zLevel, u * fW,       v * fH);
        t.draw();
    }

    private static class TinyButton extends GuiButton {
        private final boolean isUnlist;

        public TinyButton(int id, int x, int y, int width, int height, String txt, boolean isUnlist) {
            super(id, x, y, width, height, txt);
            this.isUnlist = isUnlist;
        }
        public TinyButton(int id, int x, int y, String txt) {
            this(id, x, y, 10, 10, txt, false); // 默认10x10
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!drawButton) return;
            boolean hover = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            int bg, textColor;
            if (isUnlist) {
                bg = hover ? 0x60FF2222 : 0x30FF2222;
                textColor = hover ? 0xFFFF3333 : 0xFFAA3333;
            } else {
                bg = hover ? 0x60FFFFFF : 0x30FFFFFF;
                textColor = hover ? 0xFFD700 : 0xE0E0E0;
            }
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);
            int cx = xPosition + (width - mc.fontRenderer.getStringWidth("§l" + displayString)) / 2;
            int cy = yPosition + (height - mc.fontRenderer.FONT_HEIGHT) / 2;
            mc.fontRenderer.drawStringWithShadow("§l" + displayString, cx, cy, textColor);
        }
        @Override
        public boolean mousePressed(Minecraft mc, int mx, int my) {
            return enabled &&
                    mx >= xPosition && my >= yPosition &&
                    mx < xPosition + width && my < yPosition + height;
        }
    }

    public static class GlobalListingClient {
        public int listingId;
        public int itemId;
        public int meta;
        public int amount;
        public int priceTenths;
        public String owner;
        public boolean isBuyOrder;
        public byte[] nbtCompressed;
        public NBTTagCompound nbt;
    }

    public void refreshListings() {
        int cap = capacityPerPage();
        totalPages = Math.max(1, (CLIENT_LISTINGS.size() + cap - 1) / cap);
        if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);
        buttonList.clear();
        updateButtons();
    }
}