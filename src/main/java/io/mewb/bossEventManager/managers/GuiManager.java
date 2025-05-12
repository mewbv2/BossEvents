package io.mewb.bossEventManager.managers;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.bosses.BossDefinition;
import io.mewb.bossEventManager.party.PartyInfoManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GuiManager {

    private final BossEventManagerPlugin plugin;
    private final ConfigManager configManager;
    private final BossManager bossManager;
    private ArenaManager arenaManager; // Can be null initially
    private PartyInfoManager partyInfoManager;
    private Economy economy;

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
        // plugin.getLogger().info("[GuiManager] Initialized.");

        this.fillerMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.filler-item.material"), Material.BLACK_STAINED_GLASS_PANE, "Filler Item");
        this.soundOpen = parseSound(configManager.getConfig().getString("gui.sounds.open"), Sound.BLOCK_CHEST_OPEN, "Open GUI Sound");
        this.soundClose = parseSound(configManager.getConfig().getString("gui.sounds.close"), Sound.BLOCK_CHEST_CLOSE, "Close GUI Sound");
        this.soundNavClick = parseSound(configManager.getConfig().getString("gui.sounds.nav-click"), Sound.UI_BUTTON_CLICK, "Navigation Click Sound");
        this.soundNavFail = parseSound(configManager.getConfig().getString("gui.sounds.nav-fail"), Sound.BLOCK_LEVER_CLICK, "Navigation Fail Sound");
        this.soundItemSelect = parseSound(configManager.getConfig().getString("gui.sounds.item-select"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "Item Select Sound");
    }

    private ArenaManager getArenaManager() {
        if (this.arenaManager == null) this.arenaManager = plugin.getArenaManager();
        if (this.arenaManager == null) plugin.getLogger().warning("[GuiManager] Attempted to access ArenaManager, but it is still null!");
        return this.arenaManager;
    }
    private PartyInfoManager getPartyInfoManager() {
        if (this.partyInfoManager == null) this.partyInfoManager = plugin.getPartyInfoManager();
        if (this.partyInfoManager == null) plugin.getLogger().warning("[GuiManager] Attempted to access PartyInfoManager, but it is still null!");
        return this.partyInfoManager;
    }
    private Economy getEconomy() {
        if (this.economy == null) this.economy = plugin.getVaultEconomy();
        if (this.economy == null) plugin.getLogger().warning("[GuiManager] Attempted to access Vault Economy, but it is still null!");
        return this.economy;
    }

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
            if (processedLine.contains("%boss_description%") && placeholders.containsKey("%boss_description%")) {
                String[] descLines = placeholders.get("%boss_description%").split("\n");
                for(int i = 0; i < descLines.length; i++) {
                    String currentLine = (i == 0) ? processedLine.replace("%boss_description%", descLines[i]) : descLines[i];
                    lore.add(Component.text(ChatColor.translateAlternateColorCodes('&', currentLine))
                            .decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                }
            } else {
                lore.add(Component.text(ChatColor.translateAlternateColorCodes('&', processedLine))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        return lore;
    }

    public void openDifficultySelectionGUI(Player player) {
        String title = configManager.getColoredString("gui.difficulty-selection.title", "&1&lSelect Difficulty");
        List<String> difficulties = new ArrayList<>(bossManager.getAvailableDifficulties());
        int numDifficulties = difficulties.size();
        int contentRowsNeeded = Math.max(1, (int) Math.ceil((double) numDifficulties / 5.0));
        int totalRows = Math.max(3, contentRowsNeeded + 2);
        Gui gui = Gui.gui().title(Component.text(title)).rows(totalRows).disableAllInteractions().create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        GuiItem filler = ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem();
        gui.getFiller().fillTop(filler);
        gui.getFiller().fillBottom(filler);
        if (numDifficulties == 0) {
            int centerRowForBarrier = (totalRows + 1) / 2;
            gui.setItem(centerRowForBarrier, 5, ItemBuilder.from(Material.BARRIER).name(Component.text("&cNo difficulties found!")).asGuiItem());
            for (int r = 2; r < totalRows; r++) {
                for (int c = 1; c <= 9; c++) {
                    if (gui.getGuiItem(r) == null) gui.setItem(r, c, filler);
                }
            }
        } else {
            int itemsPlaced = 0;
            for (int r = 0; r < contentRowsNeeded; r++) {
                int displayRow = r + 2;
                if (displayRow >= totalRows) break;
                int itemsInThisRow = 0;
                List<String> currentRowDifficulties = new ArrayList<>();
                for (int i = 0; i < 5 && (itemsPlaced + i) < numDifficulties; i++) {
                    currentRowDifficulties.add(difficulties.get(itemsPlaced + i));
                    itemsInThisRow++;
                }
                if (itemsInThisRow > 0) {
                    int[] colsToUse;
                    if (itemsInThisRow == 1) colsToUse = new int[]{5};
                    else if (itemsInThisRow == 2) colsToUse = new int[]{4, 6};
                    else if (itemsInThisRow == 3) colsToUse = new int[]{3, 5, 7};
                    else if (itemsInThisRow == 4) colsToUse = new int[]{2, 4, 6, 8};
                    else colsToUse = new int[]{1, 3, 5, 7, 9};
                    for (int i = 0; i < itemsInThisRow; i++) {
                        gui.setItem(displayRow, colsToUse[i], createDifficultyItem(player, currentRowDifficulties.get(i)));
                    }
                    itemsPlaced += itemsInThisRow;
                }
                for (int c = 1; c <= 9; c++) {
                    if (gui.getGuiItem(displayRow) == null) {
                        gui.setItem(displayRow, c, filler);
                    }
                }
            }
        }
        gui.setOpenGuiAction(event -> playSoundForPlayer(player, soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> playSoundForPlayer(player, soundClose, 0.5f, 1f));
        try { gui.open(player); }
        catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "[GuiManager] Exception opening difficulty GUI for " + player.getName(), e); }
    }
    private GuiItem createDifficultyItem(Player player, String difficulty) {
        String configKey = difficulty.toLowerCase();
        String name = configManager.getColoredString("gui.difficulty-selection." + configKey + "-name", "&f" + difficulty);
        List<String> loreFormat = configManager.getConfig().getStringList("gui.difficulty-selection." + configKey + "-lore");
        Material material = parseMaterial(configManager.getConfig().getString("gui.difficulty-selection." + configKey + "-material"), Material.STONE, "Difficulty Item (" + difficulty + ")");
        List<Component> lore = loreFormat.stream()
                .map(line -> Component.text(ChatColor.translateAlternateColorCodes('&', line)).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList());
        lore.add(Component.empty());
        lore.add(Component.text(ChatColor.GREEN + "Click to view bosses"));
        return ItemBuilder.from(material).name(Component.text(name)).lore(lore)
                .asGuiItem(event -> {
                    playSoundForPlayer(player, soundItemSelect, 1f, 1.1f);
                    openBossSelectionGUI(player, difficulty);
                });
    }

    public void openBossSelectionGUI(Player player, String selectedDifficulty) {
        String titleFormat = configManager.getColoredString("gui.boss-selection.title", "&1&lSelect Boss (%difficulty%)");
        String title = titleFormat.replace("%difficulty%", selectedDifficulty);
        int rows = configManager.getConfig().getInt("gui.boss-selection.rows", 6);
        int pageSize = configManager.getConfig().getInt("gui.boss-selection.items-per-page", (rows - 1) * 9);
        String noBossesMsg = configManager.getColoredString("gui.boss-selection.no-bosses", "&cNo bosses found for this difficulty.");
        List<BossDefinition> bosses = bossManager.getBossesByDifficulty(selectedDifficulty);
        PaginatedGui gui = Gui.paginated().title(Component.text(title)).rows(rows).pageSize(pageSize).disableAllInteractions().create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        if (bosses.isEmpty()) {
            gui.setItem(rows / 2, 5, ItemBuilder.from(Material.BARRIER).name(Component.text(noBossesMsg)).asGuiItem());
        } else {
            for (BossDefinition bossDef : bosses) {
                gui.addItem(createBossSelectionItem(player, bossDef));
            }
        }
        int navRow = Math.max(1, Math.min(configManager.getConfig().getInt("gui.pagination.navigation-row", rows), rows));
        addPaginationControls(gui, rows, navRow);
        gui.setItem(navRow, 1, ItemBuilder.from(Material.ARROW).name(Component.text(ChatColor.RED + "<- Back to Difficulties")).asGuiItem(event -> {
            playSoundForPlayer(player, soundNavClick, 1f, 0.9f);
            openDifficultySelectionGUI(player);
        }));
        GuiItem filler = ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem();
        for (int col = 1; col <= 9; col++) {
            if (gui.getGuiItem(navRow) == null) {
                gui.setItem(navRow, col, filler);
            }
        }
        gui.setOpenGuiAction(event -> playSoundForPlayer(player, soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> playSoundForPlayer(player, soundClose, 0.5f, 1f));
        gui.open(player);
    }
    private GuiItem createBossSelectionItem(Player player, BossDefinition bossDef) {
        String nameFormat = configManager.getConfig().getString("gui.boss-item.name-format", "&6%boss_name%");
        List<String> loreFormat = configManager.getConfig().getStringList("gui.boss-item.lore-format");
        Material iconMaterial = parseMaterial(configManager.getConfig().getString("bosses." + bossDef.getId() + ".gui-icon"), Material.PLAYER_HEAD, "Boss Icon (" + bossDef.getId() + ")");
        String processedName = ChatColor.translateAlternateColorCodes('&', nameFormat.replace("%boss_name%", bossDef.getDisplayName()));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%boss_name%", bossDef.getDisplayName());
        placeholders.put("%gem_cost%", String.valueOf(bossDef.getGemCost()));
        placeholders.put("%required_level%", bossDef.getRequiredLevel() > 0 ? String.valueOf(bossDef.getRequiredLevel()) : "None");
        placeholders.put("%boss_description%", bossDef.getDescription().stream().collect(Collectors.joining("\n")));
        List<Component> processedLore = createLore(loreFormat, placeholders);
        return ItemBuilder.from(iconMaterial).name(Component.text(processedName)).lore(processedLore)
                .asGuiItem(event -> {
                    playSoundForPlayer(player, soundItemSelect, 1f, 1.2f);
                    openArenaThemeSelectionGUI(player, bossDef);
                });
    }

    public void openArenaThemeSelectionGUI(Player player, BossDefinition selectedBoss) {
        // plugin.getLogger().info("[GuiManager] Attempting to open Arena Theme Selection GUI for player " + player.getName());
        ArenaManager currentArenaManager = getArenaManager();
        if (currentArenaManager == null) {
            player.sendMessage(ChatColor.RED + "Error: Arena Manager is not available. Cannot select arena.");
            plugin.getLogger().severe("ArenaManager is null when trying to open Arena Theme Selection GUI!");
            return;
        }
        String title = configManager.getColoredString("gui.arena-selection.title", "&1&lSelect Arena Theme");
        int rows = configManager.getConfig().getInt("gui.arena-selection.rows", 4);
        int pageSize = configManager.getConfig().getInt("gui.arena-selection.items-per-page", (rows - 1) * 9);
        String noThemesMsg = configManager.getColoredString("gui.arena-selection.no-themes", "&cNo arena themes available.");
        Collection<ArenaTheme> themes = currentArenaManager.getAllArenaThemes();
        PaginatedGui gui = Gui.paginated().title(Component.text(title)).rows(rows).pageSize(pageSize).disableAllInteractions().create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));
        if (themes.isEmpty()) {
            gui.setItem(rows / 2, 5, ItemBuilder.from(Material.BARRIER).name(Component.text(noThemesMsg)).asGuiItem());
        } else {
            for (ArenaTheme theme : themes) {
                gui.addItem(createArenaThemeItem(player, selectedBoss, theme));
            }
        }
        int navRow = Math.max(1, Math.min(configManager.getConfig().getInt("gui.pagination.navigation-row", rows), rows));
        addPaginationControls(gui, rows, navRow);
        gui.setItem(navRow, 1, ItemBuilder.from(Material.ARROW).name(Component.text(ChatColor.RED + "<- Back to Bosses")).asGuiItem(event -> {
            playSoundForPlayer(player, soundNavClick, 1f, 0.9f);
            openBossSelectionGUI(player, selectedBoss.getDifficulty());
        }));
        GuiItem filler = ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem();
        for (int col = 1; col <= 9; col++) {
            if (gui.getGuiItem(navRow) == null) {
                gui.setItem(navRow, col, filler);
            }
        }
        gui.setOpenGuiAction(event -> playSoundForPlayer(player, soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> playSoundForPlayer(player, soundClose, 0.5f, 1f));
        gui.open(player);
    }
    private GuiItem createArenaThemeItem(Player player, BossDefinition selectedBoss, ArenaTheme theme) {
        String nameFormat = configManager.getConfig().getString("gui.arena-selection.theme-item.name-format", "&b%theme_name%");
        List<String> loreFormat = configManager.getConfig().getStringList("gui.arena-selection.theme-item.lore-format");
        Material iconMaterial = parseMaterial(configManager.getConfig().getString("arena-themes." + theme.getId() + ".gui-icon"), Material.GRASS_BLOCK, "Arena Theme Icon (" + theme.getId() + ")");
        String processedName = ChatColor.translateAlternateColorCodes('&', nameFormat.replace("%theme_name%", theme.getDisplayName()).replace("%theme_id%", theme.getId()));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%theme_name%", theme.getDisplayName());
        placeholders.put("%theme_id%", theme.getId());
        List<Component> processedLore = createLore(loreFormat, placeholders);
        return ItemBuilder.from(iconMaterial).name(Component.text(processedName)).lore(processedLore)
                .asGuiItem(event -> {
                    playSoundForPlayer(player, soundItemSelect, 1f, 1.3f);
                    player.closeInventory();
                    handleFinalSelection(player, selectedBoss, theme);
                });
    }

    // --- GUI Step 4: Final Handling (Integrate Checks) ---
    private void handleFinalSelection(Player player, BossDefinition selectedBoss, ArenaTheme selectedTheme) {
        player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Selected Boss: " + selectedBoss.getDisplayName() +
                ChatColor.GREEN + " | Arena: " + selectedTheme.getDisplayName());
        player.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Checking requirements...");

        PartyInfoManager currentPartyManager = getPartyInfoManager();
        if (currentPartyManager == null) {
            player.sendMessage(configManager.getMessage("party-check-fail"));
            plugin.getLogger().severe("PartyInfoManager is null! Cannot perform party checks for " + player.getName());
            return;
        }

        // 1. Request Party Info (Asynchronous)
        currentPartyManager.requestPartyInfo(player).whenComplete((partyInfo, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> { // Ensure all subsequent logic runs on main thread
                if (throwable != null) {
                    player.sendMessage(configManager.getMessage("party-check-fail"));
                    plugin.getLogger().log(Level.WARNING, "Party info request future completed exceptionally for " + player.getName(), throwable);
                    return;
                }
                if (partyInfo == null || !partyInfo.isSuccess()) {
                    player.sendMessage(configManager.getMessage("party-check-fail"));
                    plugin.getLogger().warning("Party info request unsuccessful for " + player.getName() + " (Response success=" + (partyInfo != null && partyInfo.isSuccess()) + ")");
                    return;
                }

                // 2. Perform Party Checks
                if (!partyInfo.isInParty()) { player.sendMessage(configManager.getMessage("not-in-party")); return; }
                if (!partyInfo.isLeader()) { player.sendMessage(configManager.getMessage("not-party-leader")); return; }
                int minSize = configManager.getMinPartySize(); int maxSize = configManager.getMaxPartySize();
                if (partyInfo.getPartySize() < minSize) { Map<String, String> r = new HashMap<>(); r.put("%min_size%", String.valueOf(minSize)); player.sendMessage(configManager.getMessage("party-too-small", r)); return; }
                if (partyInfo.getPartySize() > maxSize) { Map<String, String> r = new HashMap<>(); r.put("%max_size%", String.valueOf(maxSize)); player.sendMessage(configManager.getMessage("party-too-large", r)); return; }

                player.sendMessage(configManager.getPrefix() + ChatColor.AQUA + "Party checks passed. Checking economy...");

                // 3. Perform Economy Checks
                Economy currentEconomy = getEconomy();
                if (currentEconomy == null) { player.sendMessage(configManager.getMessage("economy-error")); plugin.getLogger().severe("Vault Economy provider is null for " + player.getName() + "!"); return; }
                double cost = selectedBoss.getGemCost();
                // The player initiating the GUI is the one whose balance is checked and charged (assumed to be party leader from previous check)
                if (!currentEconomy.has(player, cost)) {
                    Map<String, String> r = new HashMap<>(); r.put("%cost%", String.valueOf(cost));
                    player.sendMessage(configManager.getMessage("not-enough-gems", r)); return;
                }

                EconomyResponse econResponse = currentEconomy.withdrawPlayer(player, cost);
                if (!econResponse.transactionSuccess()) {
                    player.sendMessage(configManager.getMessage("economy-error"));
                    plugin.getLogger().warning("Vault withdrawal failed for " + player.getName() + " (Cost: " + cost + "). Reason: " + econResponse.errorMessage);
                    return;
                }
                player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Payment successful. " + cost + " gems deducted.");

                // 4. Request Arena
                player.sendMessage(configManager.getMessage("event-starting", Collections.singletonMap("%boss_name%", selectedBoss.getDisplayName())));
                ArenaManager currentArenaManager = getArenaManager();
                if (currentArenaManager == null) {
                    player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Critical Error: Arena Manager is not available!");
                    currentEconomy.depositPlayer(player, cost); // Attempt to refund
                    plugin.getLogger().severe("ArenaManager became null before arena request for " + player.getName());
                    return;
                }

                currentArenaManager.requestArena(selectedTheme.getId()).whenComplete((arenaInstance, arenaThrowable) -> {
                    Bukkit.getScheduler().runTask(plugin, () -> { // Ensure response handling is on main thread
                        if (arenaThrowable != null || arenaInstance == null) {
                            player.sendMessage(configManager.getMessage("arena-request-failed"));
                            plugin.getLogger().log(Level.SEVERE, "Arena request failed for player " + player.getName() + " and theme " + selectedTheme.getId(), arenaThrowable);
                            currentEconomy.depositPlayer(player, cost); // Attempt to refund
                        } else {
                            player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena ready! Teleporting party and starting event...");

                            // Get ONLINE party members on THIS server
                            List<Player> onlinePartyMembers = new ArrayList<>();
                            if (partyInfo.getMemberUUIDs() != null) {
                                for (UUID memberUUID : partyInfo.getMemberUUIDs()) {
                                    Player member = Bukkit.getPlayer(memberUUID);
                                    // Ensure player is online AND on the same server where BossEventManager is running
                                    if (member != null && member.isOnline()) {
                                        onlinePartyMembers.add(member);
                                    }
                                }
                            }

                            if (onlinePartyMembers.isEmpty()) {
                                plugin.getLogger().warning("No online party members found on this server for arena " + arenaInstance.getInstanceId() + " (Initiator: " + player.getName() + "). Aborting event start.");
                                player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Could not find any online party members on this server to start the event. Refunding cost.");
                                if (getArenaManager() != null) { // Re-check arenaManager before calling endEvent
                                    getArenaManager().endEvent(arenaInstance); // Clean up the unused arena
                                }
                                currentEconomy.depositPlayer(player, cost); // Refund
                            } else {
                                // Ensure initiator is in the list if they are online
                                // This is a safeguard, as the partyInfo should include them.
                                boolean initiatorFound = false;
                                for(Player p : onlinePartyMembers) {
                                    if(p.getUniqueId().equals(player.getUniqueId())) {
                                        initiatorFound = true;
                                        break;
                                    }
                                }
                                if(!initiatorFound && player.isOnline()){
                                    plugin.getLogger().info("Initiator " + player.getName() + " was not in the online member list, adding them for event start.");
                                    onlinePartyMembers.add(player); // Add initiator if they are online but somehow missed
                                }

                                getArenaManager().startEvent(arenaInstance, onlinePartyMembers, selectedBoss);
                            }
                        }
                    });
                });
            });
        });
    }


    // --- Helper for Pagination Controls ---
    private void addPaginationControls(PaginatedGui gui, int totalRows, int navRow) {
        int prevCol = configManager.getConfig().getInt("gui.pagination.previous-page-col", 3);
        int infoCol = configManager.getConfig().getInt("gui.pagination.page-info-col", 5);
        int nextCol = configManager.getConfig().getInt("gui.pagination.next-page-col", 7);
        String prevName = configManager.getColoredString("gui.pagination.previous-page-item.name", "&c<- Prev");
        Material prevMat = parseMaterial(configManager.getConfig().getString("gui.pagination.previous-page-item.material"), Material.PAPER, "Prev Page Item");
        String nextName = configManager.getColoredString("gui.pagination.next-page-item.name", "&aNext ->");
        Material nextMat = parseMaterial(configManager.getConfig().getString("gui.pagination.next-page-item.material"), Material.PAPER, "Next Page Item");

        if (prevCol < 1 || prevCol > 9 || infoCol < 1 || infoCol > 9 || nextCol < 1 || nextCol > 9) { plugin.getLogger().warning("Invalid column configuration for pagination controls."); return; }

        gui.setItem(navRow, prevCol, ItemBuilder.from(prevMat).name(Component.text(prevName)).glow(gui.getCurrentPageNum() > 1)
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (gui.previous()) {
                        playSoundForPlayer(event.getWhoClicked(), soundNavClick, 1f, 1f);
                        updatePaginationControls(gui, navRow, prevCol, infoCol, nextCol);
                    } else { playSoundForPlayer(event.getWhoClicked(), soundNavFail, 1f, 0.8f); }
                }));
        updatePageInfoItem(gui, navRow, infoCol);
        gui.setItem(navRow, nextCol, ItemBuilder.from(nextMat).name(Component.text(nextName)).glow(gui.getCurrentPageNum() < gui.getPagesNum())
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (gui.next()) {
                        playSoundForPlayer(event.getWhoClicked(), soundNavClick, 1f, 1f);
                        updatePaginationControls(gui, navRow, prevCol, infoCol, nextCol);
                    } else { playSoundForPlayer(event.getWhoClicked(), soundNavFail, 1f, 0.8f); }
                }));
    }
    private void updatePaginationControls(PaginatedGui gui, int navRow, int prevCol, int infoCol, int nextCol) {
        updatePageInfoItem(gui, navRow, infoCol);
        GuiItem prevItem = gui.getGuiItem(navRow);
        if (prevItem != null) {
            String prevName = configManager.getColoredString("gui.pagination.previous-page-item.name", "&c<- Prev");
            Material prevMat = parseMaterial(configManager.getConfig().getString("gui.pagination.previous-page-item.material"), Material.PAPER, "Prev Page Item");
            prevItem = ItemBuilder.from(prevMat).name(Component.text(prevName)).glow(gui.getCurrentPageNum() > 1).asGuiItem();
            gui.updateItem(navRow, prevCol, prevItem);
        }
        GuiItem nextItem = gui.getGuiItem(navRow);
        if (nextItem != null) {
            String nextName = configManager.getColoredString("gui.pagination.next-page-item.name", "&aNext ->");
            Material nextMat = parseMaterial(configManager.getConfig().getString("gui.pagination.next-page-item.material"), Material.PAPER, "Next Page Item");
            nextItem = ItemBuilder.from(nextMat).name(Component.text(nextName)).glow(gui.getCurrentPageNum() < gui.getPagesNum()).asGuiItem();
            gui.updateItem(navRow, nextCol, nextItem);
        }
    }
    private void updatePageInfoItem(PaginatedGui gui, int row, int col) {
        Material infoMat = parseMaterial(configManager.getConfig().getString("gui.pagination.page-info-item.material"), Material.MAP, "Page Info Item");
        List<String> infoLoreFormat = configManager.getConfig().getStringList("gui.pagination.page-info-item.lore");
        String currentPage = String.valueOf(gui.getCurrentPageNum());
        String totalPages = String.valueOf(Math.max(1, gui.getPagesNum()));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%current%", currentPage); placeholders.put("%total%", totalPages);
        List<Component> lore = createLore(infoLoreFormat, placeholders);
        GuiItem pageInfoItem = ItemBuilder.from(infoMat).name(Component.text(ChatColor.YELLOW + "Page " + currentPage + "/" + totalPages)).lore(lore).asGuiItem(event -> event.setCancelled(true));
        gui.setItem(row, col, pageInfoItem);
    }
    private void playSoundForPlayer(HumanEntity player, Sound sound, float volume, float pitch) {
        if (player instanceof Player) { ((Player) player).playSound(player.getLocation(), sound, volume, pitch); }
    }

}