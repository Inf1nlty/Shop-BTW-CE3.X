package com.inf1nlty.shop;

import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads system shop items from config/shop.cfg
 * Format:
 *   id[:meta]=buyPrice,sellPrice
 *   minecraft:name[:meta]=buyPrice,sellPrice
 * Optional flag:
 *   force_sell_unlisted=true|false
 * Prices one decimal (tenths stored).
 */
public class ShopConfig {

    public static boolean FORCE_SELL_UNLISTED = false;
    public static boolean IS_SKYBLOCK_MODE = false;
    public static boolean ANNOUNCE_GLOBAL_LISTING = false;

    private static Map<Integer, ShopItem> itemMap = new HashMap<>();
    private static List<ShopItem> itemList = new ArrayList<>();
    private static long lastLoadTime = 0L;
    private static final long RELOAD_MS = 3000;

    private static final Logger LOGGER = Logger.getLogger(ShopConfig.class.getName());

    private ShopConfig() {}

    public static synchronized ShopItem get(int id, int dmg) {
        ensure();
        ShopItem exact = itemMap.get(compositeKey(id, dmg));
        if (exact != null) return exact;
        return itemMap.get(compositeKey(id, dmg));
    }

    public static synchronized List<ShopItem> getItems() {
        ensure();
        return itemList;
    }

    public static synchronized void forceReload() {
        loadInternal(new File("config/shop.cfg"));
    }

    private static void ensure() {
        if (System.currentTimeMillis() - lastLoadTime < RELOAD_MS && !itemMap.isEmpty()) return;
        loadInternal(new File("config/shop.cfg"));
    }

    public static synchronized void regenerateDefault() {
        File file = new File("config/shop.cfg");
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                LOGGER.warning("Failed to delete existing shop.cfg before regenerating.");
            }
        }
        generateDefault(file);
        loadInternal(file);
        lastLoadTime = 0L;
    }

    private static void loadInternal(File file) {
        List<ShopItem> list = new ArrayList<>();
        boolean forceFlag = true;

        if (!file.exists()) generateDefault(file);
        boolean inSkyblockSection = false;

        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") && line.contains("skyblock_mode_items")) {
                    inSkyblockSection = true;
                    continue;
                }
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("force_sell_unlisted")) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2) forceFlag = Boolean.parseBoolean(kv[1].trim());
                    continue;
                }
                if (line.startsWith("skyblock_mode")) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2) IS_SKYBLOCK_MODE = Boolean.parseBoolean(kv[1].trim());
                    continue;
                }
                if (line.startsWith("announceGlobalListing")) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2) ANNOUNCE_GLOBAL_LISTING = Boolean.parseBoolean(kv[1].trim());
                    continue;
                }
                if (IS_SKYBLOCK_MODE) {
                    if (!inSkyblockSection) continue;
                } else {
                    if (inSkyblockSection) continue;
                }
                String[] parts = line.split("=");
                if (parts.length != 2) continue;

                IdMeta parsed = parseIdentifier(parts[0].trim());
                if (parsed.id < 0) continue;

                String[] priceParts = parts[1].split(",");
                if (priceParts.length < 2) continue;
                String buyRaw = priceParts[0].split("[ #]")[0].trim();
                String sellRaw = priceParts[1].split("[ #]")[0].trim();
                Integer buy = parsePriceTenths(buyRaw);
                Integer sell = parsePriceTenths(sellRaw);
                if (buy == null || sell == null) continue;

                Item base = Item.itemsList[parsed.id];
                if (base == null) continue;

                ShopItem si = new ShopItem();
                si.itemID = parsed.id;
                si.damage = parsed.meta;
                si.buyPriceTenths = buy;
                si.sellPriceTenths = sell;
                si.itemStack = new ItemStack(base, 1, parsed.meta);
                si.displayName = si.itemStack.getDisplayName();
                list.add(si);
            }
        } catch (Exception ignored) {}

        // Notes, temporarily sorted by configuration file order
