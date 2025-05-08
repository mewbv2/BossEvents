package io.mewb.bossEventManager.managers;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;

import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.bosses.BossDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GuiManager {

    private final BossEventManagerPlugin plugin;
    private final ConfigManager configManager;
    private final BossManager bossManager;

    // GUI Configuration Values
    private final String bossSelectionTitle;
    private final int guiRows;
    private final int itemsPerPage;
    private final String noBossesAvailableMessage;

    private final int navigationRow;
    private final int prevPageCol;
    private final int pageInfoCol;
    private final int nextPageCol;

    private final String prevPageName;
    private final Material prevPageMaterial;
    private final String nextPageName;
    private final Material nextPageMaterial;
    private final Material pageInfoMaterial;
    private final List<String> pageInfoLore;
    private final Material fillerMaterial;

    private final String bossItemNameFormat;
    private final List<String> bossItemLoreFormat;

    // Sounds
    private final Sound soundOpen;
    private final Sound soundClose;
    private final Sound soundNavClick;
    private final Sound soundNavFail;
    private final Sound soundItemSelect;


    public GuiManager(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.bossManager = plugin.getBossManager();

        // Load GUI structural settings
        this.bossSelectionTitle = configManager.getColoredString("gui.boss-selection.title", "&1&lSelect a Boss Event");
        this.guiRows = configManager.getConfig().getInt("gui.boss-selection.rows", 6);
        this.itemsPerPage = configManager.getConfig().getInt("gui.boss-selection.items-per-page", (this.guiRows - 1) * 9);
        this.noBossesAvailableMessage = configManager.getColoredString("gui.boss-selection.no-bosses", "&cNo boss events are currently available.");

        // Load pagination layout settings
        this.navigationRow = configManager.getConfig().getInt("gui.pagination.navigation-row", this.guiRows);
        this.prevPageCol = configManager.getConfig().getInt("gui.pagination.previous-page-col", 3);
        this.pageInfoCol = configManager.getConfig().getInt("gui.pagination.page-info-col", 5);
        this.nextPageCol = configManager.getConfig().getInt("gui.pagination.next-page-col", 7);

        // Load pagination item details
        this.prevPageName = configManager.getColoredString("gui.pagination.previous-page-item.name", "&c<- Previous Page");
        this.prevPageMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.previous-page-item.material"), Material.PAPER, "Previous Page Item");
        this.nextPageName = configManager.getColoredString("gui.pagination.next-page-item.name", "&aNext Page ->");
        this.nextPageMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.next-page-item.material"), Material.PAPER, "Next Page Item");
        this.pageInfoMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.page-info-item.material"), Material.MAP, "Page Info Item");
        this.pageInfoLore = configManager.getColoredStringList("gui.pagination.page-info-item.lore");
        this.fillerMaterial = parseMaterial(configManager.getConfig().getString("gui.pagination.filler-item.material"), Material.BLACK_STAINED_GLASS_PANE, "Filler Item");

        // Load boss item format
        this.bossItemNameFormat = configManager.getConfig().getString("gui.boss-item.name-format", "&6%boss_name%");
        this.bossItemLoreFormat = configManager.getConfig().getStringList("gui.boss-item.lore-format");
        if (this.bossItemLoreFormat.isEmpty()) { // Default lore if not in config
            this.bossItemLoreFormat.add("&7Cost: &e%gem_cost% Gems");
            this.bossItemLoreFormat.add("&7Level: &b%required_level%");
            this.bossItemLoreFormat.add("");
            this.bossItemLoreFormat.add("%boss_description%");
            this.bossItemLoreFormat.add("");
            this.bossItemLoreFormat.add("&eClick to challenge!");
        }

        // Load sounds
        this.soundOpen = parseSound(configManager.getConfig().getString("gui.sounds.open"), Sound.BLOCK_CHEST_OPEN, "Open GUI Sound");
        this.soundClose = parseSound(configManager.getConfig().getString("gui.sounds.close"), Sound.BLOCK_CHEST_CLOSE, "Close GUI Sound");
        this.soundNavClick = parseSound(configManager.getConfig().getString("gui.sounds.nav-click"), Sound.UI_BUTTON_CLICK, "Navigation Click Sound");
        this.soundNavFail = parseSound(configManager.getConfig().getString("gui.sounds.nav-fail"), Sound.BLOCK_LEVER_CLICK, "Navigation Fail Sound");
        this.soundItemSelect = parseSound(configManager.getConfig().getString("gui.sounds.item-select"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "Item Select Sound");
    }

    private Material parseMaterial(String materialName, Material defaultMaterial, String context) {
        if (materialName == null || materialName.isEmpty()) {
            return defaultMaterial;
        }
        try {
            Material mat = Material.matchMaterial(materialName.toUpperCase());
            return mat != null ? mat : defaultMaterial;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid material name '" + materialName + "' for " + context + ". Defaulting to " + defaultMaterial.name() + ".", e);
            return defaultMaterial;
        }
    }

    private Sound parseSound(String soundName, Sound defaultSound, String context) {
        if (soundName == null || soundName.isEmpty()) {
            return defaultSound;
        }
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid sound name '" + soundName + "' for " + context + ". Defaulting to " + defaultSound.name() + ".", e);
            return defaultSound;
        }
    }


    public void openBossSelectionGUI(Player player) {
        Collection<BossDefinition> availableBosses = bossManager.getAllBossDefinitions();

        PaginatedGui gui = Gui.paginated()
                .title(Component.text(bossSelectionTitle)) // Already colored
                .rows(guiRows)
                .pageSize(itemsPerPage)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.setOutsideClickAction(event -> event.setCancelled(true));
        gui.disableItemDrop();
        gui.disableItemPlace();
        gui.disableItemTake();
        gui.disableOtherActions();

        if (availableBosses.isEmpty()) {
            GuiItem noBossesItem = ItemBuilder.from(Material.BARRIER) // Material for no-bosses can also be configured
                    .name(Component.text(noBossesAvailableMessage)) // Already colored
                    .asGuiItem(event -> event.setCancelled(true));
            // Calculate center slot for the no-bosses item based on guiRows
            int centerRow = (guiRows / 2) + (guiRows % 2); // e.g. for 6 rows, (6/2)+0 = 3. For 5 rows, (5/2)+1 = 3.
            int centerCol = 5; // Middle column
            gui.setItem(centerRow, centerCol, noBossesItem);
        } else {
            for (BossDefinition bossDef : availableBosses) {
                gui.addItem(createBossGuiItem(player, bossDef));
            }
        }

        // Navigation items
        gui.setItem(navigationRow, prevPageCol, ItemBuilder.from(prevPageMaterial)
                .name(Component.text(prevPageName)) // Already colored
                .glow(gui.getCurrentPageNum() > 1)
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (gui.previous()) {
                        player.playSound(player.getLocation(), soundNavClick, 1f, 1f);
                        updatePageInfoItem(gui, navigationRow, pageInfoCol);
                        updateNavigationButtonGlow(gui, navigationRow, prevPageCol, nextPageCol);
                    } else {
                        player.playSound(player.getLocation(), soundNavFail, 1f, 0.8f);
                    }
                }));

        updatePageInfoItem(gui, navigationRow, pageInfoCol); // Initial page info

        gui.setItem(navigationRow, nextPageCol, ItemBuilder.from(nextPageMaterial)
                .name(Component.text(nextPageName)) // Already colored
                .glow(gui.getCurrentPageNum() < gui.getPagesNum())
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (gui.next()) {
                        player.playSound(player.getLocation(), soundNavClick, 1f, 1f);
                        updatePageInfoItem(gui, navigationRow, pageInfoCol);
                        updateNavigationButtonGlow(gui, navigationRow, prevPageCol, nextPageCol);
                    } else {
                        player.playSound(player.getLocation(), soundNavFail, 1f, 0.8f);
                    }
                }));

        GuiItem filler = ItemBuilder.from(fillerMaterial).name(Component.text(" ")).asGuiItem(event -> event.setCancelled(true));
        for (int col = 1; col <= 9; col++) { // Fill navigation row
            if (gui.getGuiItem(navigationRow) == null) {
                gui.setItem(navigationRow, col, filler);
            }
        }

        gui.setOpenGuiAction(event -> player.playSound(player.getLocation(), soundOpen, 0.5f, 1f));
        gui.setCloseGuiAction(event -> player.playSound(player.getLocation(), soundClose, 0.5f, 1f));

        gui.open(player);
    }

    private void updateNavigationButtonGlow(PaginatedGui gui, int navRow, int prevCol, int nextCol) {
        GuiItem prevItem = gui.getGuiItem(navRow);
        if (prevItem != null && prevItem.getItemStack().getType() == prevPageMaterial) { // Check type to ensure it's the button
            gui.updateItem(navRow, prevCol, ItemBuilder.from(prevPageMaterial).name(Component.text(prevPageName)).glow(gui.getCurrentPageNum() > 1).asGuiItem());
        }

        GuiItem nextItem = gui.getGuiItem(navRow);
        if (nextItem != null && nextItem.getItemStack().getType() == nextPageMaterial) {
            gui.updateItem(navRow, nextCol, ItemBuilder.from(nextPageMaterial).name(Component.text(nextPageName)).glow(gui.getCurrentPageNum() < gui.getPagesNum()).asGuiItem());
        }
    }


    private void updatePageInfoItem(PaginatedGui gui, int row, int col) {
        List<Component> loreComponents = pageInfoLore.stream()
                .map(line -> Component.text(ChatColor.translateAlternateColorCodes('&', line)))
                .collect(Collectors.toList());

        GuiItem pageInfoItem = ItemBuilder.from(pageInfoMaterial)
                .name(Component.text(ChatColor.YELLOW + "Page " + gui.getCurrentPageNum() + "/" + Math.max(1, gui.getPagesNum())))
                .lore(loreComponents)
                .asGuiItem(event -> event.setCancelled(true));
        gui.setItem(row, col, pageInfoItem);
    }


    private GuiItem createBossGuiItem(Player player, BossDefinition bossDef) {
        Material iconMaterial;
        String materialNameFromConfig = configManager.getConfig().getString("bosses." + bossDef.getId() + ".gui-icon", "PLAYER_HEAD");
        iconMaterial = parseMaterial(materialNameFromConfig, Material.PLAYER_HEAD, "Boss Icon (" + bossDef.getId() + ")");


        String processedName = ChatColor.translateAlternateColorCodes('&',
                bossItemNameFormat.replace("%boss_name%", bossDef.getDisplayName())
        );

        List<Component> processedLore = new ArrayList<>();
        for (String loreLine : bossItemLoreFormat) {
            String line = loreLine;
            line = line.replace("%gem_cost%", String.valueOf(bossDef.getGemCost()));
            line = line.replace("%required_level%", bossDef.getRequiredLevel() > 0 ? String.valueOf(bossDef.getRequiredLevel()) : "None");

            if (line.contains("%boss_description%")) {
                if (bossDef.getDescription() != null && !bossDef.getDescription().isEmpty()) {
                    String firstDescLine = bossDef.getDescription().get(0);
                    processedLore.add(Component.text(ChatColor.translateAlternateColorCodes('&', line.replace("%boss_description%", firstDescLine)))
                            .decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                    for (int i = 1; i < bossDef.getDescription().size(); i++) {
                        processedLore.add(Component.text(ChatColor.translateAlternateColorCodes('&', bossDef.getDescription().get(i)))
                                .decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                    }
                } else {
                    processedLore.add(Component.text(ChatColor.translateAlternateColorCodes('&', line.replace("%boss_description%", "&7No specific details.")))
                            .decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                }
            } else {
                processedLore.add(Component.text(ChatColor.translateAlternateColorCodes('&', line))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        return ItemBuilder.from(iconMaterial)
                .name(Component.text(processedName))
                .lore(processedLore)
                .asGuiItem(clickEvent -> {
                    clickEvent.setCancelled(true);
                    player.playSound(player.getLocation(), soundItemSelect, 1f, 1.2f); // Use configured sound
                    player.closeInventory();
                    handleBossSelection(player, bossDef, clickEvent);
                });
    }

    private void handleBossSelection(Player player, BossDefinition bossDef, InventoryClickEvent event) {
        // Sound is now played in createBossGuiItem's action
        player.sendMessage(configManager.getPrefix() +
                ChatColor.GREEN + "You selected: " + ChatColor.translateAlternateColorCodes('&', bossDef.getDisplayName()));

        player.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Boss fight initiation for '" +
                ChatColor.translateAlternateColorCodes('&', bossDef.getDisplayName()) + ChatColor.YELLOW + "' is pending further implementation.");
    }
}
