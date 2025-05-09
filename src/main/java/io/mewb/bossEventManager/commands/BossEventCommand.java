package io.mewb.bossEventManager.commands;

 // Import PartyInfoManager
import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.bosses.BossDefinition;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.ConfigManager;
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
    private ArenaManager arenaManager;
    private PartyInfoManager partyInfoManager; // Added field


    public BossEventCommand(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        // Get managers lazily or ensure they are checked before use
    }

    // Helper getters to ensure managers are available
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


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelpMessage(sender, label);
                break;
            case "open":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Only players can open the boss event GUI.");
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission("bosseventmanager.command.open")) {
                    player.sendMessage(configManager.getPrefix() + ChatColor.RED + "You don't have permission to open the boss event menu.");
                    return true;
                }
                openDifficultySelectionGui(player);
                break;
            case "reload":
                if (!sender.hasPermission("bosseventmanager.admin.reload")) {
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "You don't have permission to reload the configuration.");
                    return true;
                }
                reloadPluginConfiguration(sender);
                break;
            case "admin":
                if (args.length < 2) {
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin <arena|party> ...");
                    sendAdminHelp(sender, label);
                    return true;
                }
                handleAdminCommands(sender, label, args);
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown sub-command. Use /" + label + " help for assistance.");
                break;
        }
        return true;
    }

    private void handleAdminCommands(CommandSender sender, String label, String[] args) {
        String adminGroup = args[1].toLowerCase();

        if (!sender.hasPermission("bosseventmanager.admin.test")) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "You don't have permission for admin/test commands.");
            return;
        }

        switch (adminGroup) {
            case "arena":
                handleAdminArenaCommands(sender, label, args);
                break;
            case "party": // New group for party testing
                handleAdminPartyCommands(sender, label, args);
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin command group. Available: arena, party");
                break;
        }
    }

    private void handleAdminArenaCommands(CommandSender sender, String label, String[] args) {
        if (getArenaManager() == null) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "ArenaManager is not available. Admin arena commands disabled.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena <listthemes|create|starttest|listinstances|cleanup> [args...]");
            return;
        }
        // ... (existing arena command logic remains the same) ...
        String arenaAction = args[2].toLowerCase();
        switch (arenaAction) {
            case "listthemes":
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Loaded Arena Themes:");
                if (getArenaManager().getAllArenaThemes().isEmpty()) { sender.sendMessage(ChatColor.GRAY + " - None loaded."); }
                else { for (ArenaTheme theme : getArenaManager().getAllArenaThemes()) { sender.sendMessage(ChatColor.GREEN + " - ID: " + ChatColor.WHITE + theme.getId() + ChatColor.GREEN + ", File: " + ChatColor.WHITE + theme.getSchematicFile()); } }
                break;
            case "create":
                if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena create <themeId>"); return; }
                String themeIdToCreate = args[3];
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting to create arena for theme: " + themeIdToCreate + "...");
                getArenaManager().requestArena(themeIdToCreate).thenAccept(instance -> {
                    if (instance != null) { sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena instance " + instance.getInstanceId() + " created successfully at " + instance.getPlotOrigin().toString()); sender.sendMessage(configManager.getPrefix() + ChatColor.GRAY + "State: " + instance.getState()); }
                    else { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create arena instance for theme: " + themeIdToCreate + ". Check console."); }
                });
                break;
            case "starttest":
                if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena starttest <themeId> [playerName]"); return; }
                String themeIdToTest = args[3];
                Player testPlayer = null;
                if (args.length >= 5) { testPlayer = Bukkit.getPlayerExact(args[4]); if (testPlayer == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Player '" + args[4] + "' not found."); return; } }
                else if (sender instanceof Player) { testPlayer = (Player) sender; }
                else { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Specify player name from console."); return; }
                final Player finalTestPlayer = testPlayer;
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting test event (Theme: " + themeIdToTest + ", Player: " + finalTestPlayer.getName() + ")...");
                BossDefinition testBossDef = plugin.getBossManager().getAllBossDefinitions().stream().findFirst().orElse(null);
                if (testBossDef == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "No bosses configured for test."); return; }
                final BossDefinition finalTestBossDef = testBossDef;
                getArenaManager().requestArena(themeIdToTest).thenAccept(instance -> {
                    if (instance != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena " + instance.getInstanceId() + " created. Starting event...");
                            List<Player> testParty = new ArrayList<>(Collections.singletonList(finalTestPlayer));
                            getArenaManager().startEvent(instance, testParty, finalTestBossDef);
                            sender.sendMessage(configManager.getPrefix() + ChatColor.AQUA + "Test event started with boss: " + finalTestBossDef.getDisplayName());
                        });
                    } else { Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create arena for test event.")); }
                });
                break;
            case "listinstances":
                sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Active Arena Instances:");
                List<ArenaInstance> instances = getArenaManager().getActiveArenaInstances();
                if (instances.isEmpty()) { sender.sendMessage(ChatColor.GRAY + " - None active."); }
                else { for (ArenaInstance instance : instances) { sender.sendMessage(ChatColor.AQUA + " - ID: " + ChatColor.WHITE + instance.getInstanceId() + ChatColor.AQUA + ", Theme: " + ChatColor.WHITE + instance.getArenaTheme().getId() + ChatColor.AQUA + ", State: " + ChatColor.WHITE + instance.getState()); } }
                break;
            case "cleanup":
                if (args.length < 4) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena cleanup <instanceId>"); return; }
                try {
                    UUID instanceIdToClean = UUID.fromString(args[3]);
                    ArenaInstance instanceToClean = getArenaManager().getActiveArenaInstance(instanceIdToClean);
                    if (instanceToClean == null) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Instance not found: " + instanceIdToClean); return; }
                    sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Initiating cleanup for instance: " + instanceIdToClean + "...");
                    getArenaManager().endEvent(instanceToClean);
                    sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Cleanup process started.");
                } catch (IllegalArgumentException e) { sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Invalid Instance ID format."); }
                break;
            default:
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin arena action.");
                break;
        }
    }

    private void handleAdminPartyCommands(CommandSender sender, String label, String[] args) {
        if (getPartyInfoManager() == null) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "PartyInfoManager is not available. Admin party commands disabled.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin party <test> <playerName>");
            return;
        }
        String partyAction = args[2].toLowerCase();
        if ("test".equals(partyAction)) {
            if (args.length < 4) {
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin party test <playerName>");
                return;
            }
            Player targetPlayer = Bukkit.getPlayerExact(args[3]);
            if (targetPlayer == null) {
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Player '" + args[3] + "' not found online.");
                return;
            }

            sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Requesting party info for " + targetPlayer.getName() + " via BungeeCord...");

            // Request info and handle the response asynchronously
            getPartyInfoManager().requestPartyInfo(targetPlayer).whenComplete((partyInfo, throwable) -> {
                // Ensure response handling runs on the main thread if updating UI/sending messages
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Error requesting party info: " + throwable.getMessage());
                        plugin.getLogger().warning("Error in PartyInfo future: " + throwable.getMessage());
                    } else if (partyInfo == null || !partyInfo.isSuccess()) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to get party info for " + targetPlayer.getName() + " (Timeout or player not found by PAF?).");
                    } else {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Party Info Received for " + targetPlayer.getName() + ":");
                        sender.sendMessage(ChatColor.GRAY + " - In Party: " + ChatColor.WHITE + partyInfo.isInParty());
                        sender.sendMessage(ChatColor.GRAY + " - Is Leader: " + ChatColor.WHITE + partyInfo.isLeader());
                        sender.sendMessage(ChatColor.GRAY + " - Party Size: " + ChatColor.WHITE + partyInfo.getPartySize());
                        sender.sendMessage(ChatColor.GRAY + " - Members (" + partyInfo.getMemberUUIDs().size() + "):");
                        for (UUID memberUUID : partyInfo.getMemberUUIDs()) {
                            Player member = Bukkit.getPlayer(memberUUID); // Get player if online on this server
                            sender.sendMessage(ChatColor.GRAY + "   - " + ChatColor.WHITE + (member != null ? member.getName() : memberUUID.toString()));
                        }
                    }
                });
            });

        } else {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin party action. Use 'test'.");
        }
    }


    private void sendHelpMessage(CommandSender sender, String label) {
        // ... (existing help message logic) ...
        sendAdminHelp(sender, label); // Ensure admin help is included
    }

    private void sendAdminHelp(CommandSender sender, String label) {
        if (sender.hasPermission("bosseventmanager.admin.test")) {
            sender.sendMessage(ChatColor.GOLD + "--- Admin Commands ---");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena listthemes" + ChatColor.GRAY + " - ...");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena create <themeId>" + ChatColor.GRAY + " - ...");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena starttest <themeId> [player]" + ChatColor.GRAY + " - ...");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena listinstances" + ChatColor.GRAY + " - ...");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena cleanup <instanceId>" + ChatColor.GRAY + " - ...");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin party test <playerName>" + ChatColor.GRAY + " - Tests Bungee party info retrieval."); // Added party test help
        }
    }

    private void openDifficultySelectionGui(Player player) {
        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openDifficultySelectionGUI(player);
        } else {
            player.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "GUI Manager failed to load.");
        }
    }

    private void reloadPluginConfiguration(CommandSender sender) {
        configManager.reloadConfig();
        plugin.getBossManager().reloadBosses();
        if (getArenaManager() != null) {
            getArenaManager().reloadArenaThemes();
        } else {
            sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "ArenaManager not available, skipped reloading arena themes.");
        }
        sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Configurations reloaded.");
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // ... (existing tab completion for help, open, reload, admin arena) ...

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                // Add "party" to the list of admin groups
                return Arrays.asList("arena", "party").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion for admin party actions
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("party")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("test").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion for admin party test player name
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("party") && args[2].equalsIgnoreCase("test")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                // Suggest online players on the current Spigot server
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // Tab completion for admin arena actions
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("listthemes", "create", "starttest", "listinstances", "cleanup").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        // ... (existing tab completion for admin arena args[4] and args[5]) ...
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena")) {
            String action = args[2].toLowerCase();
            if (action.equals("create") || action.equals("starttest")) {
                if (sender.hasPermission("bosseventmanager.admin.test") && getArenaManager() != null) {
                    return getArenaManager().getAllArenaThemes().stream()
                            .map(ArenaTheme::getId)
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (action.equals("cleanup")) {
                if (sender.hasPermission("bosseventmanager.admin.test") && getArenaManager() != null) {
                    return getArenaManager().getActiveArenaInstances().stream()
                            .map(instance -> instance.getInstanceId().toString())
                            .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena") && args[2].equalsIgnoreCase("starttest")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[4].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }


        return Collections.emptyList();
    }
}