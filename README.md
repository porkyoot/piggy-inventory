[![build](https://github.com/porkyoot/piggy-inventory/actions/workflows/build.yml/badge.svg)](https://github.com/porkyoot/piggy-inventory/actions/workflows/build.yml)
[![test](https://github.com/porkyoot/piggy-inventory/actions/workflows/test.yml/badge.svg)](https://github.com/porkyoot/piggy-inventory/actions/workflows/test.yml)
[![release](https://img.shields.io/github/v/release/porkyoot/piggy-inventory)](https://github.com/porkyoot/piggy-inventory/releases)

# Piggy Inventory

A simple yet powerful mod designed to enhance your inventory management experience in Minecraft. Piggy Inventory aims to streamline your interactions with items, tools, and containers.

---

## Screenshots

*Coming soon*

---

## ⚠️ Disclaimer

**This is a personal project.** It comes **AS IS** and might have issues.
*   **No Support:** Do not expect regular support or bug fixes.
*   **No Forge Port:** There are **NO** plans to port this mod to Forge/NeoForge.
*   **Use at your own risk.**

Feel free to fork the project or submit a Pull Request if you want to contribute fixes or features!

---

## Features

### ⚡ Automatic Swapping
*   **Auto Tool Swap**: Automatically selects the best tool for the block you are mining.
    *   Supports Silk Touch/Fortune preferences (configurable per-ore).
    *   Respects enchantments (Efficiency, Unbreaking, etc.).
*   **Auto Weapon Swap**: Automatically switches to your best weapon when attacking.
    *   Considers damage, enchantments (Sharpness, Smite, Bane of Arthropods, etc.).
    *   Configurable preferences for different target types.

### 🔄 Auto Refill
*   **Tool/Weapon Refill**: Never stop fighting or mining - broken tools are automatically replaced.
*   **Food Refill**: Eat continuously without opening inventory.
*   **Smart Matching**: Finds the next best item in your inventory automatically.

### 🎒 Inventory Management
*   **Inventory Sorting**: Sort your inventory or containers instantly with **R** or **Middle Click**.
    *   Multiple sorting algorithms: Smart Category, Alphabetical, Creative Order, Rarity, Type, Material, Color, Tag
    *   Customizable via JSON lists
*   **Fast Loot / Deposit**:
    *   **Shift + Scroll**: Move matching items between inventories.
    *   **Ctrl + Scroll**: Move ALL items between inventories.
    *   **Note**: This only works when looking at a **Container** (Chest, Barrel, etc.) to preserve vanilla crouching!
*   **Slot Locking**: Lock inventory slots to prevent accidental sorting or quick-looting.
*   **Continuous Crafting**: Hold your click to craft, trade, or move items repeatedly. No more clicking spam!

---

## Controls

You can rebind these keys in the standard Minecraft Controls menu under **"Piggy Inventory"**.

| Action | Default Key | Description |
| :--- | :--- | :--- |
| **Tool Preference Menu** | `H` | Opens radial menu to set Silk Touch/Fortune preference. |
| **Weapon Preference Menu** | `G` | Opens radial menu to configure weapon swap behavior. |
| **Sort Inventory** | `R` (in inventory) | Sorts your player inventory. |
| **Sort Container** | `Middle Click` | Sorts the container you're looking at. |
| **Fast Loot Matching** | `Shift + Scroll` (unbound by default) | Moves matching items while looking at container. |
| **Fast Loot All** | `Ctrl + Scroll` (unbound by default) | Moves all items while looking at container. |

### 🎮 Hybrid Key System
To avoid conflicts with vanilla controls:
*   **Loot Matching**: Defaults to **Unbound**. When unbound, uses **Shift**.
*   **Loot All**: Defaults to **Unbound**. When unbound, uses **Control**.
*   **Customization**: Bind to specific keys in Controls menu if you prefer not using Shift/Ctrl.

---

## Configuration

Access the configuration menu via **Mod Menu**.

*   **Sorting**: Choose default algorithm and layout (Compact, Rows, Columns, Grid).
*   **Tool/Weapon Swap**: Toggle auto-swapping, configure preferences.
*   **Slot Locking**: Set which slots to lock.
*   **Quick Loot**: Adjust delays and behavior.
*   **Custom Lists**: Configure Silk Touch blocks, tool priorities, etc.

---

## Dependencies & Installation

### Requirements
*   **Minecraft**: ~1.21.1
*   **Fabric Loader**: >=0.18.1
*   **Java**: >=21

### Required Mods
*   **[Fabric API](https://modrinth.com/mod/fabric-api)**: Any version
*   **[YACL (Yet Another Config Lib)](https://modrinth.com/mod/yacl)**: ~=3.6.1+1.21-fabric
*   **[Piggy Lib](https://github.com/porkyoot/piggy-lib)**: >=1.0.1
*   *(Optional)* **[Mod Menu](https://modrinth.com/mod/modmenu)**: >=11.0.3 - Highly recommended for accessing configuration.

### Installation
1.  Download the `.jar` file from [Releases](https://github.com/porkyoot/piggy-inventory/releases).
2.  Install Fabric Loader for Minecraft 1.21.1.
3.  Place the `piggy-inventory` jar (along with Fabric API, YACL, and Piggy Lib) into your `.minecraft/mods` folder.
4.  Launch the game!

---

## Inspiration

This mod was inspired by:
- **[Tweakeroo](https://modrinth.com/mod/tweakeroo)** - For advanced inventory management features and auto-refill mechanics.
- **[Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next)** - For sorting algorithms and inventory organization.
- **[Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks)** - For scroll-based item movement and quick loot features.
- **[Inventory Tabs](https://modrinth.com/mod/inventory-tabs)** - For UI/UX inspiration in inventory management.

---

**License**: CC0-1.0
