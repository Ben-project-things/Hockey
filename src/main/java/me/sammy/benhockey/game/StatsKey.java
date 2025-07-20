package me.sammy.benhockey.game;

import java.util.Objects;
import java.util.UUID;

/**
 * Method to deal with map relaying for game stats to determine what team, player, and if the
 * player is goalie to deal with duplicate stats.
 */
public final class StatsKey {
  final UUID playerId;
  final String team;
  final boolean goalie;

  public StatsKey(UUID id, String team, boolean goalie) {
    this.playerId = id;
    this.team = team;
    this.goalie = goalie;
  }

  public UUID playerId() {
    return playerId;
   }

  public String team() {
    return team;
  }

  public boolean goalie() {
    return goalie;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StatsKey other = (StatsKey) o;
    return goalie == other.goalie &&
            playerId.equals(other.playerId) &&
            team.equals(other.team);
  }

  @Override
  public int hashCode() {
    return Objects.hash(playerId, team, goalie);
  }

}