//        list.sort(Comparator.comparingInt(o -> compositeKey(o.itemID, o.damage)));
        Map<Integer, ShopItem> map = new HashMap<>();
        for (ShopItem s : list) map.put(compositeKey(s.itemID, s.damage), s);

        itemList = list;
        itemMap = map;
        FORCE_SELL_UNLISTED = forceFlag;
        lastLoadTime = System.currentTimeMillis();
    }

    private static Integer parsePriceTenths(String raw) {
        try {
            raw = raw.trim();
            if (raw.contains(".")) {
                String[] p = raw.split("\\.");
                if (p.length != 2) return null;
                int whole = Integer.parseInt(p[0]);
                String fracStr = p[1];
                if (fracStr.length() > 1) fracStr = fracStr.substring(0,1);
                int frac = Integer.parseInt(fracStr);
                if (whole < 0) return whole * 10 - frac;
                return whole * 10 + frac;
            }
            return Integer.parseInt(raw) * 10;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class IdMeta { int id = -1; int meta = 0; }

    /**
     * Parses item identifier strings of the format:
     * - id[:meta]
     * - modid:name[:meta]
     * Returns an IdMeta object with itemID and meta.
     */
    private static IdMeta parseIdentifier(String raw) {
        IdMeta im = new IdMeta();
        // Support numeric id[:meta] format
        if (raw.matches("^\\d+(?::\\d+)?$")) {
            String[] seg = raw.split(":");
            im.id = Integer.parseInt(seg[0]);
            if (seg.length == 2) im.meta = parseIntSafe(seg[1]);
            return im;
        }
        // Support modid:name[:meta] format
        String[] seg = raw.split(":");
        if (seg.length >= 2) {
            String modid = seg[0];
            String name = seg[1];
            int meta = 0;
            if (seg.length == 3 && seg[2].matches("\\d+")) {
                meta = parseIntSafe(seg[2]);
            }
            int found = findItemId(modid, name);
            if (found >= 0) {
                im.id = found;
                im.meta = meta;
            }
        }
        return im;
    }

    /**
     * Returns parsed integer or 0 if not valid.
     */
    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Finds itemID for the given modid and name.
     * Unlocalized names are expected as modid.name.
     */
    private static int findItemId(String modid, String name) {
        String searchUnlocalized = modid + "." + name;
        for (Item it : Item.itemsList) {
            if (it == null) continue;
            String un = it.getUnlocalizedName();
            if (un != null) {
                String trimmed = un.replace("item.", "").replace("tile.", "");
                if (trimmed.equals(searchUnlocalized)) return it.itemID;
                // Fallback: vanilla items may just use name
                if (modid.equals("minecraft") && trimmed.equals(name)) return it.itemID;
            }
        }
        for (Block b : Block.blocksList) {
            if (b == null) continue;
            String un = b.getUnlocalizedName();
            if (un != null) {
                String trimmed = un.replace("item.", "").replace("tile.", "");
                if (trimmed.equals(searchUnlocalized)) return b.blockID;
                if (modid.equals("minecraft") && trimmed.equals(name)) return b.blockID;
            }
        }
        return -1;
        }

    private static void generateDefault(File file) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                boolean createdDir = dir.mkdirs();

                if (!createdDir) {
                    LOGGER.warning("Failed to create directory: " + dir.getAbsolutePath());
                }
            }

            if (!file.exists()) {
                boolean createdFile = file.createNewFile();
                if (!createdFile) {
                    LOGGER.warning("Failed to create file: " + file.getAbsolutePath());
                }
            }

            try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
                w.write("# 系统商店配置文件，支持 id[:meta] 或 minecraft:name[:meta] 格式；价格为一位小数 (buyPrice,sellPrice)\n");
                w.write("# System Shop Config: Supports id[:meta] or minecraft:name[:meta] format; price is one decimal (buyPrice,sellPrice)\n");
                w.write("\n");

                w.write("force_sell_unlisted=false\n");
                w.write("# 是否允许强制回收未设置价格的物品（true为允许，false为禁止）\n");
                w.write("# Whether to allow forced recycling of items without prices (true = allow, false = disable)\n");
                w.write("\n");

                w.write("skyblock_mode=false\n");
                w.write("# 是否开启空岛模式（true为允许，false为禁止）\n");
                w.write("# Whether to enable the Sky block mode (true = allow, false = disable)\n");
                w.write("\n");

                w.write("announceGlobalListing=false\n");
                w.write("# 是否开启全服公告全球商店上架（true为开启，false为关闭）\n");
                w.write("# Announce global shop listings to all players (true = enabled, false = disabled)\n");

                w.write("# id=购买价格,出售价格\n");
                w.write("# id=buy,sell\n");
                w.write("\n");

                w.write("\n");
                w.write("# 前期部分杂物 | Early-stage Miscellaneous\n");
                w.write("17=4.8,1.2 #橡木 Oak Wood\n");
                w.write("17:1=4.8,1.2 #云杉木 Spruce Wood\n");
                w.write("17:2=4.8,1.2 #白桦木 Birch Wood\n");
                w.write("17:3=4.8,1.2 #丛林木 Jungle Wood\n");
                w.write("280=1,0.3 #木棍 Stick\n");
                w.write("37=1,0.3 #蒲公英 Dandelion\n");
                w.write("38=1,0.3 #玫瑰 Rose\n");
                w.write("111=1,0.3 #睡莲 Water Lily\n");
                w.write("31:2=2,0 #蕨 Fern\n");
                w.write("32=2,0 #枯死的灌木 Dead Bush\n");

                w.write("\n");
                w.write("# 打工赚钱 | Work-for-money\n");
                w.write("22587:599=3,0 #未完成的鱼钩 Unfinished Fish Hook\n");
                w.write("22586=0,15 #骨质鱼钩 Bone Fish Hook\n");
                w.write("350=5.6,5.6 #熟鱼 Cooked Fish\n");

                w.write("\n");
                w.write("# 实用物品，装备 | Utility Items, Equipment\n");
                w.write("2564=8,4 #树桩移除器 Stump Remover\n");
                w.write("22580=8,0 #骨棒槌 Bone Club\n");
                w.write("272=18,0 #石剑 Stone Sword\n");
                w.write("22551=60,0 #绿宝石粉 Emerald Powder\n");
                w.write("298=30,15 #皮革帽 Leather Cap\n");
                w.write("299=48,24 #皮革衣 Leather Tunic\n");
                w.write("300=42,21 #皮革裤 Leather Pants\n");
                w.write("301=24,12 #皮革鞋 Leather Boots\n");
                w.write("302=100,30 #锁链帽 Chain Helmet\n");
                w.write("303=160,48 #锁链衣 Chain Chestplate\n");
                w.write("304=140,42 #锁链裤 Chain Leggings\n");
                w.write("305=80,24 #锁链鞋 Chain Boots\n");

                w.write("\n");
                w.write("# 矿 | Ores\n");
                w.write("263=4,2 #煤炭 Coal\n");
                w.write("15=4,0 #铁矿 Iron Ore\n");
                w.write("42=0,108 #铁块 Iron Block\n");
                w.write("14=6,0 #金矿 Gold Ore\n");
                w.write("41=0,162 #金块 Gold Block\n");
                w.write("264=125,50 #钻石 Diamond\n");
                w.write("22537=225,50 #钻石锭 Diamond Ingot\n");
                w.write("388=60,30 #绿宝石 Emerald\n");
                w.write("337=8,0 #粘土 Clay\n");
                w.write("1038=72,36 #粘土块 Clay Block\n");
                w.write("331=2.5,0.5 #红石 Redstone\n");
                w.write("351:4=2.5,0.5 #青金石 Lapis Lazuli\n");
                w.write("348=6,0.6 #萤石粉 Glowstone Dust\n");
                w.write("406=6,1.2 #石英 Nether Quartz\n");

                w.write("\n");
                w.write("# 动物产品 | Animal Products\n");
                w.write("288=8,2 #羽毛 Feather\n");
                w.write("334=16,4 #皮革 Leather\n");
                w.write("2551:0=0,2 #羊毛 Wool\n");
                w.write("2551:1=0,2 #羊毛 Wool\n");
                w.write("2551:2=0,2 #羊毛 Wool\n");
                w.write("2551:3=0,2 #羊毛 Wool\n");
                w.write("2551:4=0,2 #羊毛 Wool\n");
                w.write("2551:5=0,2 #羊毛 Wool\n");
                w.write("2551:6=0,2 #羊毛 Wool\n");
                w.write("2551:7=0,2 #羊毛 Wool\n");
                w.write("2551:8=0,2 #羊毛 Wool\n");
                w.write("2551:9=0,2 #羊毛 Wool\n");
                w.write("2551:10=0,2 #羊毛 Wool\n");
                w.write("2551:11=0,2 #羊毛 Wool\n");
                w.write("2551:12=0,2 #羊毛 Wool\n");
                w.write("2551:13=0,2 #羊毛 Wool\n");
                w.write("2551:14=0,2 #羊毛 Wool\n");
                w.write("2551:15=8,2 #羊毛 Wool\n");
                w.write("180:7=0,72 #粪便块 Manure Block\n");

                w.write("\n");
                w.write("# 部分怪物掉落物 | Some Monster Drops\n");
                w.write("1000=0,12 #腐肉块 Rotten Flesh Block\n");
                w.write("215:15=0,12 #骨块 Bone Block\n");
                w.write("1055=0,32 #苦力怕酶腺块 Creeper Enzyme Block\n");
                w.write("3307=0,32 #蜘蛛眼块 Spider Eye Block\n");
                w.write("215:14=0,22.5 #末影块 Ender Block\n");
                w.write("397:0=0,8 #骷髅头颅 Skeleton Skull\n");
                w.write("397:1=0,32 #凋零骷髅头颅 Wither Skeleton Skull\n");
                w.write("397:2=0,8 #僵尸头颅 Zombie Head\n");
                w.write("397:4=0,8 #苦力怕头颅 Creeper Head\n");
                w.write("367=1.5,0 #腐肉 Rotten Flesh\n");
                w.write("287=8,1.6 #线 String\n");
                w.write("22493=0,1 #硝石 Niter\n");
                w.write("341=4,1 #粘液球 Slimeball\n");
                w.write("368=48,2.4 #末影珍珠 Ender Pearl\n");
                w.write("370=48,8 #恶魂之泪 Ghast Tear\n");
                w.write("376=48,8 #毒液囊 Venom Sac\n");
                w.write("22501=48,8 #女巫疣 Witch Wart\n");
                w.write("22545=36,6 #蝙蝠翼 Bat Wing\n");
                w.write("2560=18,2 #神秘腺体 Mysterious Gland\n");
                w.write("351=3,0.5 #墨囊 Ink Sac\n");
                w.write("369=0,16 #烈焰棒 Blaze Rod\n");

                w.write("\n");
                w.write("# 农作物 | Crops\n");
                w.write("482=0,2 #麻种子 Hemp Seeds\n");
                w.write("483=0,1.2 #麻 Hemp\n");
                w.write("392=0,8 #马铃薯 Potato\n");
                w.write("22597=0,8 #胡萝卜 Carrot\n");
                w.write("170=0,36 #干草块 Hay Bale\n");
                w.write("1167=0,9 #稻草捆 Straw Bale\n");
                w.write("2552=0,1.6 #可可豆 Cocoa Beans\n");
                w.write("22514=0,2.4 #红蘑菇 Red Mushroom\n");
                w.write("22515=0,2.4 #棕蘑菇 Brown Mushroom\n");
                w.write("372=0,1.6 #地狱疣 Nether Wart\n");
                w.write("81=0,0.8 #仙人掌 Cactus\n");
                w.write("103=0,6.4 #西瓜 Melon\n");
                w.write("1006=0,6.4 #南瓜 Pumpkin\n");
                w.write("86=6.4,3.2 #雕刻南瓜 Carved Pumpkin\n");

                w.write("\n");
                w.write("# 高级食物 | Advanced Foods\n");
                w.write("281=0.6,0.6 #碗 Bowl\n");
                w.write("485=0,4.5 #面粉 Flour\n");
                w.write("353=0,1 #糖 Sugar\n");
                w.write("2553=0,3.2 #巧克力 Chocolate\n");
                w.write("357=0,3.2 #曲奇 Cookie\n");
                w.write("488=0,3.2 #甜甜圈 Donut\n");
                w.write("400=0,36 #南瓜派 Pumpkin Pie\n");
                w.write("354=0,40 #蛋糕 Cake\n");
                w.write("282=4.7,3.6 #蘑菇奶油 Mushroom Stew\n");
                w.write("22512=5.6,5.6 #奶油鱼汤 Creamy Fish Soup\n");
                w.write("22521=5.6,4.2 #熟蘑菇煎蛋卷 Cooked Mushroom Omelette\n");
                w.write("22523=5.6,4.2 #熟炒蛋 Cooked Scrambled Eggs\n");
                w.write("22503=9,6.7 #三明治 Sandwich\n");
                w.write("22511=13.4,10.2 #鸡肉汤 Chicken Soup\n");
                w.write("22513=17.6,13.3 #丰盛炖菜 Hearty Stew\n");

                w.write("\n");
                w.write("# 初级工业产品 | Basic Industrial Products\n");
                w.write("484=0,0.2 #齿轮 Gear\n");
                w.write("22576=8,8 #筚板 Bamboo Board\n");
                w.write("289=0,3.2 #火药 Gunpowder\n");
                w.write("215:3=0,36 #浓缩地狱火块 Concentrated Netherfire Block\n");
                w.write("215:6=0,30 #麻绳圈 Hemp Rope Coil\n");
                w.write("215:4=0,125 #衬垫块 Padding Block\n");
                w.write("215:7=0,9 #燧石块 Flint Block\n");

                w.write("\n");
                w.write("# 中级工业产品 | Intermediate Industrial Products\n");
                w.write("46=32,16 #炸药桶 TNT Barrel\n");
                w.write("215:5=0,72 #肥皂块 Soap Block\n");
                w.write("503=0,10 #皮胶 Hide Glue\n");
                w.write("254:0=70,35 #种植盆 Planter Pot\n");
                w.write("255=0,25 #花瓶 Vase\n");
                w.write("22481=0,256 #下界孢子 Nether Spores\n");
                w.write("213:2=0,4 #血木树苗 Bloodwood Sapling\n");
                w.write("212=0,1.6 #血木 Bloodwood\n");
                w.write("22547:4=0,0.4 #血木树皮 Bloodwood Bark\n");
                w.write("539=0,0.4 #灵魂之尘 Soul Dust\n");

                w.write("\n");
                w.write("# 高级工业产品\n");
                w.write("215:9=0,4 #白色石头 White Stone\n");
                w.write("2555=0,20 #熔魂剂 Soul Melter\n");
                w.write("22492=0,1 #硫磺 Sulfur\n");
                w.write("209=0,1200 #熔魂钢块\n");

                w.write("\n");
                w.write("# 功能类方块 | Functional Blocks\n");
                w.write("1054=80,40 #大篮子 Large Basket\n");
                w.write("1034=720,0 #工作台 Workbench\n");
                w.write("1002=480,120 #未激活的熔魂钢砧 Unactivated Soul Steel Anvil\n");
                w.write("379=480,120 #酿造台 Brewing Stand\n");
                w.write("116=720,180 #附魔台 Enchanting Table\n");
                w.write("176=720,180 #龙之容器 Dragon Vessel\n");

                w.write("\n");
                w.write("# 药水\n");
                w.write("373:8258=80,0 #迅捷药水 Potion of Swiftness\n");
                w.write("373:8226=64,0 #迅捷药水 Potion of Swiftness\n");
                w.write("373:16450=80,0 #迅捷药水 Potion of Swiftness\n");
                w.write("373:16418=64,0 #迅捷药水 Potion of Swiftness\n");

                w.write("\n");
                w.write("# 回收音乐唱片，命名牌 | Music Discs & Name Tag Recycling\n");
                w.write("2256=100,32 #音乐唱片 (13) Music Disc (13)\n");
                w.write("2257=100,32 #音乐唱片 (Cat) Music Disc (Cat)\n");
                w.write("2258=100,32 #音乐唱片 (Blocks) Music Disc (Blocks)\n");
                w.write("2259=100,32 #音乐唱片 (Chirp) Music Disc (Chirp)\n");
                w.write("2260=100,32 #音乐唱片 (Far) Music Disc (Far)\n");
                w.write("2261=100,32 #音乐唱片 (Mall) Music Disc (Mall)\n");
                w.write("2262=100,32 #音乐唱片 (Mellohi) Music Disc (Mellohi)\n");
                w.write("2263=100,32 #音乐唱片 (Stal) Music Disc (Stal)\n");
                w.write("2264=100,32 #音乐唱片 (Strad) Music Disc (Strad)\n");
                w.write("2265=100,32 #音乐唱片 (Ward) Music Disc (Ward)\n");
                w.write("2266=100,32 #音乐唱片 (11) Music Disc (11)\n");
                w.write("2267=100,32 #音乐唱片 (Wait) Music Disc (Wait)\n");
                w.write("421=0,80 #命名牌 Name Tag\n");

                w.write("\n");
                w.write("# 卷轴 | Scrolls\n");
                w.write("384=4.5,4.5 #附魔之瓶 Bottle of Enchanting\n");
                w.write("22479:0=1600,160 #保护，史莱姆1/1000 Protection, Slime 1/1000\n");
                w.write("22479:1=800,160 #火焰保护，僵尸猪人1/1000 Fire Protection, Zombie Pigman 1/1000\n");
                w.write("22479:2=1600,160 #摔落保护，蝙蝠1/250 Fall Protection, Bat 1/250\n");
                w.write("22479:3=800,160 #爆炸保护，苦力怕1/1000 Explosion Protection, Creeper 1/1000\n");
                w.write("22479:4=800,160 #弹射物保护，主世界中的骷髅1/1000 Projectile Protection, Overworld Skeleton 1/1000\n");
                w.write("22479:5=800,160 #水下呼吸，鱿鱼1/250 Water Breathing, Squid 1/250\n");
                w.write("22479:6=1600,160 #水下速掘，女巫1/1000 Underwater Mining, Witch 1/1000\n");
                w.write("22479:7=800,0 #荆棘，无法获得 Thorns, Unobtainable\n");
                w.write("22479:17=1600,160 #亡灵杀手，僵尸1/1000 Undead Slayer, Zombie 1/1000\n");
                w.write("22479:18=400,160 #节肢杀手，蜘蛛，洞穴蜘蛛1/1000 Arthropod Slayer, Spider & Cave Spider 1/1000\n");
                w.write("22479:19=400,160 #击退，凋灵 Knockback, Wither\n");
                w.write("22479:20=400,160 #火焰附加，岩浆怪1/250 Fire Aspect, Magma Cube 1/250\n");
                w.write("22479:32=1600,160 #效率，末地中的蠹虫1/1000 Efficiency, End Silverfish 1/1000\n");
                w.write("22479:33=1600,160 #精准采集，末影人1/1000 Silk Touch, Enderman 1/1000\n");
                w.write("22479:49=1600,160 #冲击，恶魂1/500 Impact, Ghast 1/500\n");
                w.write("22479:50=1600,160 #火矢，烈焰人1/500 Flame Arrow, Blaze 1/500\n");
                w.write("22479:51=1600,160 #无限，地狱中的骷髅1/1000 Infinity, Nether Skeleton 1/1000\n");
                w.write("\n");

                w.write("\n");
                w.write("# 空岛模式商品 | skyblock_mode_items\n");
                w.write("# 以下商品仅在空岛模式下启用\n");
                w.write("# The following products are only available in Sky Island mode\n");

                w.write("\n");
                w.write("# 前期部分杂物 | Early-stage Miscellaneous\n");
                w.write("1158:7=2.5,2 #成熟橡树苗 Mature Oak Sapling\n");
                w.write("2564=1.5,1 #树桩移除器 Stump Remover\n");
                w.write("17=8,2.4 #橡木 Oak Wood\n");
                w.write("17:1=0,3.6 #云杉木 Spruce Wood\n");
                w.write("17:2=0,3.6 #白桦木 Birch Wood\n");
                w.write("17:3=0,2.4 #丛林木 Jungle Wood\n");
                w.write("22547:0=0,0.2 #橡树皮 Oak Bark\n");
                w.write("22547:1=0,1.2 #云杉树皮 Spruce Bark\n");
                w.write("22547:2=0,1.2 #白桦树皮 Birch Bark\n");
                w.write("22547:3=0,0.2 #丛林树皮 Jungle Bark\n");
                w.write("532=0,0.1 #木屑 Sawdust\n");
                w.write("280=1.5,0.6 #木棍 Stick\n");
                w.write("1158:0=0,2 #橡树苗 Oak Sapling\n");
                w.write("1159=160,2 #云杉树苗 Spruce Sapling\n");
                w.write("1160=160,2 #白桦树苗 Birch Sapling\n");
                w.write("1161=160,2 #丛林树苗 Jungle Sapling\n");
                w.write("37=1,0.4 #蒲公英 Dandelion\n");
                w.write("38=1,0.4 #玫瑰 Rose\n");
                w.write("111=1,0.4 #睡莲 Water Lily\n");
                w.write("31:2=2,0 #蕨 Fern\n");
                w.write("32=2,0 #枯死的灌木 Dead Bush\n");

                w.write("\n");
                w.write("# 打工赚钱 | Work-for-money\n");
                w.write("22587:599=3,0 #未完成的鱼钩 Unfinished Fish Hook\n");
                w.write("22586=0,15 #骨质鱼钩 Bone Fish Hook\n");
                w.write("349=5.6,4.8 #生鱼 Raw Fish\n");
                w.write("350=5.6,5.6 #熟鱼 Cooked Fish\n");

                w.write("\n");
                w.write("# 实用物品，装备 | Utility Items, Equipment\n");
                w.write("272=18,0 #石剑 Stone Sword\n");
                w.write("298=40,15 #皮革帽 Leather Cap\n");
                w.write("299=64,24 #皮革衣 Leather Tunic\n");
                w.write("300=56,21 #皮革裤 Leather Pants\n");
                w.write("301=32,12 #皮革鞋 Leather Boots\n");
                w.write("302=90,0 #锁链帽 Chain Helmet\n");
                w.write("303=144,0 #锁链衣 Chain Chestplate\n");
                w.write("304=126,0 #锁链裤 Chain Leggings\n");
                w.write("305=72,0 #锁链鞋 Chain Boots\n");

                w.write("\n");
                w.write("#地形方块 | Terrain Blocks\n");
                w.write("1:0=5,0 #石头 Stone\n");
                w.write("1:1=5,0 #深板岩 Deepslate\n");
                w.write("1:2=5,0 #黑石 Blackstone\n");
                w.write("13=2,0 #沙砾 Gravel\n");
                w.write("2=24,0 #草方块 Grass Block\n");
                w.write("1011=20,0 #松散泥土 Loose Dirt\n");
                w.write("82=16,0 #粘土矿 Clay Ore\n");
                w.write("48=6,0 #苔石 Mossy Cobblestone\n");
                w.write("49=50,0 #黑曜石 Obsidian\n");
                w.write("87=0.3,0 #地狱岩 Netherrack\n");
                w.write("88=8,0 #灵魂沙 Soul Sand\n");
                w.write("79=80,0 #冰 Ice\n");
                w.write("327=240,0 #岩浆桶 Lava Bucket\n");

                w.write("\n");
                w.write("# 矿 | Ores\n");
                w.write("263=4,2 #煤炭 Coal\n");
                w.write("173=36,18 #煤炭块 Coal Block\n");
                w.write("22571=4,0 #粗铁矿 Raw Iron\n");
                w.write("42=0,108 #铁块 Iron Block\n");
                w.write("22574=6,0 #粗金矿 Raw Gold\n");
                w.write("41=0,162 #金块 Gold Block\n");
                w.write("264=125,125 #钻石 Diamond\n");
                w.write("22537=225,125 #钻石锭 Diamond Ingot\n");
                w.write("388=50,50 #绿宝石 Emerald\n");
                w.write("133=450,450 #绿宝石块 Emerald Block\n");
                w.write("337=4,0 #粘土 Clay\n");
                w.write("1038=36,36 #粘土块 Clay Block\n");
                w.write("331=2.5,0 #红石 Redstone\n");
                w.write("152=22.5,0 #红石块 Redstone Block\n");
                w.write("351:4=2.5,0 #青金石 Lapis Lazuli\n");
                w.write("22=22.5,0 #青金石块 Lapis Block\n");
                w.write("348=6,0 #萤石粉 Glowstone Dust\n");
                w.write("89=0,2 #萤石 Glowstone\n");
                w.write("406=6,0 #石英 Nether Quartz\n");
                w.write("155:0=0,4 #石英块 Quartz Block\n");

                w.write("\n");
                w.write("# 动物产品 | Animal Products\n");
                w.write("288=8,2 #羽毛 Feather\n");
                w.write("334=32,8 #皮革 Leather\n");
                w.write("2551:0=0,2 #羊毛 Wool\n");
                w.write("2551:1=0,2 #羊毛 Wool\n");
                w.write("2551:2=0,2 #羊毛 Wool\n");
                w.write("2551:3=0,2 #羊毛 Wool\n");
                w.write("2551:4=0,2 #羊毛 Wool\n");
                w.write("2551:5=0,2 #羊毛 Wool\n");
                w.write("2551:6=0,2 #羊毛 Wool\n");
                w.write("2551:7=0,2 #羊毛 Wool\n");
                w.write("2551:8=0,2 #羊毛 Wool\n");
                w.write("2551:9=0,2 #羊毛 Wool\n");
                w.write("2551:10=0,2 #羊毛 Wool\n");
                w.write("2551:11=0,2 #羊毛 Wool\n");
                w.write("2551:12=0,2 #羊毛 Wool\n");
                w.write("2551:13=0,2 #羊毛 Wool\n");
                w.write("2551:14=0,2 #羊毛 Wool\n");
                w.write("2551:15=8,2 #羊毛 Wool\n");
                w.write("344=16,8 #鸡蛋 Egg\n");
                w.write("320=0,5.4 #熟猪肉 Cooked Porkchop\n");
                w.write("364=0,5.4 #熟牛肉 Cooked Beef\n");
                w.write("366=0,4.2 #熟鸡肉 Cooked Chicken\n");
                w.write("22500=0,4.2 #熟羊肉 Cooked Mutton\n");
                w.write("22610=0,5.4 #熟马肉 Cooked Horse Meat\n");
                w.write("480=0,5.4 #熟狼肉 Cooked Wolf Meat\n");
                w.write("2562=0,40 #熟野兽肝 Cooked Wild Liver\n");
                w.write("180:7=0,72 #粪便块 Dung Block\n");

                w.write("\n");
                w.write("# 部分怪物掉落物 | Some Monster Drops\n");
                w.write("1000=0,6 #腐肉块 Rotten Flesh Block\n");
                w.write("215:15=0,6 #骨块 Bone Block\n");
                w.write("1055=0,16 #苦力怕酶腺块 Creeper Enzyme Block\n");
                w.write("3307=0,16 #蜘蛛眼块 Spider Eye Block\n");
                w.write("215:14=0,22.5 #末影块 Ender Block\n");
                w.write("397:0=0,4 #骷髅头颅 Skeleton Skull\n");
                w.write("397:1=0,16 #凋零骷髅头颅 Wither Skeleton Skull\n");
                w.write("397:2=0,4 #僵尸头颅 Zombie Head\n");
                w.write("397:4=0,4 #苦力怕头颅 Creeper Head\n");
                w.write("367=1,0 #腐肉 Rotten Flesh\n");
                w.write("352=3.6,0 #骨头 Bone\n");
                w.write("287=8,0.8 #线 String\n");
                w.write("1037=16,3.2 #蜘蛛网 Cobweb\n");
                w.write("22524=16,0 #苦力怕酶腺 Creeper Enzyme\n");
                w.write("22493=0,0.5 #硝石 Saltpeter\n");
                w.write("341=4,0.5 #粘液球 Slimeball\n");
                w.write("368=48,2.4 #末影珍珠 Ender Pearl\n");
                w.write("370=48,8 #恶魂之泪 Ghast Tear\n");
                w.write("376=48,8 #毒液囊 Venom Sac\n");
                w.write("22501=48,8 #女巫疣 Witch Wart\n");
                w.write("22545=36,6 #蝙蝠翼 Bat Wing\n");
                w.write("2560=18,2 #神秘腺体 Mysterious Gland\n");
                w.write("351=3,0.5 #墨囊 Ink Sac\n");
                w.write("369=0,16 #烈焰棒 Blaze Rod\n");

                w.write("\n");
                w.write("# 农作物 | Crops\n");
                w.write("22596=16,16 #甘蔗根 Sugarcane Root\n");
                w.write("338=0,1 #甘蔗 Sugarcane\n");
                w.write("482=8,2 #麻种子 Hemp Seeds\n");
                w.write("483=0,1.2 #麻 Hemp\n");
                w.write("361=160,0 #南瓜种子 Pumpkin Seeds\n");
                w.write("362=160,0 #西瓜种子 Melon Seeds\n");
                w.write("22591=240,0 #小麦种子 Wheat Seeds\n");
                w.write("22598=480,0 #胡萝卜种子 Carrot Seeds\n");
                w.write("392=480,8 #马铃薯 Potato\n");
                w.write("22597=0,8 #胡萝卜 Carrot\n");
                w.write("170=0,36 #干草块 Hay Bale\n");
                w.write("1167=0,9 #稻草捆 Straw Bale\n");
                w.write("2552=80,1.6 #可可豆 Cocoa Beans\n");
                w.write("22514=80,2.4 #红蘑菇 Red Mushroom\n");
                w.write("22515=80,2.4 #棕蘑菇 Brown Mushroom\n");
                w.write("372=0,1.6 #地狱疣 Nether Wart\n");
                w.write("81=80,0.8 #仙人掌 Cactus\n");
                w.write("103=0,6.4 #西瓜 Melon\n");
                w.write("1006=0,6.4 #南瓜 Pumpkin\n");
                w.write("86=6.4,3.2 #雕刻南瓜 Carved Pumpkin\n");

                w.write("\n");
                w.write("# 高级食物 | Advanced Foods\n");
                w.write("281=0.5,0.5 #碗 Bowl\n");
                w.write("485=0,4.2 #面粉 Flour\n");
                w.write("353=0,1.2 #糖 Sugar\n");
                w.write("2553=0,3.2 #巧克力 Chocolate\n");
                w.write("357=0,3.2 #曲奇 Cookie\n");
                w.write("488=0,3.2 #甜甜圈 Donut\n");
                w.write("400=0,36 #南瓜派 Pumpkin Pie\n");
                w.write("354=0,40 #蛋糕 Cake\n");
                w.write("282=4.6,3.5 #蘑菇奶油 Mushroom Stew\n");
                w.write("22512=5.6,5.6 #奶油鱼汤 Creamy Fish Soup\n");
                w.write("22521=5.6,4.2 #熟蘑菇煎蛋卷 Cooked Mushroom Omelette\n");
                w.write("22523=5.6,4.2 #熟炒蛋 Cooked Scrambled Eggs\n");
                w.write("22503=9,6.7 #三明治 Sandwich\n");
                w.write("22511=13.3,10.1 #鸡肉汤 Chicken Soup\n");
                w.write("22513=17.5,13.2 #丰盛炖菜 Hearty Stew\n");
                w.write("260=24,12 #苹果 Apple\n");
                w.write("322:0=200,160 #金苹果 Golden Apple\n");
                w.write("322:1=1800,1440 #附魔金苹果 Enchanted Golden Apple\n");

                w.write("\n");
                w.write("# 初级工业产品 | Basic Industrial Products\n");
                w.write("484=0,0.2 #齿轮 Gear\n");
                w.write("22576=8,8 #筚板 Bamboo Board\n");
                w.write("289=0,3.2 #火药 Gunpowder\n");
                w.write("215:3=0,36 #浓缩地狱火块 Condensed Nether Fire Block\n");
                w.write("1166=0,30 #地狱煤块 Nether Coal Block\n");
                w.write("215:6=0,30 #麻绳圈 Hemp Rope Coil\n");
                w.write("215:4=0,125 #衬垫块 Padding Block\n");
                w.write("215:7=0,9 #燧石块 Flint Block\n");

                w.write("\n");
                w.write("# 中级工业产品 | Intermediate Industrial Products\n");
                w.write("46=90,45 #炸药桶 TNT Barrel\n");
                w.write("215:5=0,72 #肥皂块 Soap Block\n");
                w.write("1165=120,108 #木炭块 Charcoal Block\n");
                w.write("503=0,10 #皮胶 Hide Glue\n");
                w.write("254:0=60,30 #种植盆 Planter Pot\n");
                w.write("255=0,24 #花瓶 Vase\n");
                w.write("22481=0,256 #下界孢子 Nether Spore\n");
                w.write("213:2=0,4 #血木树苗 Bloodwood Sapling\n");
                w.write("212=0,4.8 #血木 Bloodwood\n");
                w.write("22547:4=0,2.4 #血木树皮 Bloodwood Bark\n");
                w.write("539=0,0.8 #灵魂之尘 Soul Dust\n");

                w.write("\n");
                w.write("# 高级工业产品 | Advanced Industrial Products\n");
                w.write("215:10=8,3.6 #白色圆石 White Cobblestone\n");
                w.write("215:9=8,4 #白色石头 White Stone\n");
                w.write("22492=4,1 #硫磺 Sulfur\n");
                w.write("2555=0,20 #熔魂剂 Soul Melter\n");
                w.write("209=0,1200 #熔魂钢块 Soulsteel Block\n");
                w.write("138=4800,800 #信标 Beacon\n");

                w.write("\n");
                w.write("# 功能类方块 | Functional Blocks\n");
                w.write("1054=80,40 #大篮子 Large Basket\n");
                w.write("1034=720,0 #工作台 Workbench\n");
                w.write("1002=480,120 #未激活的熔魂钢砧 Unactivated Soul Steel Anvil\n");
                w.write("379=480,120 #酿造台 Brewing Stand\n");
                w.write("116=720,180 #附魔台 Enchanting Table\n");
                w.write("176=720,180 #龙之容器 Dragon Vessel\n");
                w.write("403=120,4 #古代手稿 Ancient Manuscript\n");

                w.write("\n");
                w.write("# 药水 | Potions\n");
                w.write("373:8261=24,0 #1级治疗 Healing I\n");
                w.write("373:8230=24,0 #3m夜视 3m Night Vision\n");
                w.write("373:8227=48,0 #3m抗火 3m Fire Resistance\n");
                w.write("373:8201=48,0 #3m力量 3m Strength\n");
                w.write("373:8258=80,0 #8m速度 8m Swiftness\n");
                w.write("373:8226=64,0 #速度2 Swiftness II\n");
                w.write("373:16450=80,0 #6m喷溅速度 6m Splash Swiftness\n");
                w.write("373:16418=64,0 #喷溅速度2 Splash Swiftness II\n");

                w.write("\n");
                w.write("# 回收音乐唱片，命名牌 | Music Discs & Name Tag Recycling\n");
                w.write("2256=100,32 #唱片 Cat Disc Cat\n");
                w.write("2257=100,32 #唱片 Blocks Disc Blocks\n");
                w.write("2258=100,32 #唱片 Chirp Disc Chirp\n");
                w.write("2259=100,32 #唱片 Far Disc Far\n");
                w.write("2260=100,32 #唱片 Mall Disc Mall\n");
                w.write("2261=100,32 #唱片 Mellohi Disc Mellohi\n");
                w.write("2262=100,32 #唱片 Stal Disc Stal\n");
                w.write("2263=100,32 #唱片 Strad Disc Strad\n");
                w.write("2264=100,32 #唱片 Ward Disc Ward\n");
                w.write("2265=100,32 #唱片 11 Disc 11\n");
                w.write("2266=100,32 #唱片 Wait Disc Wait\n");
                w.write("2267=100,32 #唱片 Pigstep Disc Pigstep\n");
                w.write("421=0,80 #命名牌 Name Tag\n");

                w.write("\n");
                w.write("# 卷轴 | Scrolls\n");
                w.write("340=48,24 #书 Book\n");
                w.write("384=4,4 #附魔之瓶 Bottle of Enchanting\n");
                w.write("22479:0=1600,80 #保护，史莱姆1/1000 Protection, Slime 1/1000\n");
                w.write("22479:1=800,80 #火焰保护，僵尸猪人1/1000 Fire Protection, Zombie Pigman 1/1000\n");
                w.write("22479:2=1600,80 #摔落保护，蝙蝠1/250 Fall Protection, Bat 1/250\n");
                w.write("22479:3=800,80 #爆炸保护，苦力怕1/1000 Explosion Protection, Creeper 1/1000\n");
                w.write("22479:4=800,80 #弹射物保护，主世界中的骷髅1/1000 Projectile Protection, Overworld Skeleton 1/1000\n");
                w.write("22479:5=800,80 #水下呼吸，鱿鱼1/250 Water Breathing, Squid 1/250\n");
                w.write("22479:6=1600,80 #水下速掘，女巫1/1000 Underwater Mining, Witch 1/1000\n");
                w.write("22479:7=800,0 #荆棘，无法获得 Thorns, Unobtainable\n");
                w.write("22479:17=1600,80 #亡灵杀手，僵尸1/1000 Undead Slayer, Zombie 1/1000\n");
                w.write("22479:18=400,80 #节肢杀手，蜘蛛，洞穴蜘蛛1/1000 Arthropod Slayer, Spider & Cave Spider 1/1000\n");
                w.write("22479:19=400,80 #击退，凋灵 Knockback, Wither\n");
                w.write("22479:20=400,80 #火焰附加，岩浆怪1/250 Fire Aspect, Magma Cube 1/250\n");
                w.write("22479:32=1600,80 #效率，末地中的蠹虫1/1000 Efficiency, End Silverfish 1/1000\n");
                w.write("22479:33=1600,80 #精准采集，末影人1/1000 Silk Touch, Enderman 1/1000\n");
                w.write("22479:49=1600,80 #冲击，恶魂1/500 Impact, Ghast 1/500\n");
                w.write("22479:50=1600,80 #火矢，烈焰人1/500 Flame Arrow, Blaze 1/500\n");
                w.write("22479:51=1600,80 #无限，地狱中的骷髅1/1000 Infinity, Nether Skeleton 1/1000\n");

                w.write("\n");
                w.write("# 刷怪蛋 | Spawn Eggs\n");
                w.write("383:90=240,0 #猪刷怪蛋 Pig Spawn Egg\n");
                w.write("383:91=320,0 #羊刷怪蛋 Sheep Spawn Egg\n");
                w.write("383:92=400,0 #牛刷怪蛋 Cow Spawn Egg\n");
                w.write("383:100=400,0 #马刷怪蛋 Horse Spawn Egg\n");
                w.write("383:95=240,0 #狼刷怪蛋 Wolf Spawn Egg\n");
            }
        } catch (Exception ignored) {}
    }

    public static int compositeKey(int id, int dmg) {
        return ((id & 0xFFFF) << 16) | (dmg & 0xFFFF);
    }
}