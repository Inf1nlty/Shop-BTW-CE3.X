package com.inf1nlty.shop.client.gui;

import com.inf1nlty.shop.client.state.ShopClientData;
import com.inf1nlty.shop.client.state.SystemShopClientCatalog;
import com.inf1nlty.shop.inventory.ContainerShopPlayer;
import com.inf1nlty.shop.network.ShopNet;
import com.inf1nlty.shop.util.Money;
import emi.dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.src.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.List;

/**
 * System shop GUI (server catalog).
 * Always renders a 19 x 7 grid (133 entries per page).
 */
public class GuiShop extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation("shop:textures/gui/shop_gui.png");

    private static final int GUI_W = 356;
    private static final int GUI_H = 240;

    private static final int SHOP_BG_W = 356;
    private static final int SHOP_BG_H = 150;

    private static final int PLAYER_BG_X = 90;
    private static final int PLAYER_BG_Y = 150;
    private static final int PLAYER_BG_W = 176;
    private static final int PLAYER_BG_H = 90;

    private static final int SHOP_COLS = 19;
    private static final int SHOP_ROWS = 7;          // fixed 7 rows
    private static final int SHOP_START_X = 8;
    private static final int SHOP_START_Y = 18;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_HEIGHT = SHOP_ROWS * SLOT_SIZE; // 126
    // Background height check: SHOP_START_Y (18) + GRID_HEIGHT (126) = 144; remaining 6 px bottom padding inside 150

    private static final int PLAYER_LABEL_X = 97;
    private static final int PLAYER_LABEL_Y = 145;

    private static final int HOVER_COLOR = 0x40FFFFFF;

    private static final int TITLE_X = 15;
    private static final int TITLE_Y = 5;
    private static final int PAGE_Y = 5;

    private static final String KEY_TITLE = "shop.title";
    private static final String KEY_INVENTORY = "shop.inventory";
    private static final String KEY_MONEY = "shop.money";
    private static final String KEY_PAGE = "shop.page";

    private static final int BTN_PREV = 100;
    private static final int BTN_NEXT = 101;

    private final List<SystemShopClientCatalog.Entry> entries;

    private int totalPages;
    private int currentPage;
    private ItemStack hoveredStack;

    public GuiShop(EntityPlayer player, ContainerShopPlayer container) {
        super(container);
        this.entries = SystemShopClientCatalog.get();
        xSize = GUI_W;
        ySize = GUI_H;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initGui() {
        super.initGui();
        ShopClientData.inShop = true;
        ShopClientData.inGlobalShop = false;

        int capacityPerPage = SHOP_COLS * SHOP_ROWS; // 133
        totalPages = Math.max(1, (entries.size() + capacityPerPage - 1) / capacityPerPage);
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        buttonList.clear();
        int gl = (width - xSize) / 2;
        int gt = (height - ySize) / 2;
        buttonList.add(new TinyButton(BTN_PREV, gl + 4, gt + TITLE_Y - 1, "<"));
        buttonList.add(new TinyButton(BTN_NEXT, gl + xSize - 14, gt + TITLE_Y - 1, ">"));
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        ShopClientData.inShop = false;
        super.onGuiClosed();
    }

    private int capacityPerPage() {
        return SHOP_COLS * SHOP_ROWS;
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
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        updateHovered(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partial);
        if (theSlot == null && hoveredStack != null) {
            drawItemStackTooltip(hoveredStack, mouseX, mouseY);
        }
    }

    private void updateHovered(int mx, int my) {
        hoveredStack = null;
        int cap = capacityPerPage();
        int gl = (width - xSize) / 2;
        int gt = (height - ySize) / 2;
        int relX = mx - gl;
        int relY = my - gt;
        int local = getHoverLocalIndex(relX, relY);
        if (local < 0) return;
        int index = currentPage * cap + local;
        if (index < 0 || index >= entries.size()) return;
        hoveredStack = entries.get(index).toStack();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1,1,1,1);
        mc.renderEngine.bindTexture(TEX);
        int gl = (width - xSize)/2;
        int gt = (height - ySize)/2;
        blit(gl, gt, 0, 0, SHOP_BG_W, SHOP_BG_H);
        blit(gl + PLAYER_BG_X, gt + PLAYER_BG_Y, PLAYER_BG_X, PLAYER_BG_Y, PLAYER_BG_W, PLAYER_BG_H);
        renderItems(gl, gt, mouseX, mouseY);
    }

    private void renderItems(int gl, int gt, int mx, int my) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);

        int cap = capacityPerPage();
        int start = currentPage * cap;
        int end = Math.min(entries.size(), start + cap);

        int relMouseX = mx - gl;
        int relMouseY = my - gt;
        int hoverLocal = getHoverLocalIndex(relMouseX, relMouseY);

        for (int idx = start; idx < end; idx++) {
            int local = idx - start;
            int row = local / SHOP_COLS;
            int col = local % SHOP_COLS;
            int sx = gl + SHOP_START_X + col * SLOT_SIZE;
            int sy = gt + SHOP_START_Y + row * SLOT_SIZE;
            ItemStack stack = entries.get(idx).toStack();
            if (stack == null) continue;
            itemRenderer.renderItemAndEffectIntoGUI(fontRenderer, mc.renderEngine, stack, sx, sy);
            itemRenderer.renderItemOverlayIntoGUI(fontRenderer, mc.renderEngine, stack, sx, sy);
            if (local == hoverLocal) {
                GL11.glDisable(GL11.GL_LIGHTING);
                drawGradientRect(sx, sy, sx + 16, sy + 16, HOVER_COLOR, HOVER_COLOR);
                GL11.glEnable(GL11.GL_LIGHTING);
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
        if (ShopClientData.inShop) {
            List<SystemShopClientCatalog.Entry> entries =
                    SystemShopClientCatalog.get();
            int foundIndex = -1;
            for (int i = 0; i < entries.size(); ++i) {
                SystemShopClientCatalog.Entry e = entries.get(i);
                if (e.itemID == par1ItemStack.itemID && e.meta == par1ItemStack.getItemDamage()) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex != -1) {
                SystemShopClientCatalog.Entry entry = entries.get(foundIndex);
                int buy = entry.buyTenths;
                int sell = entry.sellTenths;
                String priceLbl = I18n.getString("shop.price");
                String sellLbl = I18n.getString("shop.sellprice");
                var4.add("§e" + priceLbl + ": §f" + Money.format(buy));
                var4.add("§a" + sellLbl + ": §f" + Money.format(sell));
            }
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
        fontRenderer.drawString(money, moneyX, PLAYER_LABEL_Y, 0x3498DB);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        for (Object o : buttonList) {
            if (o instanceof TinyButton b && b.mousePressed(mc, mx, my)) {
                actionPerformed(b);
                return;
            }
        }
        boolean shift = isShiftKeyDown();
        int cap = capacityPerPage();
        int gl = (width - xSize)/2;
        int gt = (height - ySize)/2;
        int relX = mx - gl;
        int relY = my - gt;
        int local = getHoverLocalIndex(relX, relY);
        if (local >= 0 && button == 0) {
            int index = currentPage * cap + local;
            if (index < entries.size()) {
                SystemShopClientCatalog.Entry e = entries.get(index);
                int count = shift ? 64 : 1;
                ShopNet.sendPurchaseRequest(e.itemID, e.meta, count);
                return;
            }
        }
        if (button == 0 && shift) {
            Slot slot = getSlotUnderMouse(mx, my);
            if (slot != null && slot.getHasStack()) {
                ItemStack s = slot.getStack();
                int containerIndex = slot.slotNumber;
                int mainIndex = (containerIndex < 27) ? (9 + containerIndex) : (containerIndex - 27);
                ShopNet.sendSellRequest(s.itemID, s.stackSize, mainIndex);
                return;
            }
        }
        super.mouseClicked(mx, my, button);
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
        int x = relX - SHOP_START_X;
        int y = relY - SHOP_START_Y;
        if (x < 0 || y < 0) return -1;
        int col = x / SLOT_SIZE;
        int row = y / SLOT_SIZE;
        if (col >= SHOP_COLS || row >= SHOP_ROWS) return -1;
        return row * SHOP_COLS + col;
    }

    private Slot getSlotUnderMouse(int mouseX, int mouseY) {
        for (Object o : inventorySlots.inventorySlots) {
            Slot s = (Slot) o;
            int x = guiLeft + s.xDisplayPosition;
            int y = guiTop + s.yDisplayPosition;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return s;
        }
        return null;
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
        public TinyButton(int id, int x, int y, String txt) {
            super(id, x, y, 10, 10, txt);
        }
        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!drawButton) return;
            boolean hover = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            int bg = hover ? 0x60FFFFFF : 0x30FFFFFF;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);
            int cx = xPosition + (width - mc.fontRenderer.getStringWidth("§l" + displayString)) / 2;
            int cy = yPosition + (height - mc.fontRenderer.FONT_HEIGHT) / 2;
            mc.fontRenderer.drawStringWithShadow("§l" + displayString, cx, cy, hover ? 0xFFD700 : 0xE0E0E0);
        }
        @Override
        public boolean mousePressed(Minecraft mc, int mx, int my) {
            return enabled && mx >= xPosition && my >= yPosition &&
                    mx < xPosition + width && my < yPosition + height;
        }
    }
}