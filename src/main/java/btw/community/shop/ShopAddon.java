package btw.community.shop;

import btw.AddonHandler;
import btw.BTWAddon;
import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.commands.MoneyCommand;
import com.inf1nlty.shop.commands.ShopCommand;
import com.inf1nlty.shop.commands.GAliasCommand;
import com.inf1nlty.shop.commands.GlobalShopCommand;
import com.inf1nlty.shop.network.ShopNet;
import com.inf1nlty.shop.global.GlobalShopData;
import com.inf1nlty.shop.util.MoneyManager;

public class ShopAddon extends BTWAddon {

    public static String MOD_VERSION;

    @Override
    public void initialize() {
        AddonHandler.logMessage(getName() + " v" + getVersionString() + " Initializing...");
        MOD_VERSION = this.getVersionString();
        MoneyManager.loadBalancesFromFile();
        GlobalShopData.load();
        this.registerAddonCommand(new ShopCommand());
        this.registerAddonCommand(new MoneyCommand());
        this.registerAddonCommand(new GlobalShopCommand());
        this.registerAddonCommand(new GAliasCommand());
        ShopNet.register(this);
        ShopConfig.forceReload();

    }

}