[![build](https://github.com/porkyoot/piggy-inventory/actions/workflows/build.yml/badge.svg)](https://github.com/porkyoot/piggy-inventory/actions/workflows/build.yml)

# Piggy Inventory

A simple yet powerful mod designed to enhance your inventory management experience in Minecraft. Piggy Inventory aims to streamline your interactions with items, tools, and containers.

## ⚠️ Disclaimer

**This is a personal project.** It comes **AS IS** and might have issues.
*   **No Support:** Do not expect regular support or bug fixes.
*   **No Forge Port:** There are **NO** plans to port this mod to Forge/NeoForge.
*   **Use at your own risk.**

Feel free to fork the project or submit a Pull Request if you want to contribute fixes or features!

---

## Features
### ⚡ Core Features
*   **Auto Tool Swap**: Automatically selects the best tool for the block you are mining. Supports Silk Touch/Fortune preferences.
*   **Auto Weapon Swap**: Automatically switches to your best weapon when attacking.
*   **Auto Refill**: Automatically replaces broken tools and depleted item stacks in your hands. Includes:
    *   **Food Refill**: Eat continuously without opening inventory.
    *   **Weapon/Tool Refill**: Never stop fighting or mining.
*   **Continuous Crafting**: Hold your click to craft, trade, or move items repeatedly. No more clicking spam!

### 🎒 Inventory Management
*   **Inventory Sorting**: Sort your inventory or containers instantly with **R** or **Middle Click**.
*   **Fast Loot / Deposit**:
    *   **Shift + Scroll**: Move matching items between inventories.
    *   **Ctrl + Scroll**: Move ALL items between inventories.
    *   **Note**: This only works when looking at a **Container** (Chest, Barrel, etc.) to preserve vanilla crouching!

### 🎮 Controls & Hybrid System
To resolve conflicts with other mods and Vanilla, Piggy Inventory uses a **Hybrid Key System**:
*   **Loot Matching** (Fast Loot Matching): Defaults to **Unbound**. When unbound, it uses **Shift**.
*   **Loot All** (Fast Loot All): Defaults to **Unbound**. When unbound, it uses **Control**.
*   **Customization**: You can bind these to any specific key in the Controls menu if you prefer not to use Shift/Ctrl.

### ⚙️ Configuration
The mod is fully configurable via **Mod Menu** (requires YACL).
*   **Safety**: Click delays, Anti-Cheat compliance.
*   **Features**: Toggle specific features on/off.
*   **Lists**: Configure custom blocks for Silk Touch, tools, etc.

---

## Dependencies & Installation

### Requirements
*   **Minecraft**: ~1.21.1
*   **Fabric Loader**: >=0.18.1
*   **Java**: >=21

### Required Mods
*   **[Fabric API](https://modrinth.com/mod/fabric-api)**
*   **[YACL (Yet Another Config Lib)](https://modrinth.com/mod/yacl)**
*   *(Optional)* **[Mod Menu](https://modrinth.com/mod/modmenu)** - Highly recommended for accessing the configuration screen.

### Installation
1.  Download the `.jar` file.
2.  Install Fabric Loader for Minecraft 1.21.1.
3.  Place the `piggy-inventory` jar (along with Fabric API and YACL) into your `.minecraft/mods` folder.
4.  Launch the game!

---

**License**: CC0-1.0
