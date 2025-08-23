package btw.community.shop;

import btw.AddonHandler;
import btw.BTWAddon;
import com.inf1nlty.shop.ShopConfig;
import com.inf1nlty.shop.commands.MoneyCommand;
import com.inf1nlty.shop.commands.ShopCommand;
import com.inf1nlty.shop.commands.GAliasCommand;
import com.inf1nlty.shop.commands.GlobalShopCommand;
import com.inf1nlty.shop.network.HandshakeClient;
import com.inf1nlty.shop.network.HandshakeServer;
import com.inf1nlty.shop.network.ShopNet;
import com.inf1nlty.shop.global.GlobalShopData;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.NetServerHandler;

public class ShopAddon extends BTWAddon {

    public static String MOD_VERSION;

    @Override
    public void initialize() {
        AddonHandler.logMessage(getName() + " v" + getVersionString() + " Initializing...");
        MOD_VERSION = this.getVersionString();
        GlobalShopData.load();
        this.registerAddonCommand(new ShopCommand());
        this.registerAddonCommand(new MoneyCommand());
        this.registerAddonCommand(new GlobalShopCommand());
        this.registerAddonCommand(new GAliasCommand());
        ShopNet.register(this);
        ShopConfig.forceReload();

        this.registerPacketHandler(HandshakeServer.VERSION_ACK_CHANNEL, (packet, player) -> {
            if (!(player instanceof EntityPlayerMP mp)) return;
            HandshakeServer.handleVersionAckPacket(mp.playerNetServerHandler, packet);
        });
        this.registerPacketHandler(HandshakeClient.VERSION_CHECK_CHANNEL, (packet, player) -> HandshakeClient.handleVersionCheckPacketClient(packet));
    }

    @Override
    public void serverPlayerConnectionInitialized(NetServerHandler serverHandler, EntityPlayerMP playerMP) {
        HandshakeServer.onPlayerJoin(serverHandler, playerMP);
    }
}