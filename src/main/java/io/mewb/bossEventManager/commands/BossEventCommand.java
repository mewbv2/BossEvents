package io.mewb.bossEventManager.commands;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.bosses.BossDefinition;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import io.mewb.bossEventManager.managers.GuiManager;
import io.mewb.bossEventManager.party.PartyInfoManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BossEventCommand implements CommandExecutor, TabCompleter {

    private final BossEventManagerPlugin plugin;
    private final ConfigManager configManager;
    // Managers retrieved lazily to handle potential delayed initialization
    private ArenaManager arenaManager;
    private PartyInfoManager partyInfoManager;
    private GuiManager guiManager;


    public BossEventCommand(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        // Initialize managers lazily via getters
    }

    // Helper getters to ensure managers are available when needed
    private ArenaManager getArenaManager() {
        if (this.arenaManager == null) {
            this.arenaManager = plugin.getArenaManager();
        }
        return this.arenaManager;
    }

    private PartyInfoManager getPartyInfoManager() {
        if (this.partyInfoManager == null) {
            this.partyInfoManager = plugin.getPartyInfoManager();
        }
        return this.partyInfoManager;
    }

    private GuiManager getGuiManager() {
        if (this.guiManager == null) {
            this.guiManager = plugin.getGuiManager();
        }
        return this.guiManager;
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 'label' will be the actual command alias used by the player (e.g., "events", "event", "ev")

        if (args.length == 0) {
            sendHelpMessage(sender, label); // Pass the used label
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelpMessage(sender, label); // Pass the used label
                break;
            case "open":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Only players can open the event GUI.");
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission("bosseventmanager.command.open")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                openDifficultySelectionGui(player);
                break;
            case "reload":
                if (!sender.hasPermission("bosseventmanager.admin.reload")) {
                    sender.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                reloadPluginConfiguration(sender);
                break;
            case "admin":
                if (args.length < 2) {
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin <arena|party> ...");
                    sendAdminHelp(sender, label); // Pass the used label
                    return true;
                }
                handleAdminCommands(sender, label, args); // Pass the used label
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown sub-command. Use /" + label + " help for assistance.");
                break;
        }
        return true;
    }

    // Helper method to open the initial GUI screen
    private void openDifficultySelectionGui(Player player) {
        GuiManager currentGuiManager = getGuiManager();
        if (currentGuiManager != null) {
            currentGuiManager.openDifficultySelectionGUI(player);
        } else {
            player.sendMessage(configManager.getPrefix() + ChatColor.RED + "Error: GUI Manager is not available.");
            plugin.getLogger().severe("GuiManager was null when attempting to open difficulty selection GUI for " + player.getName());
        }
    }

    // --- Admin Command Handling ---
    private void handleAdminCommands(CommandSender sender, String label, String[] args) {
        // args[0] is "admin"
        String adminGroup = args[1].toLowerCase();

        if (!sender.hasPermission("bosseventmanager.admin.test")) { // This permission covers all admin sub-commands for now
            sender.sendMessage(configManager.getMessage("no-permission"));
            return;
        }

        switch (adminGroup) {
            case "arena":
                handleAdminArenaCommands(sender, label, args); // Pass label
                break;
            case "party":
                handleAdminPartyCommands(sender, label, args); // Pass label
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin command group. Use: /" + label + " admin <arena|party>");
                break;
        }
    }

    private void handleAdminArenaCommands(CommandSender sender, String label, String[] args) {
        ArenaManager currentArenaManager = getArenaManager();
        if (currentArenaManager == null) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "ArenaManager is not available. Admin arena commands disabled.");
            return;
        }
        // args[0] is "admin", args[1] is "arena"
        if (args.length < 3) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena <listthemes|create|starttest|listinstances|cleanup> [args...]");
            return;
        }
        String arenaAction = args[2].toLowerCase();
        switch (arenaAction) {
            case "listthemes":
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Loaded Arena Themes:");
                if (currentArenaManager.getAllArenaThemes().isEmpty()) { sender.sendMessage(ChatColor.GRAY + " - None loaded."); }
                else { for (ArenaTheme theme : currentArenaManager.getAllArenaThemes()) { sender.sendMessage(ChatColor.GREEN + " - ID: " + ChatColor.WHITE + theme.getId() + ChatColor.GREEN + ", File: " + ChatColor.WHITE + theme.getSchematicFile()); } }
                break;
            case "create":
                if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena create <themeId>"); return; }
                String themeIdToCreate = args[3];
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting to create arena for theme: " + themeIdToCreate + "...");
                currentArenaManager.requestArena(themeIdToCreate).thenAccept(instance -> {
                    Bukkit.getScheduler().runTask(plugin, () -> { // Ensure message sending is sync
                        if (instance != null) { sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena instance " + instance.getInstanceId() + " created successfully at " + instance.getPlotOrigin().toString()); sender.sendMessage(configManager.getPrefix() + ChatColor.GRAY + "State: " + instance.getState()); }
                        else { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create arena instance for theme: " + themeIdToCreate + ". Check console."); }
                    });
                });
                break;
            case "starttest":
                if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena starttest <themeId> [playerName]"); return; }
                String themeIdToTest = args[3];
                Player testPlayer = null;
                if (args.length >= 5) { testPlayer = Bukkit.getPlayerExact(args[4]); if (testPlayer == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Player '" + args[4] + "' not found."); return; } }
                else if (sender instanceof Player) { testPlayer = (Player) sender; }
                else { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Specify player name from console for starttest."); return; }
                final Player finalTestPlayer = testPlayer;
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting test event (Theme: " + themeIdToTest + ", Player: " + finalTestPlayer.getName() + ")...");
                BossDefinition testBossDef = plugin.getBossManager().getAllBossDefinitions().stream().findFirst().orElse(null);
                if (testBossDef == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "No bosses configured for test."); return; }
                final BossDefinition finalTestBossDef = testBossDef;
                currentArenaManager.requestArena(themeIdToTest).thenAccept(instance -> {
                    if (instance != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena " + instance.getInstanceId() + " created. Starting event...");
                            List<Player> testParty = new ArrayList<>(Collections.singletonList(finalTestPlayer));
                            currentArenaManager.startEvent(instance, testParty, finalTestBossDef);
                            sender.sendMessage(configManager.getPrefix() + ChatColor.AQUA + "Test event started with boss: " + finalTestBossDef.getDisplayName());
                        });
                    } else { Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create arena for test event.")); }
                });
                break;
            case "listinstances":
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Active Arena Instances:");
                List<ArenaInstance> instances = currentArenaManager.getActiveArenaInstances();
                if (instances.isEmpty()) { sender.sendMessage(ChatColor.GRAY + " - None active."); }
                else { for (ArenaInstance instance : instances) { sender.sendMessage(ChatColor.AQUA + " - ID: " + ChatColor.WHITE + instance.getInstanceId() + ChatColor.AQUA + ", Theme: " + ChatColor.WHITE + instance.getArenaTheme().getId() + ChatColor.AQUA + ", State: " + ChatColor.WHITE + instance.getState()); } }
                break;
            case "cleanup":
                if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena cleanup <instanceId>"); return; }
                try {
                    UUID instanceIdToClean = UUID.fromString(args[3]);
                    ArenaInstance instanceToClean = currentArenaManager.getActiveArenaInstance(instanceIdToClean);
                    if (instanceToClean == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Instance not found: " + instanceIdToClean); return; }
                    sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Initiating cleanup for instance: " + instanceIdToClean + "...");
                    currentArenaManager.endEvent(instanceToClean);
                    sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Cleanup process started.");
                } catch (IllegalArgumentException e) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Invalid Instance ID format."); }
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin arena action. Use: listthemes, create, starttest, listinstances, cleanup.");
                break;
        }
    }

    private void handleAdminPartyCommands(CommandSender sender, String label, String[] args) {
        PartyInfoManager currentPartyManager = getPartyInfoManager();
        if (currentPartyManager == null) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "PartyInfoManager is not available. Admin party commands disabled.");
            return;
        }
        // args[0] is "admin", args[1] is "party"
        if (args.length < 3) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin party <test> <playerName>");
            return;
        }
        String partyAction = args[2].toLowerCase();
        if ("test".equals(partyAction)) {
            if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin party test <playerName>"); return; }
            Player targetPlayer = Bukkit.getPlayerExact(args[3]);
            if (targetPlayer == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Player '" + args[3] + "' not found online."); return; }
            sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Requesting party info for " + targetPlayer.getName() + " via BungeeCord...");
            currentPartyManager.requestPartyInfo(targetPlayer).whenComplete((partyInfo, throwable) -> {
                Bukkit.getScheduler().runTask(plugin, () -> { // Ensure message sending is sync
                    if (throwable != null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Error requesting party info: " + throwable.getMessage()); plugin.getLogger().warning("Error in PartyInfo future: " + throwable.getMessage()); }
                    else if (partyInfo == null || !partyInfo.isSuccess()) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to get party info for " + targetPlayer.getName() + " (Timeout or player not found by PAF?)."); }
                    else {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Party Info Received for " + targetPlayer.getName() + ":");
                        sender.sendMessage(ChatColor.GRAY + " - In Party: " + ChatColor.WHITE + partyInfo.isInParty());
                        sender.sendMessage(ChatColor.GRAY + " - Is Leader: " + ChatColor.WHITE + partyInfo.isLeader());
                        sender.sendMessage(ChatColor.GRAY + " - Party Size: " + ChatColor.WHITE + partyInfo.getPartySize());
                        sender.sendMessage(ChatColor.GRAY + " - Members (" + partyInfo.getMemberUUIDs().size() + "):");
                        for (UUID memberUUID : partyInfo.getMemberUUIDs()) { Player member = Bukkit.getPlayer(memberUUID); sender.sendMessage(ChatColor.GRAY + "   - " + ChatColor.WHITE + (member != null ? member.getName() : memberUUID.toString())); }
                    }
                });
            });
        } else {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin party action. Use 'test'.");
        }
    }

    // --- Help and Reload ---
    private void sendHelpMessage(CommandSender sender, String label) {
        String prefix = configManager.getPrefix();
        sender.sendMessage(ChatColor.GOLD + "--- " + prefix + ChatColor.GOLD + "Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " open" + ChatColor.GRAY + " - Opens the event selection menu.");
        if (sender.hasPermission("bosseventmanager.admin.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - Reloads plugin configurations.");
        }
        sendAdminHelp(sender, label); // Pass label
    }
    private void sendAdminHelp(CommandSender sender, String label) {
        if (sender.hasPermission("bosseventmanager.admin.test")) {
            sender.sendMessage(ChatColor.GOLD + "--- Admin Commands ---");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena listthemes" + ChatColor.GRAY + " - Lists loaded arena themes.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena create <themeId>" + ChatColor.GRAY + " - Creates a test arena (no event start).");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena starttest <themeId> [player]" + ChatColor.GRAY + " - Creates arena & starts test event for player.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena listinstances" + ChatColor.GRAY + " - Lists active arena instances.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena cleanup <instanceId>" + ChatColor.GRAY + " - Cleans up an arena instance.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin party test <playerName>" + ChatColor.GRAY + " - Tests Bungee party info retrieval.");
        }
    }
    private void reloadPluginConfiguration(CommandSender sender) {
        configManager.reloadConfig();
        if (plugin.getBossManager() != null) plugin.getBossManager().reloadBosses(); // Check for null
        if (getArenaManager() != null) { // Use getter for lazy init
            getArenaManager().reloadArenaThemes();
        } else {
            sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "ArenaManager not available, skipped reloading arena themes.");
        }
        sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Configurations reloaded.");
    }


    // --- Tab Completion ---
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // alias will be the actual command alias used by the player
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("help", "open"));
            if (sender.hasPermission("bosseventmanager.admin.reload")) { subCommands.add("reload"); }
            if (sender.hasPermission("bosseventmanager.admin.test")) { subCommands.add("admin"); }
            return subCommands.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("arena", "party").stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("arena") && sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("listthemes", "create", "starttest", "listinstances", "cleanup").stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            } else if (args[1].equalsIgnoreCase("party") && sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("test").stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("arena")) {
                String action = args[2].toLowerCase();
                if (action.equals("create") || action.equals("starttest")) {
                    if (sender.hasPermission("bosseventmanager.admin.test") && getArenaManager() != null) { // Use getter
                        return getArenaManager().getAllArenaThemes().stream().map(ArenaTheme::getId).filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                    }
                } else if (action.equals("cleanup")) {
                    if (sender.hasPermission("bosseventmanager.admin.test") && getArenaManager() != null) { // Use getter
                        return getArenaManager().getActiveArenaInstances().stream().map(instance -> instance.getInstanceId().toString()).filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                    }
                }
            } else if (args[1].equalsIgnoreCase("party") && args[2].equalsIgnoreCase("test")) {
                if (sender.hasPermission("bosseventmanager.admin.test")) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                }
            }
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena") && args[2].equalsIgnoreCase("starttest")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[4].toLowerCase())).collect(Collectors.toList());
            }
        }

        return Collections.emptyList(); // Return empty list if no match
    }
}