package io.mewb.bossEventManager.commands;


import io.mewb.bossEventManager.BossEventManagerPlugin;
import io.mewb.bossEventManager.arena.ArenaInstance;
import io.mewb.bossEventManager.arena.ArenaTheme;
import io.mewb.bossEventManager.bosses.BossDefinition;
import io.mewb.bossEventManager.managers.ArenaManager;
import io.mewb.bossEventManager.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler; // Import BukkitScheduler
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


    public BossEventCommand(BossEventManagerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    private ArenaManager getArenaManager() {
        if (this.arenaManager == null) {
            this.arenaManager = plugin.getArenaManager();
        }
        return this.arenaManager;
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
                openBossSelectionGui(player);
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
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin <arena> ...");
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

        if (getArenaManager() == null) {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "ArenaManager is not available. Admin arena commands disabled.");
            return;
        }

        if ("arena".equals(adminGroup)) {
            if (args.length < 3) {
                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena <listthemes|create|starttest|listinstances|cleanup> [args...]");
                return;
            }
            String arenaAction = args[2].toLowerCase();
            switch (arenaAction) {
                case "listthemes":
                    sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Loaded Arena Themes:");
                    if (getArenaManager().getAllArenaThemes().isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + " - None loaded.");
                    } else {
                        for (ArenaTheme theme : getArenaManager().getAllArenaThemes()) {
                            sender.sendMessage(ChatColor.GREEN + " - ID: " + ChatColor.WHITE + theme.getId() +
                                    ChatColor.GREEN + ", Name: " + ChatColor.WHITE + theme.getDisplayName() +
                                    ChatColor.GREEN + ", File: " + ChatColor.WHITE + theme.getSchematicFile());
                        }
                    }
                    break;
                case "create":
                    if (args.length < 4) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena create <themeId>");
                        return;
                    }
                    String themeIdToCreate = args[3];
                    sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting to create arena for theme: " + themeIdToCreate + "...");
                    getArenaManager().requestArena(themeIdToCreate).thenAccept(instance -> {
                        if (instance != null) {
                            sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena instance " + instance.getInstanceId() + " created successfully at " + instance.getPlotOrigin().toString());
                            sender.sendMessage(configManager.getPrefix() + ChatColor.GRAY + "It is currently in state: " + instance.getState());
                        } else {
                            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create arena instance for theme: " + themeIdToCreate + ". Check console for errors.");
                        }
                    });
                    break;
                case "starttest":
                    if (args.length < 4) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena starttest <themeId> [playerName]");
                        return;
                    }
                    String themeIdToTest = args[3];
                    Player testPlayer = null;
                    if (args.length >= 5) {
                        testPlayer = Bukkit.getPlayerExact(args[4]);
                        if (testPlayer == null) {
                            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Player '" + args[4] + "' not found for test event.");
                            return;
                        }
                    } else if (sender instanceof Player) {
                        testPlayer = (Player) sender;
                    } else {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "You must specify a player name if running this from console.");
                        return;
                    }

                    final Player finalTestPlayer = testPlayer;
                    sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting to start test event with theme '" + themeIdToTest + "' for player " + finalTestPlayer.getName() + "...");

                    BossDefinition testBossDef = plugin.getBossManager().getAllBossDefinitions().stream().findFirst().orElse(null);
                    if (testBossDef == null) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "No boss definitions found to use for the test event. Please configure bosses.");
                        return;
                    }
                    final BossDefinition finalTestBossDef = testBossDef;

                    // Request arena asynchronously
                    getArenaManager().requestArena(themeIdToTest).thenAccept(instance -> {
                        if (instance != null) {
                            // Arena created, now schedule the event start synchronously
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Arena instance " + instance.getInstanceId() + " created for test. Starting event...");
                                List<Player> testParty = new ArrayList<>();
                                testParty.add(finalTestPlayer);
                                // Start the event (includes teleport and boss spawn) - This now runs synchronously
                                getArenaManager().startEvent(instance, testParty, finalTestBossDef);
                                sender.sendMessage(configManager.getPrefix() + ChatColor.AQUA + "Test event started with boss: " + finalTestBossDef.getDisplayName());
                            });
                        } else {
                            // Send failure message back to the command sender (might need to run this sync too if sender is console)
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Failed to create arena for test event with theme: " + themeIdToTest + ".");
                            });
                        }
                    });
                    break;
                case "listinstances":
                    sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Active Arena Instances:");
                    List<ArenaInstance> instances = getArenaManager().getActiveArenaInstances();
                    if (instances.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + " - None active.");
                    } else {
                        for (ArenaInstance instance : instances) {
                            sender.sendMessage(ChatColor.AQUA + " - ID: " + ChatColor.WHITE + instance.getInstanceId() +
                                    ChatColor.AQUA + ", Theme: " + ChatColor.WHITE + instance.getArenaTheme().getId() +
                                    ChatColor.AQUA + ", State: " + ChatColor.WHITE + instance.getState() +
                                    ChatColor.AQUA + ", Origin: " + ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", instance.getPlotOrigin().getX(), instance.getPlotOrigin().getY(), instance.getPlotOrigin().getZ()));
                        }
                    }
                    break;
                case "cleanup":
                    if (args.length < 4) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Usage: /" + label + " admin arena cleanup <instanceId>");
                        return;
                    }
                    try {
                        UUID instanceIdToClean = UUID.fromString(args[3]);
                        ArenaInstance instanceToClean = getArenaManager().getActiveArenaInstance(instanceIdToClean);
                        if (instanceToClean == null) {
                            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "No active arena instance found with ID: " + instanceIdToClean);
                            return;
                        }
                        sender.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Attempting to cleanup arena instance: " + instanceIdToClean + "...");
                        getArenaManager().endEvent(instanceToClean);
                        sender.sendMessage(configManager.getPrefix() + ChatColor.GREEN + "Cleanup process initiated for " + instanceIdToClean + ". Check console for details.");

                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Invalid Instance ID format.");
                    }
                    break;
                default:
                    sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin arena action. Use listthemes, create, starttest, listinstances, cleanup.");
                    break;
            }
        } else {
            sender.sendMessage(configManager.getPrefix() + ChatColor.RED + "Unknown admin command group. Available: arena");
        }
    }

    private void sendHelpMessage(CommandSender sender, String label) {
        String prefix = configManager.getPrefix();
        sender.sendMessage(ChatColor.GOLD + "--- " + prefix + ChatColor.GOLD + "Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " open" + ChatColor.GRAY + " - Opens the boss event selection menu.");
        if (sender.hasPermission("bosseventmanager.admin.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - Reloads plugin configurations.");
        }
        sendAdminHelp(sender, label);
    }

    private void sendAdminHelp(CommandSender sender, String label) {
        if (sender.hasPermission("bosseventmanager.admin.test")) {
            sender.sendMessage(ChatColor.GOLD + "--- Admin Arena Commands ---");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena listthemes" + ChatColor.GRAY + " - Lists loaded arena themes.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena create <themeId>" + ChatColor.GRAY + " - Creates a test arena (no event start).");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena starttest <themeId> [player]" + ChatColor.GRAY + " - Creates arena & starts test event for player.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena listinstances" + ChatColor.GRAY + " - Lists active arena instances.");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " admin arena cleanup <instanceId>" + ChatColor.GRAY + " - Cleans up an arena instance.");
        }
    }


    private void openBossSelectionGui(Player player) {
        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openBossSelectionGUI(player);
        } else {
            player.sendMessage(configManager.getPrefix() + ChatColor.YELLOW + "Boss selection GUI is not yet implemented or GuiManager failed to load.");
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
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("help", "open"));
            if (sender.hasPermission("bosseventmanager.admin.reload")) {
                subCommands.add("reload");
            }
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                subCommands.add("admin");
            }
            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("arena").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("arena")) {
            if (sender.hasPermission("bosseventmanager.admin.test")) {
                return Arrays.asList("listthemes", "create", "starttest", "listinstances", "cleanup").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

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