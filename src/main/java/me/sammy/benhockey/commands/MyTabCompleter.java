package me.sammy.benhockey.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import me.sammy.benhockey.lobby.LobbyManager;
import me.sammy.benhockey.game.Rink;


/**
 * Class that assists with filling in the tab for certain game commands.
 */
public class MyTabCompleter implements TabCompleter {

  private final LobbyManager lobbyManager;

  public MyTabCompleter(LobbyManager lobbyManager) {
    this.lobbyManager = lobbyManager;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (command.getName().equalsIgnoreCase("join")) {
      if (args.length == 1) {
        List<String> options = Arrays.asList("home", "away", "fan", "ref");
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
      } else if (args.length == 2 && sender.isOp()) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
      }
    }

    if (command.getName().equalsIgnoreCase("rink")) {
      if (args.length == 1) {
        return lobbyManager.getRinks().stream()
                .map(Rink::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
      }
    }

    if (command.getName().equalsIgnoreCase("setgoal")) {
      if (args.length == 1) {
        return Arrays.asList("home", "away", "penalty", "homebench", "awaybench").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
      }
    }

    if (command.getName().equalsIgnoreCase("penalty")) {
      if (args.length == 1) {
        List<String> options = Arrays.asList("give", "edit", "end");
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
      } else if (args.length == 2 && sender.isOp()) {
        String type = args[0].toLowerCase();
        if (type.equals("give") || type.equals("edit") || type.equals("end")) {
          return Bukkit.getOnlinePlayers().stream()
                  .map(Player::getName)
                  .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                  .collect(Collectors.toList());
        }
      }
    }

    if (command.getName().equalsIgnoreCase("setteamname")) {
      if (args.length == 1) {
        return Arrays.asList("home", "away").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
      }
    }

    return Collections.emptyList();
  }
}
