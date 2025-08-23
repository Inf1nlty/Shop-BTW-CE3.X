# Shop & Global Marketplace for Better Than Wolves CE 3.x

An in‑game economy system for BTW‑CE 3.x providing:

- System (server‑configured) item shop
- Player global marketplace (list / buy NBT‑aware items)
- Persistent balances
- Localized chat with parameter substitution
- Hotkeys for quick access (default: B system, G global)
- Minimal bandwidth (authoritative server catalogs)

---

## Table of Contents
1. Features Overview
2. System Shop
3. Global Marketplace
4. Economy & Balances
5. Configuration Files
6. Commands
7. Hotkeys & Key Binding Persistence
8. Chat Localization & Placeholders
9. Item Tooltip Augmentation
10. Data Persistence
11. Networking Protocol
12. Adding / Updating Prices
13. Troubleshooting
14. Extending / Ideas
15. License

---

## 1. Features Overview

| Domain | Highlights |
|--------|-----------|
| System Shop | Server‑owned price list (buy/sell), GUI grid, shift stack buy/sell |
| Global Marketplace | Player listings, NBT preserved, unlist refund, self‑buy protection |
| Economy | One‑decimal currency (tenths), `/money` query & OP set |
| Persistence | Balances (NBT), global listings (`config/global_shop.cfg`) |
| Localization | Keys + params; client resolves placeholders with language pack |
| Input | Two injectable key bindings (rebind safely, persisted) |
| Safety | Server authoritative pricing; client never trusts local cfg for costs |

---

## 2. System Shop

- Open: `/shop` or default hotkey **B**.
- Grid of server items (paged 19×N).
- Shift‑click shop item → attempt full stack purchase (max stack size).
- Shift‑click your inventory slot → sells that entire stack (if configured) or disposes if forced.

Server sends the *entire catalog* (id, meta, buy, sell) when opening; client never reads local `shop.cfg`.

---

## 3. Global Marketplace

Player‑driven listings:

| Action | Command / UI | Notes |
|--------|--------------|-------|
| Open marketplace | `/gshop` or hotkey **G** | Snapshot from server |
| List item | `/gshop sell <price> [amount]` | Price per item (one decimal), amount ≤ hand |
| View own listings | `/gshop my` | Shows id, item, amount, price |
| Unlist | `/gshop unlist <id>` | Refunds original items (NBT intact) |
| Buy | GUI click (1) / Shift (all) | Self‑buy blocked |

Listing record stores: id, owner UUID/name, item id, meta, amount, priceTenths, optional NBT (compressed on wire).

Self‑buy rejection avoids double messaging and accidental price manipulation.

---

## 4. Economy & Balances

- Currency unit: tenths (1 displayed decimal).
- Formatting: `whole.fraction` (e.g. `12.3`).
- Balance operations:
  - Buy: subtract
  - Sell: add
  - Dispose (if forced unlisted sell): no change
- Persisted per player (NBT tag `shop_money`).

---

## 5. Configuration Files

| File | Purpose | Format |
|------|---------|--------|
| `config/shop.cfg` | System shop price list | `id[:meta]=buy,sell` + `force_sell_unlisted=true/false` |
| `config/global_shop.cfg` | Player listings persistence | `id;ownerUUID;ownerName;itemId;meta;amount;priceTenths;base64NBT` |

### shop.cfg Example
```
force_sell_unlisted=true
minecraft:diamond=100,50
minecraft:iron_ingot=25,12
35:14=8,4            # Red wool (numeric id:meta)
263=7,3              # Coal
```

Rules:
- Comments start with `#`
- `minecraft:<name>` matches unlocalized root (no `item./tile.` prefix)
- Negative or malformed lines ignored
- Prices accept one decimal (tenths stored)

---

## 6. Commands

| Command | Description | Access |
|---------|-------------|--------|
| `/shop` | Open system shop | All |
| `/shop reload` | Reload server price list (no GUI forced) | All (adjust via wrapper if needed) |
| `/gshop` | Open global market | All |
| `/gshop sell <price> [amount]` | List item in hand | All |
| `/gshop my` | Show own listings | All |
| `/gshop unlist <id>` | Remove listing & refund | All |
| `/money` | Show balance | All |
| `/money <amount>` | Set own balance | OP |

---

## 7. Hotkeys & Persistence

Default bindings:
- System shop: **B** (`key.openShop`)
- Global market: **G** (`key.openGlobalShop`)

Persistence details:
- Custom bindings are appended after GameSettings constructor.
- A Mixin re-parses `options.txt` to restore saved keyCodes for these descriptions.
- Rebinding via the Controls menu persists normally across restarts.

---

## 8. Chat Localization & Placeholders

Server sends raw keys with pipe‑delimited parameters:
```
shop.buy.success|item=Diamond Sword|count=1|cost=120.0
gshop.sale.success|buyer=Alice|item=Enchanted Book|count=2|revenue=50.0
```

Client Mixin:
1. Detects prefix (`shop.`, `gshop.`, `globalshop.`)
2. Looks up translation
3. Replaces `{item}`, `{count}`, `{cost}`, `{gain}`, `{revenue}`, `{buyer}`, `{seller}`, `{id}`, `{page}`, `{pages}`, `{price}`, `{amount}`, `{balance}`.
4. When on a remote server, also attempts to translate raw `tile.*.name` / `item.*.name` tokens inside param values.

---

## 9. Item Tooltip Augmentation

Context flags:
- `inShop` (system shop GUI open)
- `inGlobalShop` (global market GUI open)

Global market stacks get transient NBT markers (`GShopPriceTenths`, etc.) to inject listing info without altering the player's real inventory items.

System shop uses NBT values sent per catalog entry; falls back to server config (should be redundant).

---

## 10. Data Persistence

| Data | Mechanism |
|------|-----------|
| Player balance | Player NBT tag (`shop_money`) |
| Global listings | `config/global_shop.cfg` lines |
| System prices | `config/shop.cfg` authoritative on server |
| Key binds | `options.txt` (with custom re-load) |

---

## 11. Networking Protocol (Summary)

| ID | Dir | Payload | Description |
|----|-----|---------|-------------|
| 1  | S→C | resultKey, balance | Transaction/result message |
| 2  | C→S | itemID, meta, count | System buy |
| 3  | C→S | itemID, count, slotIndex | System sell |
| 4  | S→C | windowId, balance, catalog[] | Open system shop + catalog |
| 5  | S→C | 36 slots | Inventory sync |
| 6  | C→S | — | Open system shop |
| 7  | S→C | windowId, balance, listings[] | Global market snapshot |
| 8  | C→S | — | Open global market |
| 9  | C→S | listingId, count | Buy listing |
| 10 | C→S | itemId, meta, amount, price | (Reserved) future GUI listing |
| 11 | C→S | listingId | Unlist listing |

Catalog entry: `(itemID, meta, buyTenths, sellTenths)`  
Listing entry: `(id, itemId, meta, amount, priceTenths, ownerName, [NBT])`

---

## 12. Adding / Updating Prices

1. Edit `config/shop.cfg`
2. Run `/shop reload`
3. Players reopening the/system shop will receive updated catalog.

No GUI auto-pop on reload (prevents surprise context switches).

---

## 13. License

MIT

---