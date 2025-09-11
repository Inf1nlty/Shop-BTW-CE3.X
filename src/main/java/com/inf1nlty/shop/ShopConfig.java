package com.inf1nlty.shop;

import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

import java.io.*;
import java.util.*;

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

    private ShopConfig() {}

    public static synchronized ShopItem get(int id, int dmg) {
        ensure();
        ShopItem exact = itemMap.get(compositeKey(id, dmg));
        if (exact != null) return exact;
        return itemMap.get(compositeKey(id, 0));
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
            if (dir != null && !dir.exists()) dir.mkdirs();
            if (!file.exists()) file.createNewFile();
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

                w.write("# id=buy,sell\n");
                w.write("# id=购买价格,出售价格\n");
                w.write("\n");

                w.write("# 前期部分杂物 | Early-stage Miscellaneous\n");
                w.write("17=4,1.2 #橡木 Oak Wood\n");
                w.write("17:1=4,1.2 #云杉木 Spruce Wood\n");
                w.write("17:2=4,1.2 #白桦木 Birch Wood\n");
                w.write("17:3=4,1.2 #丛林木 Jungle Wood\n");
                w.write("280=1,0.3 #木棍 Stick\n");
                w.write("22503=9,0.0 #三明治 Sandwich\n");
                w.write("37=1,0.4 #蒲公英 Dandelion\n");
                w.write("38=1,0.4 #玫瑰 Rose\n");
                w.write("111=1,0.4 #睡莲 Water Lily\n");

                w.write("\n");
                w.write("# 前期动物产品 | Early-stage Animal Products\n");
                w.write("288=4,2.0 #羽毛 Feather\n");
                w.write("334=16,8.0 #皮革 Leather\n");
                w.write("2551=8,4.0 #羊毛 Wool\n");
                w.write("2551:3=8,4.0 #羊毛 Wool\n");
                w.write("2551:7=8,4.0 #羊毛 Wool\n");
                w.write("2551:8=8,4.0 #羊毛 Wool\n");
                w.write("2551:9=8,4.0 #羊毛 Wool\n");
                w.write("2551:10=8,4.0 #羊毛 Wool\n");
                w.write("2551:12=8,4.0 #羊毛 Wool\n");
                w.write("2551:15=8,4.0 #羊毛 Wool\n");

                w.write("\n");
                w.write("# 实用物品，装备 | Utility Items, Equipment\n");
                w.write("22580=8,0.0 #骨棒槌 Bone Club\n");
                w.write("272=18,0.0 #石剑 Stone Sword\n");
                w.write("2564=8,4.0 #树桩移除器 Stump Remover\n");
                w.write("22551=80,0.0 #绿宝石粉 Emerald Powder\n");
                w.write("46=32,16.0 #炸药桶 TNT Barrel\n");
                w.write("298=30,0.0 #皮革帽 Leather Cap\n");
                w.write("299=48,0.0 #皮革衣 Leather Tunic\n");
                w.write("300=42,0.0 #皮革裤 Leather Pants\n");
                w.write("301=24,0.0 #皮革鞋 Leather Boots\n");
                w.write("302=100,0.0 #锁链帽 Chain Helmet\n");
                w.write("303=160,0.0 #锁链衣 Chain Chestplate\n");
                w.write("304=140,0.0 #锁链裤 Chain Leggings\n");
                w.write("305=80,0.0 #锁链鞋 Chain Boots\n");
                w.write("384=3,0.0 #附魔之瓶 Bottle of Enchanting\n");

                w.write("\n");
                w.write("# 回收音乐唱片，命名牌 | Music Discs & Name Tag Recycling\n");
                w.write("2256=0,32.0\n");
                w.write("2257=0,32.0\n");
                w.write("2258=0,32.0\n");
                w.write("2259=0,32.0\n");
                w.write("2260=0,32.0\n");
                w.write("2261=0,32.0\n");
                w.write("2262=0,32.0\n");
                w.write("2263=0,32.0\n");
                w.write("2264=0,32.0\n");
                w.write("2265=0,32.0\n");
                w.write("2266=0,32.0\n");
                w.write("2267=0,32.0\n");
                w.write("421=0,80.0\n");

                w.write("\n");
                w.write("# 矿 | Ores\n");
                w.write("263=4,2.0 #煤炭 Coal\n");
                w.write("14=4,2.0 #金矿 Gold Ore\n");
                w.write("15=4,2.0 #铁矿 Iron Ore\n");
                w.write("265=0,10.0 #铁锭 Iron Ingot\n");
                w.write("266=0,20.0 #金锭 Gold Ingot\n");
                w.write("264=80,80.0 #钻石 Diamond\n");
                w.write("388=80,20.0 #绿宝石 Emerald\n");
                w.write("331=2.5,0.5 #红石 Redstone\n");
                w.write("351:4=2.5,0.5 #青金石 Lapis Lazuli\n");

                w.write("\n");
                w.write("# 部分怪物掉落物 | Some Monster Drops\n");
                w.write("287=10,2.0 #线 String\n");
                w.write("341=4,1.0 #粘液球 Slimeball\n");
                w.write("1000=0,9.0 #腐肉块 Rotten Flesh Block\n");
                w.write("215:15=0,18.0 #骨块 Bone Block\n");
                w.write("1055=0,32.0 #苦力怕酶腺块 Creeper Enzyme Block\n");
                w.write("1699:7=0,1.0 #硝石块 Saltpeter Block\n");
                w.write("3307=0,32.0 #蜘蛛眼块 Spider Eye Block\n");
                w.write("215:14=0,9.0 #末影块 Ender Block\n");

                w.write("\n");
                w.write("# 较难获取物品 | Hard-to-get Items\n");
                w.write("368=32,1.0 #末影珍珠 Ender Pearl\n");
                w.write("370=32,0.0 #恶魂之泪 Ghast Tear\n");
                w.write("376=32,0.0 #毒液囊 Venom Sac\n");
                w.write("22501=32,0.0 #女巫疣 Witch Wart\n");
                w.write("22545=24,0.0 #蝙蝠翼 Bat Wing\n");
                w.write("2560=12,0.0 #神秘腺体 Mysterious Gland\n");

                w.write("\n");
                w.write("# 科技树进度有关 | Tech Progression Related\n");
                w.write("369=0,8.0 #烈焰棒 Blaze Rod\n");

                w.write("\n");
                w.write("# 其他材料 | Other Materials\n");
                w.write("22576=8,8.0 #筚板 Bamboo Board\n");
                w.write("2555=0,4.0 #熔魂剂 Soul Melter\n");
                w.write("22492=8,2.0 #硫磺 Sulfur\n");
                w.write("215:9=8,2.0 #白色石头 White Stone\n");

                w.write("\n");
                w.write("# 打工赚钱 | Work-for-money\n");
                w.write("22587:599=4,0.0\n");
                w.write("22586=0,12.0\n");

                w.write("\n");
                w.write("# 功能类方块 | Functional Blocks\n");
                w.write("1054=80,40.0 #大篮子 Large Basket\n");
                w.write("1034=500,0.0 #工作台 Workbench\n");
                w.write("1002=480,120.0 #未激活的熔魂钢砧 Unactivated Soul Steel Anvil\n");
                w.write("116=720,180.0 #附魔台 Enchanting Table\n");
                w.write("176=720,180.0 #龙之容器 Dragon Vessel\n");
                w.write("379=480,120.0 #酿造台 Brewing Stand\n");

                w.write("\n");
                w.write("# 卷轴 | Scrolls\n");
                w.write("22479:0=1600,160.0 #保护，史莱姆1/1000 Protection, Slime 1/1000\n");
                w.write("22479:1=800,160.0 #火焰保护，僵尸猪人1/1000 Fire Protection, Zombie Pigman 1/1000\n");
                w.write("22479:2=1600,160.0 #摔落保护，蝙蝠1/250 Fall Protection, Bat 1/250\n");
                w.write("22479:3=800,160.0 #爆炸保护，苦力怕1/1000 Explosion Protection, Creeper 1/1000\n");
                w.write("22479:4=800,160.0 #弹射物保护，主世界中的骷髅1/1000 Projectile Protection, Overworld Skeleton 1/1000\n");
                w.write("22479:5=800,160.0 #水下呼吸，鱿鱼1/250 Water Breathing, Squid 1/250\n");
                w.write("22479:6=1600,160.0 #水下速掘，女巫1/1000 Underwater Mining, Witch 1/1000\n");
                w.write("22479:7=800,0.0 #荆棘，无法获得 Thorns, Unobtainable\n");
                w.write("22479:17=1600,160.0 #亡灵杀手，僵尸1/1000 Undead Slayer, Zombie 1/1000\n");
                w.write("22479:18=400,160.0 #节肢杀手，蜘蛛，洞穴蜘蛛1/1000 Arthropod Slayer, Spider & Cave Spider 1/1000\n");
                w.write("22479:19=400,160.0 #击退，凋灵 Knockback, Wither\n");
                w.write("22479:20=400,160.0 #火焰附加，岩浆怪1/250 Fire Aspect, Magma Cube 1/250\n");
                w.write("22479:32=1600,160.0 #效率，末地中的蠹虫1/1000 Efficiency, End Silverfish 1/1000\n");
                w.write("22479:33=1600,160.0 #精准采集，末影人1/1000 Silk Touch, Enderman 1/1000\n");
                w.write("22479:49=1600,160.0 #冲击，恶魂1/500 Impact, Ghast 1/500\n");
                w.write("22479:50=1600,160.0 #火矢，烈焰人1/500 Flame Arrow, Blaze 1/500\n");
                w.write("22479:51=1600,160.0 #无限，地狱中的骷髅1/1000 Infinity, Nether Skeleton 1/1000\n");

                w.write("\n");
                w.write("# 空岛模式商品 | skyblock_mode_items\n");
                w.write("# 以下商品仅在空岛模式下启用\n");

                w.write("\n");
                w.write("# 前期部分杂物 | Early-stage Miscellaneous\n");
                w.write("17=12,2.4 #橡木 Oak Wood\n");
                w.write("17:1=12,2.4 #云杉木 Spruce Wood\n");
                w.write("17:2=12,2.4 #白桦木 Birch Wood\n");
                w.write("17:3=12,2.4 #丛林木 Jungle Wood\n");
                w.write("280=2,0.6 #木棍 Stick\n");
                w.write("22503=9,0.0 #三明治 Sandwich\n");
                w.write("37=1,0.4 #蒲公英 Dandelion\n");
                w.write("38=1,0.4 #玫瑰 Rose\n");
                w.write("111=1,0.4 #睡莲 Water Lily\n");
                w.write("2=32,0.0 #草方块\n");
                w.write("1158=0,2.0 #橡树苗\n");
                w.write("1158:7=4,2.0 #橡树苗\n");
                w.write("1159=160,2.0 #云杉树苗\n");
                w.write("1160=160,2.0 #白桦树苗\n");
                w.write("1161=160,2.0 #丛林树苗\n");
                w.write("81=160,0.4 #仙人掌\n");
                w.write("361=320,0.4 #南瓜种子\n");
                w.write("362=320,0.4 #西瓜种子\n");
                w.write("392=320,4.0 #马铃薯\n");
                w.write("22597=320,4.0 #胡萝卜\n");
                w.write("22591=320,2.0 #小麦种子\n");
                w.write("2552=160,2.0 #可可豆\n");
                w.write("22514=160,0.4 #红蘑菇\n");
                w.write("22515=160,0.4 #棕蘑菇\n");
                w.write("1:0=8,0.0 #石头\n");
                w.write("1:1=8,0.0 #深板岩\n");
                w.write("1:2=8,0.0 #黑石\n");
                w.write("48=8,0.0 #苔石\n");

                w.write("\n");
                w.write("# 前期动物产品 | Early-stage Animal Products\n");
                w.write("288=4,2.0 #羽毛 Feather\n");
                w.write("334=16,8.0 #皮革 Leather\n");
                w.write("2551=8,4.0 #羊毛 Wool\n");
                w.write("344=16,8.0 #鸡蛋\n");
                w.write("479=64,8.0 #狼肉\n");

                w.write("\n");
                w.write("# 实用物品，装备 | Utility Items, Equipment\n");
                w.write("22580=8,0.0 #骨棒槌 Bone Club\n");
                w.write("272=18,0.0 #石剑 Stone Sword\n");
                w.write("2564=4,2.0 #树桩移除器 Stump Remover\n");
                w.write("46=32,16.0 #炸药桶 TNT Barrel\n");
                w.write("298=30,0.0 #皮革帽 Leather Cap\n");
                w.write("299=48,0.0 #皮革衣 Leather Tunic\n");
                w.write("300=42,0.0 #皮革裤 Leather Pants\n");
                w.write("301=24,0.0 #皮革鞋 Leather Boots\n");
                w.write("302=75,0.0 #锁链帽 Chain Helmet\n");
                w.write("303=120,0.0 #锁链衣 Chain Chestplate\n");
                w.write("304=105,0.0 #锁链裤 Chain Leggings\n");
                w.write("305=60,0.0 #锁链鞋 Chain Boots\n");
                w.write("384=3,0.0 #附魔之瓶 Bottle of Enchanting\n");

                w.write("\n");
                w.write("# 回收音乐唱片，命名牌 | Music Discs & Name Tag Recycling\n");
                w.write("2256=0,32.0\n");
                w.write("2257=0,32.0\n");
                w.write("2258=0,32.0\n");
                w.write("2259=0,32.0\n");
                w.write("2260=0,32.0\n");
                w.write("2261=0,32.0\n");
                w.write("2262=0,32.0\n");
                w.write("2263=0,32.0\n");
                w.write("2264=0,32.0\n");
                w.write("2265=0,32.0\n");
                w.write("2266=0,32.0\n");
                w.write("2267=0,32.0\n");
                w.write("421=0,80.0\n");

                w.write("\n");
                w.write("# 矿 | Ores\n");
                w.write("263=4,2.0 #煤炭 Coal\n");
                w.write("265=0,10.0 #铁锭 Iron Ingot\n");
                w.write("266=0,20.0 #金锭 Gold Ingot\n");
                w.write("264=80,80.0 #钻石 Diamond\n");
                w.write("388=80,20.0 #绿宝石 Emerald\n");
                w.write("331=2.5,0.5 #红石 Redstone\n");
                w.write("351:4=2.5,0.5 #青金石 Lapis Lazuli\n");
                w.write("49=50,0.0 #黑曜石\n");
                w.write("337=5,2.5 #粘土\n");
                w.write("1038=45,22.5 #粘土块\n");
                w.write("22572=2,0 #粗铁粉\n");
                w.write("22575=4,0 #粗金粉\n");
                w.write("348=12,2.0 #萤石粉\n");
                w.write("406=12,4.0 #石英\n");

                w.write("\n");
                w.write("# 部分怪物掉落物 | Some Monster Drops\n");
                w.write("287=10,0.8 #线 String\n");
                w.write("341=4,0.5 #粘液球 Slimeball\n");
                w.write("1000=0,4.5 #腐肉块 Rotten Flesh Block\n");
                w.write("215:15=0,6.0 #骨块 Bone Block\n");
                w.write("1055=0,16.0 #苦力怕酶腺块 Creeper Enzyme Block\n");
                w.write("3307=0,16.0 #蜘蛛眼块 Spider Eye Block\n");
                w.write("215:14=0,9.0 #末影块 Ender Block\n");
                w.write("22493=0,0.5 #硝石\n");

                w.write("\n");
                w.write("# 较难获取物品 | Hard-to-get Items\n");
                w.write("368=32,1.0 #末影珍珠 Ender Pearl\n");
                w.write("370=32,0.0 #恶魂之泪 Ghast Tear\n");
                w.write("376=32,0.0 #毒液囊 Venom Sac\n");
                w.write("22501=32,0.0 #女巫疣 Witch Wart\n");
                w.write("22545=24,0.0 #蝙蝠翼 Bat Wing\n");
                w.write("2560=12,0.0 #神秘腺体 Mysterious Gland\n");
                w.write("2561=160,40.0 #心肝\n");


                w.write("\n");
                w.write("# 科技树进度有关 | Tech Progression Related\n");
                w.write("369=0,8.0 #烈焰棒 Blaze Rod\n");
                w.write("403=120,4.0 #古代手稿 Ancient Manuscript\n");
                w.write("491=64,16.0 #粪便\n");

                w.write("\n");
                w.write("# 其他材料 | Other Materials\n");
                w.write("22576=8,8.0 #筚板 Bamboo Board\n");
                w.write("2555=0,4.0 #熔魂剂 Soul Melter\n");
                w.write("22492=8,2.0 #硫磺 Sulfur\n");
                w.write("215:9=8,2.0 #白色石头 White Stone\n");

                w.write("\n");
                w.write("# 打工赚钱 | Work-for-money\n");
                w.write("22587:599=4,0.0\n");
                w.write("22586=0,12.0\n");

                w.write("\n");
                w.write("# 功能类方块 | Functional Blocks\n");
                w.write("1054=80,40.0 #大篮子 Large Basket\n");
                w.write("1034=500,0.0 #工作台 Workbench\n");
                w.write("1002=480,120.0 #未激活的熔魂钢砧 Unactivated Soul Steel Anvil\n");
                w.write("116=720,180.0 #附魔台 Enchanting Table\n");
                w.write("176=720,180.0 #龙之容器 Dragon Vessel\n");
                w.write("379=480,120.0 #酿造台 Brewing Stand\n");

                w.write("\n");
                w.write("# 卷轴 | Scrolls\n");
                w.write("22479:0=1600,80.0 #保护，史莱姆1/1000 Protection, Slime 1/1000\n");
                w.write("22479:1=800,80.0 #火焰保护，僵尸猪人1/1000 Fire Protection, Zombie Pigman 1/1000\n");
                w.write("22479:2=1600,80.0 #摔落保护，蝙蝠1/250 Fall Protection, Bat 1/250\n");
                w.write("22479:3=800,80.0 #爆炸保护，苦力怕1/1000 Explosion Protection, Creeper 1/1000\n");
                w.write("22479:4=800,80.0 #弹射物保护，主世界中的骷髅1/1000 Projectile Protection, Overworld Skeleton 1/1000\n");
                w.write("22479:5=800,80.0 #水下呼吸，鱿鱼1/250 Water Breathing, Squid 1/250\n");
                w.write("22479:6=1600,80.0 #水下速掘，女巫1/1000 Underwater Mining, Witch 1/1000\n");
                w.write("22479:7=800,0.0 #荆棘，无法获得 Thorns, Unobtainable\n");
                w.write("22479:17=1600,80.0 #亡灵杀手，僵尸1/1000 Undead Slayer, Zombie 1/1000\n");
                w.write("22479:18=400,80.0 #节肢杀手，蜘蛛，洞穴蜘蛛1/1000 Arthropod Slayer, Spider & Cave Spider 1/1000\n");
                w.write("22479:19=400,80.0 #击退，凋灵 Knockback, Wither\n");
                w.write("22479:20=400,80.0 #火焰附加，岩浆怪1/250 Fire Aspect, Magma Cube 1/250\n");
                w.write("22479:32=1600,80.0 #效率，末地中的蠹虫1/1000 Efficiency, End Silverfish 1/1000\n");
                w.write("22479:33=1600,80.0 #精准采集，末影人1/1000 Silk Touch, Enderman 1/1000\n");
                w.write("22479:49=1600,80.0 #冲击，恶魂1/500 Impact, Ghast 1/500\n");
                w.write("22479:50=1600,80.0 #火矢，烈焰人1/500 Flame Arrow, Blaze 1/500\n");
                w.write("22479:51=1600,80.0 #无限，地狱中的骷髅1/1000 Infinity, Nether Skeleton 1/1000\n");
            }
        } catch (Exception ignored) {}
    }

    public static int compositeKey(int id, int dmg) {
        return ((id & 0xFFFF) << 16) | (dmg & 0xFFFF);
    }
}