package me.sammy.benhockey.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.stream.Collectors;

import me.sammy.benhockey.game.GameState;
import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;


/**
 * Class that deals with all the commands related to games.
 */
public class GameCommands implements CommandExecutor {

  private final LobbyManager lobbyManager;

  public GameCommands(LobbyManager lobbyManager) {
    this.lobbyManager = lobbyManager;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (sender instanceof Player) {
      Player player = (Player) sender;

      switch (label.toLowerCase()) {
        case "rink":
            if (args.length != 1) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/rink <rinkname>");
              Set<Rink> rinks = lobbyManager.getRinks();
              String formatted = rinks.isEmpty()
                      ? "None"
                      : rinks.stream().map(Rink::getName).collect(Collectors.joining(", "));
              player.sendMessage("§6[§bBH§6] §aAvailable Rinks: §7" + formatted);
              return true;
            }
            lobbyManager.movePlayerFromLobby(args[0], player);
            return true;

        case "join":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }

          Rink joinRink = lobbyManager.getPlayerRink(player);

          if (args.length == 1) {
            if (args[0].equalsIgnoreCase("ref") && !player.isOp()) {
              player.sendMessage("§6[§bFH§6] §7You don't have perms to join as a ref.");
              return true;
            }
            joinRink.handleTeamJoin(args[0], player);
            return true;
          }
          else if (args.length == 2 && player.isOp()) {
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
              player.sendMessage("§6[§bBH§6] §cPlayer " + args[1] + " not found or not online.");
              return true;
            }
            joinRink.handleTeamJoin(args[0], targetPlayer);
            return true;
          } else {
            player.sendMessage("§6[§bBH§6] §aUsage: §7/join <home/away/fan/ref>");
            return true;
          }

