package me.sammy.benhockey;


import me.sammy.benhockey.commands.GameCommands;
import me.sammy.benhockey.commands.MyTabCompleter;
import me.sammy.benhockey.lobby.LobbyManager;

import me.sammy.benhockey.events.PlayerHockeyListener;
import me.sammy.benhockey.events.PlayerImportantEventsListener;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Represents the initialization
 */
public final class BenHockey extends JavaPlugin {

    private LobbyManager lobbyManager;

    @Override
    public void onEnable() {

        lobbyManager = new LobbyManager(this);

        getServer().getPluginManager().registerEvents(
                new PlayerImportantEventsListener(this.lobbyManager), this);

        PlayerHockeyListener hockeyStickListener = new PlayerHockeyListener(lobbyManager, this);
        getServer().getPluginManager().registerEvents(hockeyStickListener, this);
        Bukkit.getScheduler().runTaskTimer(this, hockeyStickListener::update, 1L, 1L);


        registerCommands(new String[]{"rink", "join", "goalie", "leave", "goalie", "stats", "puck",
                        "startgame", "pregame", "endgame", "togglehitting", "whistle", "penalty",
                        "lockteams", "setteamname", "fo", "settime", "createrink", "setgoal",
                        "cancelrink", "deleterink", "help"},
                new GameCommands(this.lobbyManager));

        getCommand("join").setTabCompleter(new MyTabCompleter(lobbyManager));
        getCommand("rink").setTabCompleter(new MyTabCompleter(lobbyManager));
        getCommand("setgoal").setTabCompleter(new MyTabCompleter(lobbyManager));
        getCommand("penalty").setTabCompleter(new MyTabCompleter(lobbyManager));
        getCommand("setteamname").setTabCompleter(new MyTabCompleter(lobbyManager));

        getLogger().info("Ben Hockey plugin has been enabled!");
    }

    @Override
    public void onDisable() {

        lobbyManager.removeLobbyPlayers();

        for (World world : Bukkit.getWorlds()) {
            for (Slime slime : world.getEntitiesByClass(Slime.class)) {
                slime.remove();
            }
        }
        getLogger().info("Ben Hockey plugin has been disabled!");
    }

    /**
     * Method that streamlines setting up and registering commands.
     * @param commands are the commands to register
     * @param executor is the gameCommands executor
     */
    private void registerCommands(String[] commands, GameCommands executor) {
        for (String command : commands) {
            if (getCommand(command) != null) {
                getCommand(command).setExecutor(executor);
            } else {
                getLogger().warning("Command " + command + " is not defined in plugin.yml");
            }
        }
    }

    //TODO
    // Fix Boards bouncy
    // Make timer or other scoreboard for intermissions or penalties
    // Add /bench to go to bench
    // make goal zones better (maybe make you set the goal on the location of the actual goal zone)
    // Think of thing to add for right click
    // Fix gloving <--
    // Add goalie shift to block saves
    // Add shot counter
    // fix slide thingy
}
