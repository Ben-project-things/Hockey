package me.sammy.benhockey.game;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Class to help assist with keeping track of the goal contributions
 */
public class GoalContribution {
  private final Player scorer;
  private final List<Player> assisters;
  private final boolean ownGoal;

  public GoalContribution(Player scorer, List<Player> assisters, boolean ownGoal) {
    this.scorer = scorer;
    this.assisters = assisters;
    this.ownGoal = ownGoal;
  }

  /**
   * Gets the goal scorer of the goal.
   * @return the scorer of the goal
   */
  public Player getScorer() {
    return scorer;
  }

  /**
   * The list of the assisters based on the goal contribution.
   * @return the list of assisters
   */
  public List<Player> getAssisters() {
    return assisters;
  }

  /**
   * If the goal scored was an own goal
   * @return true if it is an own goal
   */
  public boolean isOwnGoal() {
    return ownGoal;
  }
}
