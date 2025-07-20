package me.sammy.benhockey.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Represents the scoreboard representation for a game.
 */
public class OldScoreboard {

  private final Scoreboard scoreboard;
  private final Objective objective;
  private final Rink rink;

  public OldScoreboard(Rink rink) {
    this.rink = rink;
    this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    this.objective = scoreboard.registerNewObjective("rinkGame", "dummy", "§bBen Hockey");
    this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
  }

  public void update() {
    Game game = rink.getGame();
    if (game == null) {
      return;
    }

    for (String entry : scoreboard.getEntries()) {
      scoreboard.resetScores(entry);
    }

    objective.getScore("§cHome: §f" + game.getHomeScore()).setScore(3);
    objective.getScore("§9Away: §f" + game.getAwayScore()).setScore(2);
    objective.getScore("§ePeriod: §f" + getPeriodDisplay()).setScore(1);
    objective.getScore("§aTime: §f" + formatTime(game.getTimeLeft())).setScore(0);

    for (Player p : rink.getAllPlayers()) {
      p.setScoreboard(scoreboard);
    }
  }

  /**
   * Gets the formatted time.
   * - If ≥ 1 minute: shows m:ss
   * - If < 1 minute: shows s.ss
   * @param milliseconds total milliseconds remaining
   * @return formatted time string
   */
  private String formatTime(int milliseconds) {
    int totalSeconds = milliseconds / 1000;
    int minutes = totalSeconds / 60;
    int seconds = totalSeconds % 60;

    if (totalSeconds >= 60) {
      return String.format("%d:%02d", minutes, seconds);
    } else {
      double secondsWithMillis = milliseconds / 1000.0;
      return String.format("%.1f", secondsWithMillis);
    }
  }

  /**
   * Gets the correct OT display.
   * @return the String for the OT display
   */
  public String getPeriodDisplay() {
    int period = rink.getGame().getPeriod();
    if (period == 1 || period == 2 || period == 3) {
      return String.valueOf(period);
    } else {
      return "OT" + (period - 3);
    }
  }

  /**
   * Shows the specified player the scoreboard.
   * @param player is the player to show the scorebaord
   */
  public void show(Player player) {
    player.setScoreboard(scoreboard);
  }
}
