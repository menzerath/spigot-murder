package pro.marvin.minecraft.mcmurder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class CommandManager implements CommandExecutor {
    private Murder plugin;

    public CommandManager(Murder dta) {
        plugin = dta;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("mcm")) {
            // No further arguments
            if (args.length == 0) {
                // Send list of available commands for OPs and normal Players
                sender.sendMessage(Texts.COMMANDS_HEAD);
                sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_COMMANDS);
                sender.sendMessage("§6     - mcm join [arena]");
                sender.sendMessage("§6     - mcm leave");
                if (sender.isOp()) {
                    sender.sendMessage("§6     - mcm [enable/disable] [arena]");
                    sender.sendMessage("§6     - mcm setLobby");
                    sender.sendMessage("§6     - mcm setSpawn [arena] [spawn]");
                    sender.sendMessage("§6     - mcm reload");
                }
                sender.sendMessage(Texts.COPYRIGHT);
                return true;
            }

            // One additional argument
            if (args.length == 1) {
                // Teleport player to lobby if he/she is not in-game
                if (args[0].equalsIgnoreCase("lobby")) {
                    Player player = (Player) sender;
                    if (plugin.getPlayerInGame(player)) return true;
                    player.teleport(plugin.mcmSpawn());
                    return true;
                }

                // Player leaves arena
                if (args[0].equalsIgnoreCase("leave")) {
                    Player player = (Player) sender;
                    plugin.playerLeave(player);
                    return true;
                }

                // Set lobby-spawn [OP only]
                if (args[0].equalsIgnoreCase("setLobby")) {
                    if (sender.isOp()) {
                        Player player = (Player) sender;
                        List<Double> listPosition = Arrays.asList(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
                        plugin.getConfig().set("lobbyWorld", player.getWorld().getName());
                        plugin.getConfig().set("lobbySpawn", listPosition);
                        plugin.saveConfig();
                        plugin.loadConfig();
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_EXECUTED);
                    } else {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_OP_ONLY);
                    }

                    return true;
                }

                // Reload config [OP only]
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.isOp()) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_OP_ONLY);
                        return true;
                    }
                    plugin.loadConfig();
                    sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_EXECUTED);
                    return true;
                }
            }

            // Two additional arguments
            if (args.length == 2) {
                // Player joins arena
                if (args[0].equalsIgnoreCase("join") && sender instanceof Player) {
                    Player p = (Player) sender;

                    int map;
                    try {
                        map = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_ARENA);
                        return true;
                    }

                    if (map > 100 || map < 0 || !plugin.arenaConfig.get(map)) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INACTIVE_ARENA);
                        return true;
                    }

                    if (plugin.getGameStarted(map)) {
                        p.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_GAME_RUNNING);
                        return true;
                    }

                    plugin.playerJoinedArena(p, map);
                    return true;
                }

                // Enabled an arena [OP only]
                if (args[0].equalsIgnoreCase("enable")) {
                    if (!sender.isOp()) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_OP_ONLY);
                        return true;
                    }

                    int map;
                    try {
                        map = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_NUMBER);
                        return true;
                    }

                    if (map < 1 || map > 100) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_NUMBER);
                        return true;
                    }

                    plugin.getConfig().set("maps." + map + ".enabled", true);
                    plugin.saveConfig();
                    plugin.loadConfig();
                    sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_EXECUTED);
                    return true;
                }

                // Disables an arena [OP only]
                if (args[0].equalsIgnoreCase("disable")) {
                    if (!sender.isOp()) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_OP_ONLY);
                        return true;
                    }

                    int map;
                    try {
                        map = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_NUMBER);
                        return true;
                    }

                    if (map < 1 || map > 100) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_NUMBER);
                        return true;
                    }

                    plugin.getConfig().set("maps." + map + ".enabled", false);
                    plugin.saveConfig();
                    plugin.loadConfig();
                    sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_EXECUTED);

                    plugin.stopGame(map, false);
                    return true;
                }

                return true;
            }

            // Three additional arguments
            if (args.length == 3) {
                // Set one spawn in a map [OP only]
                if (args[0].equalsIgnoreCase("setSpawn")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_PLAYER_ONLY);
                        return true;
                    }

                    if (!sender.isOp()) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_OP_ONLY);
                        return true;
                    }

                    int arg1;
                    int arg2;
                    try {
                        arg1 = Integer.parseInt(args[1]);
                        arg2 = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_NUMBER);
                        return true;
                    }

                    if (arg1 < 100 && arg1 > 0 && arg2 < 9 && arg2 > 0) {
                        Player player = (Player) sender;
                        List<Double> listPosition = Arrays.asList(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ());
                        plugin.getConfig().set("maps." + arg1 + "." + arg2, listPosition);
                        plugin.saveConfig();
                        plugin.loadConfig();
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_EXECUTED);
                        return true;
                    } else {
                        sender.sendMessage(Texts.PRE_TEXT + Texts.COMMANDS_INVALID_SPAWNS);
                        return true;
                    }
                }

            }
        }
        return false;
    }
}