# MerchantRole — Paper 26.2

Automatically grants a configurable LuckPerms group when a player completes the Merchant I task.

## Requirements
- Paper 26.2 (Java 25)
- LuckPerms, WorldGuard, QuickShop-Hikari
- WorldGuard region ID `market` (configurable)

## Build
Run `mvn package` with JDK 25. Copy `target/merchant-role-1.0.0.jar` into the server's `plugins` folder, install the three dependencies, and restart.

## Task behavior
- A successful QuickShop-Hikari purchase in the configured WorldGuard region counts as one distinct customer for the player-owned shop owner. Repeat purchases by the same customer count once. Admin/unlimited shops and self-purchases do not count.
- The owner must have at least five distinct customers and 64 emerald blocks in their player inventory. The plugin checks inventory requirements every second while the player is online.
- The LuckPerms group defaults to `merchant`; edit `plugins/MerchantRole/config.yml` after the first start to change it. The group must already exist in LuckPerms.
- Progress is saved in the plugin's config. `/merchantprogress` shows your current progress.
