package me.sammy.benhockey.game;


import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Class that deals with displaying the scoreboard.
 */
public class GameScoreboard {

  private final BossBar bossBar;
  private final Rink rink;

  public GameScoreboard(Rink rink) {
    this.rink = rink;
    this.bossBar = Bukkit.createBossBar(
            "§bBen Hockey",
            BarColor.BLUE,
            BarStyle.SOLID
    );
    bossBar.setColor(BarColor.WHITE);
    bossBar.setProgress(0.0);
    this.update();
  }

  /**
   * Method that deals with dynamically updated the scoreboard.
   */
  public void update() {
    Game game = rink.getGame();
    String title;
    if (game == null) {
      title = "§c" + rink.getHomeTeamName() + ": §f0    §9" + rink.getAwayTeamName() + ": §f0    " +
              "§ePeriod: §f1    §aTime: §f5:00";

    }
    else {
      title = String.format(
              "§c" + rink.getHomeTeamName() + ": §f%d    §9" + rink.getAwayTeamName() + ": §f%d  " +
                      "  §ePeriod: §f%s    §aTime: §f%s",
              game.getHomeScore(),
              game.getAwayScore(),
              getPeriodDisplay(),
              formatTime(game.getTimeLeft())
      );
    }

    bossBar.setTitle(title);
    syncPlayers();
  }

  /**
   * Method dealing with whom to display the bossbar to.
   */
  private void syncPlayers() {
    Set<Player> currentPlayers = new HashSet<>(bossBar.getPlayers());
    Set<Player> targetPlayers = new HashSet<>(rink.getAllPlayers());

    for (Player p : targetPlayers) {
      if (!currentPlayers.contains(p)) {
        bossBar.addPlayer(p);
      }
    }

    for (Player p : currentPlayers) {
      if (!targetPlayers.contains(p)) {
        bossBar.removePlayer(p);
      }
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

  public void showToPlayer(Player p) {
    bossBar.addPlayer(p);
  }

  public void hideFromPlayer(Player p) {
    bossBar.removePlayer(p);
  }

  public void clear() {
    bossBar.removeAll();
  }
}
