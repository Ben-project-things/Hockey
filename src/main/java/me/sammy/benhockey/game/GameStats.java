package me.sammy.benhockey.game;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Class that deals with keeping track of the stats of the game
 */
public class GameStats {
  private final UUID playerId;
  private String team;
  private final boolean goalie;
  private int goals;
  private int assists;
  private int touches;
  private int saves;

  public GameStats(UUID id, String team, boolean goalie) {
    this.playerId = id;
    this.team     = team;
    this.goalie   = goalie;
    this.goals = 0;
    this.assists = 0;
    this.touches = 0;
    this.saves = 0;
  }

  public void addGoal() {
    goals++;
  }

  public void addAssist() {
    assists++;
  }

  public void addTouch() {
    touches++;
  }

  public int getPoints() {
    return goals + assists;
  }

  public String getTeam() {
    return team;

  }
  public boolean isGoalie() {
    return goalie;
  }

  /**
   * Method to get and format a specific player's stats.
   * @param p is the player, including offline players
   * @return the formatting for the stats
   */
  public String formatForPlayer(OfflinePlayer p) {
    String name = p.getName() != null ? p.getName() : "Unknown";

    if (this.goalie) {
      return String.format("§b%s §a| §7%d Points §a| §7%d Saves §a| §7%d Assists §a| §7%d Touches",
              name, getPoints(), this.saves, this.assists, this.touches);
    } else {
      return String.format("§b%s §a| §7%d Points §a| §7%d Goals §a| §7%d Assists §a| §7%d Touches",
              name, getPoints(), this.goals, this.assists, this.touches);
    }
  }

  /**
   * Method to get and format a specific player's stats.
   * @param p is the player
   * @return the formatting for the stats
   */
  public String formatForPlayer(Player p) {
    return formatForPlayer((OfflinePlayer) p);
  }
}
