package me.sammy.benhockey.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.sammy.benhockey.BenHockey;
import java.util.stream.Collectors;

import me.sammy.benhockey.game.GameState;
import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;


/**
 * Class that deals with all the commands related to games.
 */
public class GameCommands implements CommandExecutor {

  private final LobbyManager lobbyManager;
  private final Map<UUID, FightInvite> pendingFightInvites = new HashMap<>();
  private final Set<UUID> acceptedFightInvites = new HashSet<>();

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
        case "team":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }

          Rink joinRink = lobbyManager.getPlayerRink(player);
          String teamArg = null;
          String targetArg = null;

          if (label.equalsIgnoreCase("team")) {
            if (args.length < 2 || !args[0].equalsIgnoreCase("join")) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/team join <home/away/fan/ref>");
              return true;
            }
            teamArg = args[1];
            if (args.length >= 3) {
              targetArg = args[2];
            }
          } else if (args.length >= 1) {
            teamArg = args[0];
            if (args.length >= 2) {
              targetArg = args[1];
            }
          }

          if (teamArg != null && targetArg == null) {
            if (teamArg.equalsIgnoreCase("ref") && !player.isOp()) {
              player.sendMessage("§6[§bFH§6] §7You don't have perms to join as a ref.");
              return true;
            }
            joinRink.handleTeamJoin(teamArg, player);
            return true;
          }
          else if (teamArg != null && targetArg != null && player.isOp()) {
            Player targetPlayer = Bukkit.getPlayer(targetArg);
            if (targetPlayer == null) {
              player.sendMessage("§6[§bBH§6] §cPlayer " + targetArg + " not found or not online.");
              return true;
            }
            joinRink.handleTeamJoin(teamArg, targetPlayer);
            return true;
          } else {
            if (label.equalsIgnoreCase("team")) {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/team join <home/away/fan/ref>");
            } else {
              player.sendMessage("§6[§bBH§6] §aUsage: §7/team join <home/away/fan/ref>");
            }
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

        case "bench":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }
          Rink benchRink = lobbyManager.getPlayerRink(player);
          benchRink.sendPlayerToBench(player);
          return true;

        case "broadcast":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou must be in a rink to use this command.");
            return true;
          }

          Rink broadcastRink = lobbyManager.getPlayerRink(player);
          if (!broadcastRink.isFan(player)) {
            player.sendMessage("§6[§bBH§6] §cOnly fans can use /broadcast.");
            return true;
          }

          if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
          }

          if (player.getSpectatorTarget() == broadcastRink.getSpectatorCamera()) {
            player.setSpectatorTarget(null);
            player.teleport(broadcastRink.getFanSpawnLocation());
            player.sendMessage("§6[§bBH§6] §aBroadcast camera disabled.");
          } else {
            org.bukkit.entity.ArmorStand camera = broadcastRink.ensureSpectatorCamera();
            if (camera == null) {
              player.sendMessage("§6[§bBH§6] §cUnable to spawn the broadcast camera right now.");
              return true;
            }
            player.setSpectatorTarget(camera);
            player.sendMessage("§6[§bBH§6] §aBroadcast camera enabled.");
          }
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

        case "startfight":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to start a fight.");
            return true;
          }
          if (!player.isOp() && !"ref".equalsIgnoreCase(lobbyManager.getPlayerRink(player).getTeam(player))) {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission.");
            return true;
          }
          Rink fightRink = lobbyManager.getPlayerRink(player);
          if (fightRink.getGameState() != GameState.GAME || fightRink.getGame() == null) {
            player.sendMessage("§6[§bBH§6] §cA game must be going to start a fight.");
            return true;
          }
          if (!fightRink.getGame().isPaused()) {
            player.sendMessage("§6[§bBH§6] §cThe game must be paused for a fight to start.");
            return true;
          }
          if (args.length != 1 && args.length != 2) {
            player.sendMessage("§6[§bBH§6] §aUsage: §7/startfight <player1> <player2> or /startfight brawl");
            return true;
          }
          if (args.length == 1 && "brawl".equalsIgnoreCase(args[0])) {
            Set<Player> fighters = new HashSet<>();
            fighters.addAll(fightRink.getHomeTeamPlayers());
            fighters.addAll(fightRink.getAwayTeamPlayers());
            fightRink.startFight(new java.util.ArrayList<>(fighters), true);
            for (Player rinkPlayer : fightRink.getAllPlayers()) {
              rinkPlayer.sendMessage("§6[§bBH§6] §cA team brawl has started!");
            }
            return true;
          }
          if (args.length != 2) {
            player.sendMessage("§6[§bBH§6] §aUsage: §7/startfight <player1> <player2> or /startfight brawl");
            return true;
          }
          Player fighterOne = Bukkit.getPlayer(args[0]);
          Player fighterTwo = Bukkit.getPlayer(args[1]);
          if (fighterOne == null || fighterTwo == null) {
            player.sendMessage("§6[§bBH§6] §cBoth players must be online.");
            return true;
          }
          if (!fightRink.containsPlayer(fighterOne) || !fightRink.containsPlayer(fighterTwo)) {
            player.sendMessage("§6[§bBH§6] §cBoth players must be in your rink.");
            return true;
          }
          createFightInvite(fightRink, fighterOne, fighterTwo, player);
          return true;

        case "acceptfight":
          if (lobbyManager.isPlayerInLobby(player)) {
            return true;
          }
          FightInvite acceptInvite = pendingFightInvites.get(player.getUniqueId());
          if (acceptInvite == null) {
            player.sendMessage("§6[§bBH§6] §cYou don't have a pending fight invite.");
            return true;
          }
          this.acceptedFightInvites.add(player.getUniqueId());
          player.sendMessage("§6[§bBH§6] §aYou accepted the fight challenge.");
          Player opponent = Bukkit.getPlayer(acceptInvite.getOpponentId(player.getUniqueId()));
          if (opponent != null) {
            opponent.sendMessage("§6[§bBH§6] §e" + player.getName() + " has accepted the fight.");
          }
          if (acceptedFightInvites.contains(acceptInvite.playerOneId) && acceptedFightInvites.contains(acceptInvite.playerTwoId)) {
            Rink inviteRink = acceptInvite.rink;
            inviteRink.startFight(java.util.Arrays.asList(
                    Bukkit.getPlayer(acceptInvite.playerOneId),
                    Bukkit.getPlayer(acceptInvite.playerTwoId)
            ), false);
            for (Player rinkPlayer : inviteRink.getAllPlayers()) {
              rinkPlayer.sendMessage("§6[§bBH§6] §cFight started: §f" + acceptInvite.playerOneName
                      + " §7vs §f" + acceptInvite.playerTwoName);
            }
            pendingFightInvites.remove(acceptInvite.playerOneId);
            pendingFightInvites.remove(acceptInvite.playerTwoId);
            acceptedFightInvites.remove(acceptInvite.playerOneId);
            acceptedFightInvites.remove(acceptInvite.playerTwoId);
          }
          return true;

        case "declinefight":
          FightInvite declineInvite = pendingFightInvites.remove(player.getUniqueId());
          if (declineInvite == null) {
            player.sendMessage("§6[§bBH§6] §cYou don't have a pending fight invite.");
            return true;
          }
          pendingFightInvites.remove(declineInvite.getOpponentId(player.getUniqueId()));
          acceptedFightInvites.remove(declineInvite.playerOneId);
          acceptedFightInvites.remove(declineInvite.playerTwoId);
          UUID otherId = declineInvite.getOpponentId(player.getUniqueId());
          Player otherPlayer = Bukkit.getPlayer(otherId);
          if (otherPlayer != null) {
            otherPlayer.sendMessage("§6[§bBH§6] §c" + player.getName() + " has declined to fight.");
          }
          player.sendMessage("§6[§bBH§6] §cYou have declined to fight "
                  + declineInvite.getOpponentName(player.getUniqueId()) + ".");
          return true;

        case "endfight":
          if (lobbyManager.isPlayerInLobby(player)) {
            player.sendMessage("§6[§bBH§6] §cYou need to be in a rink to end a fight.");
            return true;
          }
          if (!player.isOp() && !"ref".equalsIgnoreCase(lobbyManager.getPlayerRink(player).getTeam(player))) {
            player.sendMessage("§6[§bBH§6] §cYou do not have permission.");
            return true;
          }
          Rink endFightRink = lobbyManager.getPlayerRink(player);
          endFightRink.endFight();
          player.sendMessage("§6[§bBH§6] §aFight ended.");
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
          if (args.length < 1 || args.length > 2) {
            player.sendMessage("§6[§bBH§6] §aUsage: §7/setgoal " +
                    "<home/away/penalty/homebench/awaybench/camera> [default]");
            return true;
          }
          boolean defaultCamera = args.length == 2
                  && "camera".equalsIgnoreCase(args[0])
                  && "default".equalsIgnoreCase(args[1]);
          if (args.length == 2 && !defaultCamera) {
            player.sendMessage("§6[§bBH§6] §aUsage: §7/setgoal camera default");
            return true;
          }
          lobbyManager.setGoal(args[0], player, defaultCamera);
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


        case "cosmetics":
          ((BenHockey) this.lobbyManager.getPlugin()).getCosmeticsMenuListener().openMainMenu(player);
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
    player.sendMessage("§a/bench §7- Teleport to your team's bench.");
    player.sendMessage("§a/broadcast §7- Toggle rink broadcast camera (fans only).");
    player.sendMessage("§a/cosmetics §7- Open the cosmetics menu.");

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
      player.sendMessage("§a/startfight <p1> <p2>/brawl §7- Starts a fight while game is paused.");
      player.sendMessage("§a/endfight §7- Ends the current fight.");
      player.sendMessage("§a/createrink <rinkname> §7- Create a new rink.");
      player.sendMessage("§a/setgoal <home/away/penalty/homebench/awaybench/camera> §7- Sets the " +
              "specified location for the rink.");
      player.sendMessage("§a/setgoal camera default §7- Use the auto camera position for this rink.");
      player.sendMessage("§a/cancelrink §7- Cancel rink setup.");
      player.sendMessage("§a/deleterink <rinkname> §7- Delete a rink.");
    }
  }

  private void createFightInvite(Rink rink, Player fighterOne, Player fighterTwo, Player ref) {
    FightInvite invite = new FightInvite(rink, fighterOne, fighterTwo);
    this.pendingFightInvites.put(fighterOne.getUniqueId(), invite);
    this.pendingFightInvites.put(fighterTwo.getUniqueId(), invite);
    this.acceptedFightInvites.remove(fighterOne.getUniqueId());
    this.acceptedFightInvites.remove(fighterTwo.getUniqueId());

    sendFightPrompt(fighterOne, fighterTwo.getName());
    sendFightPrompt(fighterTwo, fighterOne.getName());
    ref.sendMessage("§6[§bBH§6] §aFight invite sent to " + fighterOne.getName() + " and " + fighterTwo.getName() + ".");
  }

  private void sendFightPrompt(Player player, String opponentName) {
    TextComponent prefix = new TextComponent("§6[§bBH§6] §eFight request vs §f" + opponentName + "§e: ");
    TextComponent accept = new TextComponent("§a[ACCEPT]");
    accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Click to accept the fight.").create()));
    accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/acceptfight"));

    TextComponent spacer = new TextComponent(" ");
    TextComponent decline = new TextComponent("§c[DECLINE]");
    decline.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Click to decline the fight.").create()));
    decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/declinefight"));

    player.spigot().sendMessage(prefix, accept, spacer, decline);
  }

  private static class FightInvite {
    private final Rink rink;
    private final UUID playerOneId;
    private final UUID playerTwoId;
    private final String playerOneName;
    private final String playerTwoName;

    private FightInvite(Rink rink, Player playerOne, Player playerTwo) {
      this.rink = rink;
      this.playerOneId = playerOne.getUniqueId();
      this.playerTwoId = playerTwo.getUniqueId();
      this.playerOneName = playerOne.getName();
      this.playerTwoName = playerTwo.getName();
    }

    private UUID getOpponentId(UUID playerId) {
      return playerOneId.equals(playerId) ? playerTwoId : playerOneId;
    }

    private String getOpponentName(UUID playerId) {
      return playerOneId.equals(playerId) ? playerTwoName : playerOneName;
    }
  }
}