        case "goalie":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }

          Rink changePos = lobbyManager.getPlayerRink(player);
          changePos.changeToGoalie(player);
          return true;

        case "stats":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }

          Rink getStats = lobbyManager.getPlayerRink(player);
          getStats.displayStats(player);
          return true;

        case "puck":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }

          Rink spawnPuck = lobbyManager.getPlayerRink(player);
          if (spawnPuck.getGameState() == GameState.PREGAME) {
            spawnPuck.summonPersonalPuck(player);
          }
          else {
            player.sendMessage("§6[§bBH§6] §cYou can only spawn pucks in pre game.");
          }
          return true;

        case "leave":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }
          Rink leaveRink = lobbyManager.getPlayerRink(player);
          leaveRink.removePlayerFromRink(player);
          lobbyManager.addPlayerToLobby(player);
          return true;

        case "startgame":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to start a game.");
            }
            else {
              Rink gameStart = lobbyManager.getPlayerRink(player);
              gameStart.startGame();
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to start a game.");
          }
          return true;

        case "pregame":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to start pregame.");
            }
            else {
              Rink gamePregame = lobbyManager.getPlayerRink(player);
              gamePregame.setToPregame();
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to set to pregame.");
          }
          return true;

        case "endgame":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to end a game.");
            }
            else {
              Rink gameEnd = lobbyManager.getPlayerRink(player);
              if (gameEnd.getGameState() != GameState.END_GAME && gameEnd.getGame() != null) {
                gameEnd.endGame();
              } else {
                player.sendMessage("§6[§bBH§6] §cThe game has already ended.");
              }
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to end a game.");
          }
          return true;

        case "lockteams":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to lock teams.");
            }
            else {
              Rink lockTeams = lobbyManager.getPlayerRink(player);
              lockTeams.lockTeams(player);
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to lock teams.");
          }
          return true;

        case "setteamname":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to set team names.");
            }
            else if (args.length == 2) {
              Rink setteamname = lobbyManager.getPlayerRink(player);
              setteamname.setTeamName(args[0], args[1], player);
              return true;
            }
            else {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/setteamname <home/away> <name>");
              return true;
            }
          }
          else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to set team names.");
          }
          return true;

        case "fo":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to skip the face off.");
            }
            else {
              Rink skipFaceoff = lobbyManager.getPlayerRink(player);
              skipFaceoff.skipFO(player);
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to skip the faceoff.");
          }
          return true;

        case "settime":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to set the time.");
            }
            else if (args.length != 1) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/settime <time>");
              return true;
            }
            else {
              Rink changeTime = lobbyManager.getPlayerRink(player);
              changeTime.updateTime(args[0], player);
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to set the time.");
          }
          return true;

        case "togglehitting":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to toggle hits.");
            } else {
              Rink hitting = lobbyManager.getPlayerRink(player);
              hitting.toggleHits(player);
            }
          }
          else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to toggle hitting.");
          }
          return true;

        case "whistle":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to blow the whistle.");
            } else {
              Rink whistle = lobbyManager.getPlayerRink(player);
              whistle.blowWhistle(player);
            }
          }
          else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission.");
          }
          return true;

        case "penalty":
          if (player.isOp() || lobbyManager.getPlayerRink(player).getTeam(player).equalsIgnoreCase("ref")) {
            if (lobbyManager.isPlayerInLobby(player)) {
              player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to give a penalty.");
              return true;
            }

            if (args.length < 1) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/penalty <give/edit/end> ...");
              return true;
            }

            String type = args[0].toLowerCase();
            Rink penalty = lobbyManager.getPlayerRink(player);

            switch (type) {
              case "give":
                if (args.length != 4) {
                  player.sendMessage("§6[§bBH§6] §aUsage: §7/penalty give <player> <reason> <time>");
                  return true;
                }
                penalty.givePenalty(player,"give", args[1], args[2], args[3]);
                break;

              case "edit":
                if (args.length != 3) {
                  player.sendMessage("§6[§bBH§6] §aUsage: §7/penalty edit <player> <time>");
                  return true;
                }
                penalty.givePenalty(player,"edit", args[1], "", args[2]);
                break;

              case "end":
                if (args.length != 2) {
                  player.sendMessage("§6[§bBH§6] §aUsage: §7/penalty end <player>");
                  return true;
                }
                penalty.givePenalty(player, "end", args[1], "", "0");
                break;

              default:
                player.sendMessage("§6[§bBH§6] §cUnknown penalty type. Use give/edit/end.");
                break;
            }
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission.");
          }
          return true;

        case "createrink":
          if (player.isOp()) {
            if (args.length != 1) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/createrink <rinkname>");
              return true;
            }
            lobbyManager.createRink(args[0], player);
            return true;
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to set up a rink.");
          }
          return true;

        case "setgoal":
          if (!player.isOp()) {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission.");
            return true;
          }
          if (args.length != 1) {
            player.sendMessage("§6[§bBH§6] §aUsage: §7/setgoal " +
                    "<home/away/penalty/homebench/awaybench>");
            return true;
          }
          lobbyManager.setGoal(args[0], player);
          return true;

        case "cancelrink":
          if (!player.isOp()) {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission.");
            return true;
          }
          lobbyManager.cancelRink(player);
          return true;

        case "deleterink":
          if (player.isOp()) {
            if (args.length != 1) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/deleterink <rinkname>");
              return true;
            }
            lobbyManager.deleteRink(args[0], player);
            return true;
          } else {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission to delete rinks.");
          }
          return true;

        case "help":
          displayHelp(player);
          return true;

        default:
          return false;
      }
    }

    return false;
  }

  private void displayHelp(Player player) {
    player.sendMessage("§6[§bBH§6] §aAvailable Commands:");

    player.sendMessage("§a/rink <rinkname> §7- Join a rink.");
    if (player.isOp()) {
      player.sendMessage("§a/join <home/away/fan/ref> <playername> §7- Join a team.");
    }
    else {
      player.sendMessage("§a/join <home/away/fan> §7- Join a team.");
    }
    player.sendMessage("§a/goalie §7- Go in and out of goalie mode.");
    player.sendMessage("§a/stats §7- Gets the stats of the current game.");
    player.sendMessage("§a/puck §7- Spawns a personal puck.");
    player.sendMessage("§a/leave §7- Return to lobby.");

    if (player.isOp()) {
      player.sendMessage("§6[§bBH§6] §cOperator Commands:");
      player.sendMessage("§a/startgame §7- Starts the game.");
      player.sendMessage("§a/pregame §7- Sets the game to pregame.");
      player.sendMessage("§a/endgame §7- Ends the game.");
      player.sendMessage("§a/lockteams §7 - Locks team joining.");
      player.sendMessage("§a/fo §7 - Skips the waiting time and starts the faceoff.");
      player.sendMessage("§a/settime <time> §7 - Sets the time in seconds.");
      player.sendMessage("§a/togglehitting §7 - Toggles hitting on and off.");
      player.sendMessage("§a/whistle §7- Stops the game.");
      player.sendMessage("§a/penalty <give/edit/end> <player> <reason> <time> §7- Gives, edits, " +
                      "or ends a penalty to the given player.");
      player.sendMessage("§a/createrink <rinkname> §7- Create a new rink.");
      player.sendMessage("§a/setgoal <red/blue/penalty> §7- Sets the specified location for " +
              "§7the rink.");
      player.sendMessage("§a/cancelrink §7- Cancel rink setup.");
      player.sendMessage("§a/deleterink <rinkname> §7- Delete a rink.");
    }
  }
}
