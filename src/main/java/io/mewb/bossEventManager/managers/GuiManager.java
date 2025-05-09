package io.mewb.bossEventManager.managers;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.components.InteractionModifier;
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
import net.milkbowl.vault.economy.EconomyResponse; // Import EconomyResponse
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

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
    private ArenaManager arenaManager;
    private PartyInfoManager partyInfoManager; // Added PartyInfoManager field
    private Economy economy; // Added Economy field

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
        // Get managers lazily or when needed now
        plugin.getLogger().info("[GuiManager] Initialized.");

        // Load common settings
        this.fillerMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.filler-item.material"), Material.BLACK_STAINED_GLASS_PANE, "Filler Item");
        this.soundOpen = parseSound(configManager.getConfig().getString("gui.sounds.open"), Sound.BLOCK_CHEST_OPEN, "Open GUI Sound");
        this.soundClose = parseSound(configManager.getConfig().getString("gui.sounds.close"), Sound.BLOCK_CHEST_CLOSE, "Close GUI Sound");
        this.soundNavClick = parseSound(configManager.getConfig().getString("gui.sounds.nav-click"), Sound.UI_BUTTON_CLICK, "Navigation Click Sound");
        this.soundNavFail = parseSound(configManager.getConfig().getString("gui.sounds.nav-fail"), Sound.BLOCK_LEVER_CLICK, "Navigation Fail Sound");
        this.soundItemSelect = parseSound(configManager.getConfig().getString("gui.sounds.item-select"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "Item Select Sound");
    }

    // Helper getters with null checks
    private ArenaManager getArenaManager() {
        if (this.arenaManager == null) this.arenaManager = plugin.getArenaManager();
        return this.arenaManager;
    }
    private PartyInfoManager getPartyInfoManager() {
        if (this.partyInfoManager == null) this.partyInfoManager = plugin.getPartyInfoManager();
        return this.partyInfoManager;
    }
    private Economy getEconomy() {
        if (this.economy == null) this.economy = plugin.getVaultEconomy();
        return this.economy;
    }

    // --- Helper Methods (parseMaterial, parseSound, createLore) remain the same ---
    private Material parseMaterial(String materialName, Material defaultMaterial, String context) { /* ... */ return defaultMaterial; }
    private Sound parseSound(String soundName, Sound defaultSound, String context) { /* ... */ return defaultSound; }
    private List<Component> createLore(List<String> format, Map<String, String> placeholders) { /* ... */ return new ArrayList<>(); }


    // --- GUI Steps (openDifficultySelectionGUI, createDifficultyItem, openBossSelectionGUI, createBossSelectionItem, openArenaThemeSelectionGUI, createArenaThemeItem) remain the same ---
    public void openDifficultySelectionGUI(Player player) { /* ... */ }
    private GuiItem createDifficultyItem(Player player, String difficulty) { /* ... */ return null; }
    public void openBossSelectionGUI(Player player, String selectedDifficulty) { /* ... */ }
    private GuiItem createBossSelectionItem(Player player, BossDefinition bossDef) { /* ... */ return null; }
    public void openArenaThemeSelectionGUI(Player player, BossDefinition selectedBoss) { /* ... */ }
    private GuiItem createArenaThemeItem(Player player, BossDefinition selectedBoss, ArenaTheme theme) { /* ... */ return null; }


    // --- GUI Step 4: Final Handling (Integrate Checks) ---

    private void handleFinalSelection(Player player, BossDefinition selectedBoss, ArenaTheme selectedTheme) {
        player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Selected Boss: " + selectedBoss.getDisplayName() +
                ChatColor.GREEN + " | Arena: " + selectedTheme.getDisplayName());
        player.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Checking requirements...");

        PartyInfoManager currentPartyManager = getPartyInfoManager();
        if (currentPartyManager == null) {
            player.sendMessage(configManager.getMessage("party-check-fail")); // Use configured message
            plugin.getLogger().severe("PartyInfoManager is null! Cannot perform party checks.");
            return;
        }

        // 1. Request Party Info (Asynchronous)
        currentPartyManager.requestPartyInfo(player).whenComplete((partyInfo, throwable) -> {
            // Ensure subsequent checks run on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {

                // Handle errors from the future itself
                if (throwable != null) {
                    player.sendMessage(configManager.getMessage("party-check-fail"));
                    plugin.getLogger().log(Level.WARNING, "Party info request failed for " + player.getName(), throwable);
                    return;
                }
                // Handle failure reported by the Bungee extension (e.g., timeout, player not found by PAF)
                if (partyInfo == null || !partyInfo.isSuccess()) {
                    player.sendMessage(configManager.getMessage("party-check-fail"));
                    plugin.getLogger().warning("Party info request unsuccessful for " + player.getName() + " (Response success=" + (partyInfo != null && partyInfo.isSuccess()) + ")");
                    return;
                }

                // 2. Perform Party Checks (Synchronous - we have the info now)
                if (!partyInfo.isInParty()) {
                    player.sendMessage(configManager.getMessage("not-in-party"));
                    return;
                }
                if (!partyInfo.isLeader()) {
                    player.sendMessage(configManager.getMessage("not-party-leader"));
                    return;
                }
                int minSize = configManager.getMinPartySize();
                int maxSize = configManager.getMaxPartySize();
                if (partyInfo.getPartySize() < minSize) {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("%min_size%", String.valueOf(minSize));
                    player.sendMessage(configManager.getMessage("party-too-small", replacements));
                    return;
                }
                if (partyInfo.getPartySize() > maxSize) {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("%max_size%", String.valueOf(maxSize));
                    player.sendMessage(configManager.getMessage("party-too-large", replacements));
                    return;
                }

                // Party checks passed! Proceed to economy checks.
                player.sendMessage(configManager.getPrefix() + ChatColor.AQUA + "Party checks passed. Checking economy...");

                // 3. Perform Economy Checks (Synchronous)
                Economy currentEconomy = getEconomy();
                if (currentEconomy == null) {
                    player.sendMessage(configManager.getMessage("economy-error"));
                    plugin.getLogger().severe("Vault Economy provider is null! Cannot perform transaction.");
                    return;
                }
                double cost = selectedBoss.getGemCost();
                if (!currentEconomy.has(player, cost)) {
                    Map<String, String> replacements = new HashMap<>();
                    replacements.put("%cost%", String.valueOf(cost));
                    player.sendMessage(configManager.getMessage("not-enough-gems", replacements));
                    return;
                }

                // Attempt to withdraw funds
                EconomyResponse response = currentEconomy.withdrawPlayer(player, cost);
                if (!response.transactionSuccess()) {
                    player.sendMessage(configManager.getMessage("economy-error"));
                    plugin.getLogger().warning("Vault withdrawal failed for " + player.getName() + " (Cost: " + cost + "). Reason: " + response.errorMessage);
                    return;
                }

                // Economy checks passed! Proceed to arena request.
                player.sendMessage(configManager.getMessage("event-starting", Collections.singletonMap("%boss_name%", selectedBoss.getDisplayName())));

                // 4. Request Arena (Asynchronous)
                ArenaManager currentArenaManager = getArenaManager();
                if (currentArenaManager == null) {
                    player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Critical Error: Arena Manager became unavailable!");
                    // Attempt to refund player
                    currentEconomy.depositPlayer(player, cost);
                    return;
                }

                currentArenaManager.requestArena(selectedTheme.getId()).whenComplete((arenaInstance, arenaThrowable) -> {
                    // Ensure response handling is on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (arenaThrowable != null || arenaInstance == null) {
                            player.sendMessage(configManager.getMessage("arena-request-failed"));
                            plugin.getLogger().log(Level.SEVERE, "Arena request failed for player " + player.getName(), arenaThrowable);
                            // Attempt to refund player
                            currentEconomy.depositPlayer(player, cost);
                        } else {
                            // Arena ready! Start the event
                            player.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena ready! Starting event...");

                            // Get online players from the party UUIDs
                            List<Player> onlinePartyMembers = new ArrayList<>();
                            for (UUID memberUUID : partyInfo.getMemberUUIDs()) {
                                Player member = Bukkit.getPlayer(memberUUID);
                                // Ensure player is online AND on the same server where BossEventManager is running
                                if (member != null && member.isOnline()) {
                                    onlinePartyMembers.add(member);
                                }
                            }

                            if (onlinePartyMembers.isEmpty()) {
                                plugin.getLogger().warning("No online party members found on this server for arena " + arenaInstance.getInstanceId() + ". Aborting event start and cleaning up.");
                                player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Could not find any online party members on this server to start the event.");
                                currentArenaManager.endEvent(arenaInstance); // Clean up the unused arena
                                currentEconomy.depositPlayer(player, cost); // Refund
                            } else {
                                // Start the event with the online members
                                currentArenaManager.startEvent(arenaInstance, onlinePartyMembers, selectedBoss);
                            }
                        }
                    });
                });
            }); // End of runTask for synchronous checks
        }); // End of whenComplete for party info request
    }


    // --- Helper for Pagination Controls ---
    private void addPaginationControls(PaginatedGui gui, int totalRows, int navRow) { /* ... */ }
    private void updatePaginationControls(PaginatedGui gui, int navRow, int prevCol, int infoCol, int nextCol) { /* ... */ }
    private void updatePageInfoItem(PaginatedGui gui, int row, int col) { /* ... */ }
    private void playSoundForPlayer(HumanEntity player, Sound sound, float volume, float pitch) { /* ... */ }

}