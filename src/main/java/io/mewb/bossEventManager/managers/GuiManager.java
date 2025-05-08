package io.mewb.bossEventManager.managers;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.components.InteractionModifier;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;

import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.bosses.BossDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GuiManager {

    private final BossEventManagerPlugin plugin;
    private final ConfigManager configManager;
    private final BossManager bossManager;
    private final ArenaManager arenaManager; // Need ArenaManager to get themes

    // Common GUI settings
    private final Material fillerMaterial;
    private final Sound soundOpen;
    private final Sound soundClose;
    private final Sound soundNavClick;
    private final Sound soundNavFail;
    private final Sound soundItemSelect;

    public GuiManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.bossManager = plugin.getBossManager();
        this.arenaManager = plugin.getArenaManager(); // Get ArenaManager instance

        // Load common settings
        this.fillerMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.filler-item.material"), Material.BLACK_STAINED_GLASS_PANE, "Filler Item");
        this.soundOpen = parseSound(configManager.getConfig().getString("gui.sounds.open"), Sound.BLOCK_CHEST_OPEN, "Open GUI Sound");
        this.soundClose = parseSound(configManager.getConfig().getString("gui.sounds.close"), Sound.BLOCK_CHEST_CLOSE, "Close GUI Sound");
        this.soundNavClick = parseSound(configManager.getConfig().getString("gui.sounds.nav-click"), Sound.UI_BUTTON_CLICK, "Navigation Click Sound");
        this.soundNavFail = parseSound(configManager.getConfig().getString("gui.sounds.nav-fail"), Sound.BLOCK_LEVER_CLICK, "Navigation Fail Sound");
        this.soundItemSelect = parseSound(configManager.getConfig().getString("gui.sounds.item-select"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "Item Select Sound");

    }

    // --- Helper Methods ---
    private Material parseMaterial(String materialName, Material defaultMaterial, String context) {
        if (materialName == null || materialName.isEmpty()) return defaultMaterial;
        try {
            Material mat = Material.matchMaterial(materialName.toUpperCase());
            return mat != null ? mat : defaultMaterial;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid material name '" + materialName + "' for " + context + ". Defaulting to " + defaultMaterial.name() + ".", e);
            return defaultMaterial;
        }
    }

    private Sound parseSound(String soundName, Sound defaultSound, String context) {
        if (soundName == null || soundName.isEmpty()) return defaultSound;
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid sound name '" + soundName + "' for " + context + ". Defaulting to " + defaultSound.name() + ".", e);
            return defaultSound;
        }
    }

    private List<Component> createLore(List<String> format, Map<String, String> placeholders) {
        List<Component> lore = new ArrayList<>();
        for (String line : format) {
            String processedLine = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                processedLine = processedLine.replace(entry.getKey(), entry.getValue());
            }
            // Handle multi-line description placeholder separately if needed
            if (processedLine.contains("%boss_description%") && placeholders.containsKey("%boss_description%")) {
                String[] descLines = placeholders.get("%boss_description%").split("\n"); // Assuming description is newline separated
                for(int i = 0; i < descLines.length; i++) {
                    String currentLine = (i == 0) ? processedLine.replace("%boss_description%", descLines[i]) : descLines[i];
                    lore.add(Component.text(ChatColor.translateAlternateColorCodes('&', currentLine))
                            .decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY)); // Apply default style
                }
            } else {
                lore.add(Component.text(ChatColor.translateAlternateColorCodes('&', processedLine))
                        .decoration(TextDecoration.ITALIC, false)); // Apply default style (no gray)
            }
        }
        return lore;
    }

    // --- GUI Step 1: Difficulty Selection ---

    public void openDifficultySelectionGUI(Player player) {
        String title = configManager.getColoredString("gui.difficulty-selection.title", "&1&lSelect Difficulty");
        Collection<String> difficulties = bossManager.getAvailableDifficulties();

        // Determine rows needed (simple grid for now, can be improved)
        int rows = Math.max(3, (int) Math.ceil((double) difficulties.size() / 9.0) + 2); // Min 3 rows, add buffer

        Gui gui = Gui.gui()
                .title(Component.text(title))
                .rows(rows)
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        if (difficulties.isEmpty()) {
            gui.setItem(rows / 2, 5, ItemBuilder.from(Material.BARRIER).name(Component.text("&cNo difficulties found!")).asGuiItem());
        } else {
            int slot = 10; // Start placing items not on the edge (row 2, col 2)
            for (String difficulty : difficulties) {
                gui.setItem(slot, createDifficultyItem(player, difficulty));
                // Basic grid placement logic
                if ((slot + 1) % 9 == 8) slot += 3; // Move to next row start if near edge
                else slot++; // Move to next slot
            }
        }

        // Optional: Add filler items
        gui.getFiller().fill(ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem());

        gui.setOpenGuiAction(event -> player.playSound(player.getLocation(), soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> player.playSound(player.getLocation(), soundClose, 0.5f, 1f));
        gui.open(player);
    }

    private GuiItem createDifficultyItem(Player player, String difficulty) {
        String configKey = difficulty.toLowerCase(); // Use lowercase for config lookup
        String name = configManager.getColoredString("gui.difficulty-selection." + configKey + "-name", "&f" + difficulty); // Default to white name
        List<String> loreFormat = configManager.getConfig().getStringList("gui.difficulty-selection." + configKey + "-lore");
        Material material = parseMaterial(configManager.getConfig().getString("gui.difficulty-selection." + configKey + "-material"), Material.STONE, "Difficulty Item (" + difficulty + ")");

        List<Component> lore = loreFormat.stream()
                .map(line -> Component.text(ChatColor.translateAlternateColorCodes('&', line)).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList());
        lore.add(Component.empty());
        lore.add(Component.text(ChatColor.GREEN + "Click to view bosses"));

        return ItemBuilder.from(material)
                .name(Component.text(name))
                .lore(lore)
                .asGuiItem(event -> {
                    player.playSound(player.getLocation(), soundItemSelect, 1f, 1.1f);
                    openBossSelectionGUI(player, difficulty); // Proceed to next step
                });
    }


    // --- GUI Step 2: Boss Selection (Filtered by Difficulty) ---

    public void openBossSelectionGUI(Player player, String selectedDifficulty) {
        String titleFormat = configManager.getColoredString("gui.boss-selection.title", "&1&lSelect Boss (%difficulty%)");
        String title = titleFormat.replace("%difficulty%", selectedDifficulty); // Add selected difficulty to title
        int rows = configManager.getConfig().getInt("gui.boss-selection.rows", 6);
        int pageSize = configManager.getConfig().getInt("gui.boss-selection.items-per-page", (rows - 1) * 9);
        String noBossesMsg = configManager.getColoredString("gui.boss-selection.no-bosses", "&cNo bosses found for this difficulty.");

        List<BossDefinition> bosses = bossManager.getBossesByDifficulty(selectedDifficulty);

        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title))
                .rows(rows)
                .pageSize(pageSize)
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        if (bosses.isEmpty()) {
            gui.setItem(rows / 2, 5, ItemBuilder.from(Material.BARRIER).name(Component.text(noBossesMsg)).asGuiItem());
        } else {
            for (BossDefinition bossDef : bosses) {
                gui.addItem(createBossSelectionItem(player, bossDef));
            }
        }

        // Add navigation and back button
        addPaginationControls(gui, rows);
        // Add a "Back" button to return to difficulty selection
        gui.setItem(rows, 1, ItemBuilder.from(Material.ARROW).name(Component.text(ChatColor.RED + "<- Back to Difficulties")).asGuiItem(event -> {
            player.playSound(player.getLocation(), soundNavClick, 1f, 0.9f);
            openDifficultySelectionGUI(player);
        }));


        gui.getFiller().fillBottom(ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem()); // Fill bottom row if needed

        gui.setOpenGuiAction(event -> player.playSound(player.getLocation(), soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> player.playSound(player.getLocation(), soundClose, 0.5f, 1f));
        gui.open(player);
    }

    private GuiItem createBossSelectionItem(Player player, BossDefinition bossDef) {
        String nameFormat = configManager.getConfig().getString("gui.boss-item.name-format", "&6%boss_name%");
        List<String> loreFormat = configManager.getConfig().getStringList("gui.boss-item.lore-format");
        Material iconMaterial = parseMaterial(configManager.getConfig().getString("bosses." + bossDef.getId() + ".gui-icon"), Material.PLAYER_HEAD, "Boss Icon (" + bossDef.getId() + ")");

        String processedName = ChatColor.translateAlternateColorCodes('&',
                nameFormat.replace("%boss_name%", bossDef.getDisplayName())
        );

        // Prepare placeholders for lore
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%boss_name%", bossDef.getDisplayName());
        placeholders.put("%gem_cost%", String.valueOf(bossDef.getGemCost()));
        placeholders.put("%required_level%", bossDef.getRequiredLevel() > 0 ? String.valueOf(bossDef.getRequiredLevel()) : "None");
        // Join description lines with newline for multi-line replacement in createLore helper
        placeholders.put("%boss_description%", bossDef.getDescription().stream().collect(Collectors.joining("\n")));

        List<Component> processedLore = createLore(loreFormat, placeholders);

        return ItemBuilder.from(iconMaterial)
                .name(Component.text(processedName))
                .lore(processedLore)
                .asGuiItem(event -> {
                    player.playSound(player.getLocation(), soundItemSelect, 1f, 1.2f);
                    openArenaThemeSelectionGUI(player, bossDef); // Proceed to arena selection
                });
    }


    // --- GUI Step 3: Arena Theme Selection ---

    public void openArenaThemeSelectionGUI(Player player, BossDefinition selectedBoss) {
        if (arenaManager == null) {
            player.sendMessage(ChatColor.RED + "Error: Arena Manager is not available.");
            plugin.getLogger().severe("Attempted to open Arena Theme Selection GUI, but ArenaManager is null!");
            return;
        }

        String title = configManager.getColoredString("gui.arena-selection.title", "&1&lSelect Arena Theme");
        int rows = configManager.getConfig().getInt("gui.arena-selection.rows", 4);
        int pageSize = configManager.getConfig().getInt("gui.arena-selection.items-per-page", (rows - 1) * 9);
        String noThemesMsg = configManager.getColoredString("gui.arena-selection.no-themes", "&cNo arena themes available.");

        Collection<ArenaTheme> themes = arenaManager.getAllArenaThemes();

        PaginatedGui gui = Gui.paginated()
                .title(Component.text(title))
                .rows(rows)
                .pageSize(pageSize)
                .disableAllInteractions()
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        if (themes.isEmpty()) {
            gui.setItem(rows / 2, 5, ItemBuilder.from(Material.BARRIER).name(Component.text(noThemesMsg)).asGuiItem());
        } else {
            for (ArenaTheme theme : themes) {
                gui.addItem(createArenaThemeItem(player, selectedBoss, theme));
            }
        }

        // Add navigation and back button
        addPaginationControls(gui, rows);
        gui.setItem(rows, 1, ItemBuilder.from(Material.ARROW).name(Component.text(ChatColor.RED + "<- Back to Bosses")).asGuiItem(event -> {
            player.playSound(player.getLocation(), soundNavClick, 1f, 0.9f);
            openBossSelectionGUI(player, selectedBoss.getDifficulty()); // Go back to boss list for the same difficulty
        }));

        gui.getFiller().fillBottom(ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem());

        gui.setOpenGuiAction(event -> player.playSound(player.getLocation(), soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> player.playSound(player.getLocation(), soundClose, 0.5f, 1f));
        gui.open(player);
    }

    private GuiItem createArenaThemeItem(Player player, BossDefinition selectedBoss, ArenaTheme theme) {
        String nameFormat = configManager.getConfig().getString("gui.arena-selection.theme-item.name-format", "&b%theme_name%");
        List<String> loreFormat = configManager.getConfig().getStringList("gui.arena-selection.theme-item.lore-format");
        Material iconMaterial = parseMaterial(configManager.getConfig().getString("arena-themes." + theme.getId() + ".gui-icon"), Material.GRASS_BLOCK, "Arena Theme Icon (" + theme.getId() + ")");

        String processedName = ChatColor.translateAlternateColorCodes('&',
                nameFormat.replace("%theme_name%", theme.getDisplayName())
                        .replace("%theme_id%", theme.getId()) // Allow ID in name too
        );

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%theme_name%", theme.getDisplayName());
        placeholders.put("%theme_id%", theme.getId());
        // Add more placeholders if needed (e.g., description from config)

        List<Component> processedLore = createLore(loreFormat, placeholders);

        return ItemBuilder.from(iconMaterial)
                .name(Component.text(processedName))
                .lore(processedLore)
                .asGuiItem(event -> {
                    player.playSound(player.getLocation(), soundItemSelect, 1f, 1.3f);
                    player.closeInventory(); // Close GUI before starting checks
                    handleFinalSelection(player, selectedBoss, theme); // Final step
                });
    }


    // --- GUI Step 4: Final Handling (Initiate Checks & Event) ---

    private void handleFinalSelection(Player player, BossDefinition selectedBoss, ArenaTheme selectedTheme) {
        player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Selected Boss: " + selectedBoss.getDisplayName() +
                ChatColor.GREEN + " | Arena: " + selectedTheme.getDisplayName());
        player.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Initiating pre-flight checks (Party, Economy)...");

        // TODO: Implement actual checks and event start sequence
        // 1. Party Checks (PartyIntegrationManager)
        //    - Is player in a party? Leader? Size ok?
        //    - If fail: player.sendMessage(configManager.getMessage("party-not-ready")); return;

        // 2. Economy Checks (Vault)
        //    - Does leader have enough gems (selectedBoss.getGemCost())?
        //    - If fail: player.sendMessage(configManager.getMessage("not-enough-gems")); return;
        //    - If success: Deduct gems. If deduction fails, refund/cancel.

        // 3. Request Arena (ArenaManager)
        if (arenaManager == null) {
            player.sendMessage(ChatColor.RED + "Error: Arena Manager is unavailable.");
            // Refund gems if they were taken
            return;
        }
        player.sendMessage(configManager.getPrefix() + ChatColor.AQUA + "Requesting arena instance...");
        arenaManager.requestArena(selectedTheme.getId()).thenAccept(arenaInstance -> {
            if (arenaInstance != null) {
                // Arena created, schedule event start synchronously
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena ready! Starting event...");
                    // TODO: Get actual party members from PartyIntegrationManager
                    List<Player> partyMembers = new ArrayList<>();
                    partyMembers.add(player); // Placeholder: Use actual party
                    arenaManager.startEvent(arenaInstance, partyMembers, selectedBoss);
                });
            } else {
                // Arena request failed
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create an arena instance. Please try again later.");
                    // Refund gems if they were taken
                });
            }
        });
    }

    // --- Helper for Pagination Controls ---
    private void addPaginationControls(PaginatedGui gui, int totalRows) {
        int navRow = configManager.getConfig().getInt("gui.pagination.navigation-row", totalRows);
        int prevCol = configManager.getConfig().getInt("gui.pagination.previous-page-col", 3);
        int infoCol = configManager.getConfig().getInt("gui.pagination.page-info-col", 5);
        int nextCol = configManager.getConfig().getInt("gui.pagination.next-page-col", 7);

        String prevName = configManager.getColoredString("gui.pagination.previous-page-item.name", "&c<- Prev");
        Material prevMat = parseMaterial(configManager.getConfig().getString("gui.pagination.previous-page-item.material"), Material.PAPER, "Prev Page Item");
        String nextName = configManager.getColoredString("gui.pagination.next-page-item.name", "&aNext ->");
        Material nextMat = parseMaterial(configManager.getConfig().getString("gui.pagination.next-page-item.material"), Material.PAPER, "Next Page Item");
        Material infoMat = parseMaterial(configManager.getConfig().getString("gui.pagination.page-info-item.material"), Material.MAP, "Page Info Item");
        List<String> infoLoreFormat = configManager.getConfig().getStringList("gui.pagination.page-info-item.lore");


        gui.setItem(navRow, prevCol, ItemBuilder.from(prevMat)
                .name(Component.text(prevName))
                .glow(gui.getCurrentPageNum() > 1)
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (gui.previous()) {
                        playSoundForPlayer(event.getWhoClicked(), soundNavClick, 1f, 1f);
                        updatePaginationControls(gui, navRow, prevCol, infoCol, nextCol); // Update items after page change
                    } else {
                        playSoundForPlayer(event.getWhoClicked(), soundNavFail, 1f, 0.8f);
                    }
                }));

        updatePageInfoItem(gui, navRow, infoCol); // Initial set

        gui.setItem(navRow, nextCol, ItemBuilder.from(nextMat)
                .name(Component.text(nextName))
                .glow(gui.getCurrentPageNum() < gui.getPagesNum())
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (gui.next()) {
                        playSoundForPlayer(event.getWhoClicked(), soundNavClick, 1f, 1f);
                        updatePaginationControls(gui, navRow, prevCol, infoCol, nextCol); // Update items after page change
                    } else {
                        playSoundForPlayer(event.getWhoClicked(), soundNavFail, 1f, 0.8f);
                    }
                }));
    }

    // Helper to update all pagination items (glow + page number)
    private void updatePaginationControls(PaginatedGui gui, int navRow, int prevCol, int infoCol, int nextCol) {
        updatePageInfoItem(gui, navRow, infoCol); // Update page number display

        // Update Previous Button Glow
        GuiItem prevItem = gui.getGuiItem(navRow);
        if (prevItem != null) {
            String prevName = configManager.getColoredString("gui.pagination.previous-page-item.name", "&c<- Prev");
            Material prevMat = parseMaterial(configManager.getConfig().getString("gui.pagination.previous-page-item.material"), Material.PAPER, "Prev Page Item");
            gui.updateItem(navRow, prevCol, ItemBuilder.from(prevMat).name(Component.text(prevName)).glow(gui.getCurrentPageNum() > 1).asGuiItem());
        }
        // Update Next Button Glow
        GuiItem nextItem = gui.getGuiItem(navRow);
        if (nextItem != null) {
            String nextName = configManager.getColoredString("gui.pagination.next-page-item.name", "&aNext ->");
            Material nextMat = parseMaterial(configManager.getConfig().getString("gui.pagination.next-page-item.material"), Material.PAPER, "Next Page Item");
            gui.updateItem(navRow, nextCol, ItemBuilder.from(nextMat).name(Component.text(nextName)).glow(gui.getCurrentPageNum() < gui.getPagesNum()).asGuiItem());
        }
    }


    private void updatePageInfoItem(PaginatedGui gui, int row, int col) {
        Material infoMat = parseMaterial(configManager.getConfig().getString("gui.pagination.page-info-item.material"), Material.MAP, "Page Info Item");
        List<String> infoLoreFormat = configManager.getConfig().getStringList("gui.pagination.page-info-item.lore");
        String currentPage = String.valueOf(gui.getCurrentPageNum());
        String totalPages = String.valueOf(Math.max(1, gui.getPagesNum())); // Ensure total pages is at least 1

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%current%", currentPage);
        placeholders.put("%total%", totalPages);

        List<Component> lore = createLore(infoLoreFormat, placeholders);

        GuiItem pageInfoItem = ItemBuilder.from(infoMat)
                .name(Component.text(ChatColor.YELLOW + "Page " + currentPage + "/" + totalPages))
                .lore(lore)
                .asGuiItem(event -> event.setCancelled(true));
        gui.setItem(row, col, pageInfoItem);
    }

    private void playSoundForPlayer(HumanEntity player, Sound sound, float volume, float pitch) {
        if (player instanceof Player) {
            ((Player) player).playSound(player.getLocation(), sound, volume, pitch);
        }
    }

}