package me.sammy.benhockey.events;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import me.sammy.benhockey.BenHockey;
import me.sammy.benhockey.cosmetics.CosmeticsManager;
import me.sammy.benhockey.game.GameState;
import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;

/**
 * Deals with the logic with the hockey stick.
 */
public class PlayerHockeyListener implements Listener {

  private static final int GOALIE_SLIDE_COOLDOWN_TICKS = 10;
  private static final int GOALIE_GLOVE_REGRAB_COOLDOWN_TICKS = 10;
  private static final int HIT_LEVEL_THREE_SLOWNESS_AMPLIFIER = 1;
  private static final long PLAYER_HIT_COOLDOWN_MS = 350L;
  private static final long SHIFT_LIFT_HIT_COOLDOWN_MS = 250L;
  private static final double NON_DANGLE_LEVEL_THREE_FORWARD_BOOST = 0.5;
  private static final double DANGLE_LEVEL_ONE_SLIDE_STRENGTH = 0.55;
  private static final double SHIFT_LIFT_Y_HARD_CAP = 0.8;
  private static final double SHIFT_LIFT_BASE_Y = 0.6;
  private static final double SHIFT_LIFT_SCALE_Y = 0.25;
  private static final int BOARD_BOUNCE_NO_DAMAGE_TICKS = 15;
  private static final long GOALIE_OWN_CLEARANCE_NO_REGLOVE_MS = 1200L;
  private final LobbyManager lobbyManager;
  private final HashMap<UUID, Double> charges = new HashMap<>();
  private final JavaPlugin plugin;
  private final Set<UUID> rightClickCooldown = new HashSet<>();
  private final Set<UUID> slideCooldown = new HashSet<>();
  private final Map<UUID, Integer> goalieSlideCooldownTicks = new HashMap<>();
  private final Map<UUID, Slime> goalieGloveSlime = new HashMap<>();
  private final Map<UUID, BukkitTask> gloveTimers = new HashMap<>();
  private final Set<UUID> glovedGoalies = new HashSet<>();
  private final Map<UUID, Long> recentGoalieBounceMillis = new HashMap<>();
  private final Map<UUID, UUID> recentGoalieBouncePlayer = new HashMap<>();
  private final Map<UUID, Long> goalieGloveReleaseCooldownMillis = new HashMap<>();
  private final Map<UUID, List<ArmorStand>> goaliePadStands = new HashMap<>();
  private final Map<String, ArmorStand> spectatorCamStands = new HashMap<>();
  private final Map<String, Entity> activeGoalCelebrationMounts = new HashMap<>();
  private final Set<UUID> releasedSpectatorCamera = new HashSet<>();
  private final Map<UUID, Vector> lastPuckVelocity = new HashMap<>();
  private final Set<UUID> dangleModePlayers = new HashSet<>();
  private final Map<UUID, Long> playerHitCooldownMillis = new HashMap<>();
  private final Map<UUID, Long> shiftLiftHitCooldownMillis = new HashMap<>();
  private final Map<UUID, Long> recentShotOnTargetCreditMillis = new HashMap<>();
  private final Map<UUID, Long> suppressGroundBounceUntilMillis = new HashMap<>();
  private final Map<UUID, Long> goalieOwnHitNoGloveUntilMillis = new HashMap<>();
  private final Map<UUID, UUID> goalieOwnHitNoGlovePlayer = new HashMap<>();
  private int goalTrailTick = 0;
  private static final String GLOVED_PUCK_NAME = "§bGloved Puck";
  private static final String HOCKEY_STICK_NAME = "§aHockey Stick";
  private static final String GOALIE_STICK_NAME = "§bGoalie Stick";
  private static final String POSSESSION_YES = "§aYour team has possession";
  private static final String POSSESSION_NO = "§cYour team does not have possession";
  private static final Set<Material> ICE_SURFACES = new HashSet<Material>(
          Arrays.asList(Material.ICE, Material.BLUE_ICE, Material.PACKED_ICE)
  );

  public PlayerHockeyListener(LobbyManager lobbyManager, JavaPlugin plugin) {
    this.lobbyManager = lobbyManager;
    this.plugin = plugin;
  }

  @EventHandler
  public void onPlayerLeave(PlayerQuitEvent e) {
    this.charges.remove(e.getPlayer().getUniqueId());
    this.goalieSlideCooldownTicks.remove(e.getPlayer().getUniqueId());
    this.dangleModePlayers.remove(e.getPlayer().getUniqueId());
    this.shiftLiftHitCooldownMillis.remove(e.getPlayer().getUniqueId());
    clearGoalieGloveState(e.getPlayer().getUniqueId());
    clearGoaliePads(e.getPlayer().getUniqueId());
    this.recentGoalieBouncePlayer.values().removeIf(id -> id.equals(e.getPlayer().getUniqueId()));
    this.releasedSpectatorCamera.remove(e.getPlayer().getUniqueId());
    removeGoalCelebrationMountForPlayer(e.getPlayer());
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    this.dangleModePlayers.remove(playerId);
    this.charges.remove(playerId);
    this.shiftLiftHitCooldownMillis.remove(playerId);
    player.removePotionEffect(PotionEffectType.POISON);
    player.removePotionEffect(PotionEffectType.SLOW);
    getCosmeticsManager().loadPlayer(playerId);
  }

  @EventHandler
  public void onStickDurabilityLoss(PlayerItemDamageEvent e) {
    if (isHockeyStick(e.getItem())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
    Player player = e.getPlayer();
    String message = e.getMessage().toLowerCase();
    if (message.startsWith("/join") || message.startsWith("/team") || message.startsWith("/goalie")) {
      setDangleMode(player, false);
      this.resetPower(player);
      this.releasedSpectatorCamera.remove(player.getUniqueId());
    }
  }

  @EventHandler
  public void onGoalieMove(PlayerMoveEvent e) {
    Player player = e.getPlayer();
    if (!this.lobbyManager.isPlayerAKeeper(player) || !player.isSneaking()) {
      return;
    }
    // Goalie pad armor stand visuals are disabled to prevent runaway spawning.
  }

  @EventHandler
  public void onSneak(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (p.getGameMode() == GameMode.SPECTATOR && !this.lobbyManager.isPlayerInLobby(p)) {
      p.setSpectatorTarget(null);
      this.releasedSpectatorCamera.add(p.getUniqueId());
    }

    if (lobbyManager.isPlayerInLobby(p)) {
      return;
    }

    if (e.isSneaking() && lobbyManager.isPlayerOnTeam(p)) {
      charges.put(p.getUniqueId(), 0.0);
    } else {
      p.setExp(0.0f);
      charges.remove(p.getUniqueId());
    }

    if (lobbyManager.isPlayerAKeeper(p) && e.isSneaking()) {
      // Intentionally disabled to prevent repeated armor stand spawning.
    }
    removeGoalCelebrationMountForPlayer(p);
  }

  @EventHandler
  public void onGoalieSlide(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    UUID uuid = p.getUniqueId();

    if (!lobbyManager.isPlayerAKeeper(p)) {
      return;
    }

    int newSlot = e.getNewSlot();
    boolean attemptedSlide = newSlot == 1 || newSlot == 2;
    if (!attemptedSlide) {
      return;
    }

    e.setCancelled(true);

    if (this.slideCooldown.contains(uuid)) {
      p.getInventory().setHeldItemSlot(0);
      return;
    }

    if (newSlot == 1) {
      slidePlayer(p, "Left");
    }

    if (newSlot == 2) {
      slidePlayer(p, "Right");
    }

    p.getInventory().setHeldItemSlot(0);
    this.slideCooldown.add(uuid);
    this.goalieSlideCooldownTicks.put(uuid, GOALIE_SLIDE_COOLDOWN_TICKS);
    Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> slideCooldown.remove(uuid),
            GOALIE_SLIDE_COOLDOWN_TICKS
    );
  }

  /**
   * Helper method to check which direction to slide for keepers.
   * @param p is the player to slide
   * @param direction is the direction to slide them
   */
  private void slidePlayer(Player p, String direction) {
    Location loc = p.getLocation();
    float yaw = loc.getYaw();

    double radians = Math.toRadians(yaw);
    Vector slide = new Vector(Math.cos(radians), 0, Math.sin(radians));

    if (Objects.equals(direction, "Right")) {
      slide.multiply(-1);
    }

    slide.multiply(0.5);
    p.setVelocity(slide);
  }

  @EventHandler
  public void onPlayerWhistle(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player player = e.getPlayer();
    Rink playerRink = lobbyManager.getPlayerRink(player);

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    ItemStack interactItem = player.getInventory().getItemInMainHand();
    if (interactItem.getType() == Material.IRON_NUGGET &&
            interactItem.hasItemMeta() &&
            "§6Ref Whistle".equals(interactItem.getItemMeta().getDisplayName())) {
      playerRink.blowWhistle(player);
    }
  }

  @EventHandler
  public void onPlayerStickInteract(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player player = e.getPlayer();
    UUID uuid = player.getUniqueId();

    if (rightClickCooldown.contains(uuid)) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    ItemStack interactItem = player.getInventory().getItemInMainHand();
    if (player.isSneaking()
            && action == Action.RIGHT_CLICK_BLOCK
            && e.getClickedBlock() != null
            && e.getClickedBlock().getType() == Material.BARRIER
            && isHockeyStick(interactItem)
            && handleBenchBarrierInteract(player, e.getClickedBlock().getLocation())) {
      e.setCancelled(true);
      return;
    }

    if (isHockeyStick(interactItem)) {

      this.rightClickCooldown.add(uuid);
      Bukkit.getScheduler().runTaskLater(plugin, () -> rightClickCooldown.remove(uuid), 1L);

      int currentLevel = player.getLevel();
      int newLevel = (currentLevel >= 3) ? 1 : currentLevel + 1;
      player.setLevel(newLevel);
      updateHitLevelEffects(player, newLevel);

      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);

      ItemMeta meta = interactItem.getItemMeta();
      if (meta == null) {
        return;
      }
      meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

      double attackSpeed = (newLevel == 3) ? -0.5 : 2.5;
      if (this.dangleModePlayers.contains(uuid) && newLevel == 1) {
        attackSpeed = 10.0;
      }
      AttributeModifier attackSpeedModifier = new AttributeModifier(
              UUID.randomUUID(),
              "generic_attack_speed",
              attackSpeed,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlot.HAND
      );
      meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);
      meta.removeEnchant(Enchantment.KNOCKBACK);
      meta.addEnchant(Enchantment.KNOCKBACK, Math.min(newLevel, 3), true);
      interactItem.setItemMeta(meta);
    }
  }

  /**
   * Gets a safe drop location for the puck to ensure that it doesn't go into the ground or boards.
   * @param goalie is the goalie that gloved
   * @return a safe location
   */
  private Location getSafeDropLocation(Player goalie) {
    Location eye = goalie.getEyeLocation();
    Vector forward = eye.getDirection().setY(0).normalize();
    if (forward.lengthSquared() < 0.0001) {
      forward = goalie.getLocation().getDirection().setY(0).normalize();
    }

    Location base = goalie.getLocation().clone().add(forward.multiply(1.2));
    World world = base.getWorld();

    for (int yOffset = 1; yOffset >= -2; yOffset--) {
      Location candidate = base.clone().add(0, yOffset, 0);
      Block feet = candidate.getBlock();
      Block head = candidate.clone().add(0, 1, 0).getBlock();
      Block below = candidate.clone().subtract(0, 1, 0).getBlock();

      if (feet.isPassable() && head.isPassable() && isIceSurface(below.getType())) {
        return candidate.add(0.5, 0.02, 0.5);
      }
    }

    Location nearestIce = findNearestIceDrop(goalie, 22);
    if (nearestIce != null) {
      return nearestIce;
    }

    return world.getHighestBlockAt(base).getLocation().add(0.5, 1.02, 0.5);
  }


  @EventHandler
  public void onDropHockeyStick(PlayerDropItemEvent e) {
    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    ItemStack droppedItem = e.getItemDrop().getItemStack();

    if (lobbyManager.isPlayerAKeeper(player)
            && this.glovedGoalies.contains(playerId)
            && ((droppedItem.getType() == Material.SLIME_BALL
            && droppedItem.hasItemMeta()
            && droppedItem.getItemMeta() != null
            && GLOVED_PUCK_NAME.equals(droppedItem.getItemMeta().getDisplayName()))
            || isHockeyStick(droppedItem))) {
      e.setCancelled(true);
      e.getItemDrop().remove();
      dropGlovedPuck(player);
      return;
    }

    if (!isHockeyStick(droppedItem)) {
      return;
    }

    e.setCancelled(true);
    e.getItemDrop().remove();

    if (lobbyManager.isPlayerAKeeper(player)) {
      return;
    }

    Rink rink = this.lobbyManager.getPlayerRink(player);
    if (rink == null || !this.lobbyManager.isPlayerOnTeam(player)) {
      return;
    }

    if (rink.getGameState() == GameState.GAME && !rink.hasPossession(player)) {
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
      player.sendMessage("§6[§bBH§6] §cYou can only dangle when your team has possession.");
      return;
    }

    setDangleMode(player, !this.dangleModePlayers.contains(playerId));
  }

  /**
   * Deals with the logic behind a player right clicking on a slime and pulling it back
   * @param e is the player interacting with the entity event.

  @EventHandler
  public void onRightClick(PlayerInteractEntityEvent e) {
    Entity entity = e.getRightClicked();
    Player player = e.getPlayer();
    //TODO Maybe some cool interaction here

    if (!(entity instanceof Slime)) {
      return;
    }

    Slime puck = (Slime) entity;
  }
  */

  @EventHandler
  public void onHitSlime(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof Slime)) {
      return;
    }

    Slime slime = (Slime) e.getEntity();

    Player player = (Player) e.getDamager();
    ItemStack item = player.getInventory().getItemInMainHand();
    ItemMeta meta = isHockeyStick(item) ? item.getItemMeta() : null;

    if (meta == null || !meta.hasDisplayName() || !isHockeyStickName(meta.getDisplayName())) {
      return;
    }

    long now = System.currentTimeMillis();
    double speed = slime.getVelocity().length();
    long gloveCooldownEnd = this.goalieGloveReleaseCooldownMillis.getOrDefault(player.getUniqueId(), 0L);
    if (lobbyManager.isPlayerAKeeper(player)
            && speed > 0.3
            && now >= gloveCooldownEnd
            && canGoalieGlovePuck(player, slime, now)) {
      handleGoalieGlove(player, slime);
      this.lobbyManager.getPlayerRink(player).addPlayerLastHit(player);
      e.setCancelled(true);
      return;
    }

    Rink playerRink = this.lobbyManager.getPlayerRink(player);
    if (playerRink == null || !this.lobbyManager.isPlayerOnTeam(player)) {
      return;
    }

    boolean firstFaceoffTouch = playerRink.consumeFaceoffFirstTouch();
    boolean dangleMode = this.dangleModePlayers.contains(player.getUniqueId());
    int hitLevel = Math.max(1, Math.min(3, player.getLevel()));

    playerRink.addPlayerLastHit(player);
    Bukkit.getScheduler().runTaskLater(this.plugin, () -> registerShotOnTargetIfNeeded(player, slime), 1L);

    double charge = getShiftCharge(player);
    boolean goalie = this.lobbyManager.isPlayerAKeeper(player);
    boolean wantsShiftLift = player.isSneaking()
            && charge > 0.02
            && (!goalie || hitLevel >= 3);
    long shiftLiftCooldownEnd = this.shiftLiftHitCooldownMillis.getOrDefault(player.getUniqueId(), 0L);
    if (player.isSneaking() && now < shiftLiftCooldownEnd) {
      e.setCancelled(true);
      return;
    }
    boolean shiftLift = wantsShiftLift;
    if (!wantsShiftLift || shiftLift) {
      this.resetPower(player);
    }

    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
      if (slime.isDead() || !slime.isValid()) {
        return;
      }
      if (goalie) {
        this.goalieOwnHitNoGloveUntilMillis.put(slime.getUniqueId(), nowMillis() + GOALIE_OWN_CLEARANCE_NO_REGLOVE_MS);
        this.goalieOwnHitNoGlovePlayer.put(slime.getUniqueId(), player.getUniqueId());
      }

      if (dangleMode) {
        slime.playEffect(EntityEffect.HURT);
        if (hitLevel <= 2) {
          slime.setNoDamageTicks(0);
        }
      }

      Vector updatedVelocity = slime.getVelocity();
      Vector forward = player.getLocation().getDirection().setY(0);
      if (forward.lengthSquared() < 0.0001) {
        forward = new Vector(0, 0, 1);
      }
      forward.normalize();
      Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

      if (firstFaceoffTouch && hitLevel >= 2) {
        double faceoffPullDistance = hitLevel >= 3 ? 2.1 : 1.0;
        Vector faceoffPull = forward.clone().multiply(-faceoffPullDistance);
        faceoffPull.setY(0.0);
        slime.setVelocity(faceoffPull);
        slime.playEffect(EntityEffect.HURT);
        registerShotOnTargetIfNeeded(player, slime);
        return;
      }

      if (!dangleMode) {
        Vector existingVelocity = slime.getVelocity().clone();
        Vector flatForward = forward.clone().setY(0).normalize();
        Vector boosted = existingVelocity.clone();
        if (hitLevel == 3) {
          boosted.add(flatForward.multiply(NON_DANGLE_LEVEL_THREE_FORWARD_BOOST));
        }

        double basePop = 0.07 + (hitLevel * 0.03);
        if (shiftLift) {
          boosted.setY(getPuckLiftY(slime, existingVelocity.getY(), basePop, getShiftLift(charge)));
          boosted.setY(Math.min(boosted.getY(), SHIFT_LIFT_Y_HARD_CAP));
        } else if (hitLevel >= 2) {
          boosted.setY(Math.max(existingVelocity.getY(), basePop));
        }

        slime.setVelocity(boosted);
        return;
      }

      if (hitLevel == 1) {
        Vector toPuck = slime.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (toPuck.lengthSquared() < 0.0001) {
          toPuck = forward.clone();
        } else {
          toPuck.normalize();
        }
        double sideDot = toPuck.dot(right);
        Vector sideShuffle = sideDot < 0 ? right.clone() : right.clone().multiply(-1);
        sideShuffle.multiply(DANGLE_LEVEL_ONE_SLIDE_STRENGTH);
        if (shiftLift) {
          sideShuffle.setY(getPuckLiftY(slime, updatedVelocity.getY(), 0.08, getShiftLift(charge)));
        } else {
          sideShuffle.setY(0.0);
        }
        slime.setVelocity(sideShuffle);
        player.setVelocity(sideShuffle.clone().setY(0.0));
        if (shiftLift) {
          this.shiftLiftHitCooldownMillis.put(player.getUniqueId(), nowMillis() + SHIFT_LIFT_HIT_COOLDOWN_MS);
        }
        return;
      }

      if (hitLevel == 2) {
        Vector pull = updatedVelocity.clone().setY(0).multiply(-0.18);
        Vector backward = forward.clone().multiply(-0.42);
        pull.add(backward);
        if (pull.lengthSquared() < 0.06) {
          pull = backward;
        }
        if (shiftLift) {
          pull.setY(getPuckLiftY(slime, updatedVelocity.getY(), 0.10, getShiftLift(charge)));
          pull.setY(Math.min(pull.getY(), SHIFT_LIFT_Y_HARD_CAP));
          this.shiftLiftHitCooldownMillis.put(player.getUniqueId(), nowMillis() + SHIFT_LIFT_HIT_COOLDOWN_MS);
        } else {
          pull.setY(0.0);
        }
        slime.setVelocity(pull);
        return;
      }

      Vector existingVelocity = updatedVelocity.clone();
      Vector horizontalMomentum = existingVelocity.clone().setY(0);
      Vector shotDirection = forward.clone();
      if (horizontalMomentum.lengthSquared() > 0.0001) {
        Vector momentumDirection = horizontalMomentum.clone().normalize();
        if (momentumDirection.dot(forward) >= 0) {
          shotDirection = momentumDirection;
        }
      }

      Vector boosted = horizontalMomentum.clone().add(shotDirection.multiply(1.15));
      double basePop = 0.07 + (hitLevel * 0.03);
      if (shiftLift) {
        boosted.setY(getPuckLiftY(slime, existingVelocity.getY(), basePop, getShiftLift(charge)));
        boosted.setY(Math.min(boosted.getY(), SHIFT_LIFT_Y_HARD_CAP));
      } else {
        boosted.setY(Math.max(existingVelocity.getY(), basePop));
      }
      slime.setVelocity(boosted);
      if (shiftLift) {
        this.shiftLiftHitCooldownMillis.put(player.getUniqueId(), nowMillis() + SHIFT_LIFT_HIT_COOLDOWN_MS);
      }
    }, 1L);
  }

  private double getShiftCharge(Player player) {
    double charged = this.charges.getOrDefault(player.getUniqueId(), 0.0);
    return Math.max(charged, player.getExp());
  }

  private double getShiftLift(double charge) {
    double clampedCharge = Math.max(0.0, Math.min(1.0, charge));
    double eased = Math.pow(clampedCharge, 0.65);
    return SHIFT_LIFT_BASE_Y + (eased * SHIFT_LIFT_SCALE_Y);
  }

  private double getPuckLiftY(Slime slime, double currentY, double basePop, double requestedLift) {
    if (slime.isOnGround()) {
      return Math.max(Math.max(currentY, basePop), requestedLift);
    }

    double target = Math.max(basePop, requestedLift);
    double gap = Math.max(0.0, target - currentY);
    double diminishingIncrease = Math.min(0.13, (gap * 0.35) + 0.02);
    return Math.min(SHIFT_LIFT_Y_HARD_CAP, Math.max(currentY, basePop) + diminishingIncrease);
  }


  /**
   * Method to deal with the handling of when a goalie gloves the puck.
   * @param goalie is the goalie that gloved the puck
   * @param puck is the specific slime entity
   */
  private void handleGoalieGlove(Player goalie, Slime puck) {
    UUID goalieId = goalie.getUniqueId();
    clearGoalieGloveState(goalieId);

    Location puckLoc = puck.getLocation();
    this.goalieGloveSlime.put(goalieId, puck);
    this.glovedGoalies.add(goalieId);
    puck.setVelocity(new Vector(0, 0, 0));
    Location stash = puck.getLocation().clone().add(0, -25, 0);
    puck.teleport(stash);

    puckLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, puckLoc, 10, 0.2, 0.2, 0.2, 0.01);
    updateGoalieGloveIndicator(goalie, true);

    BukkitTask task = new BukkitRunnable() {
      int ticksLeft = 60;

      @Override
      public void run() {
        if (!goalie.isOnline()) {
          clearGoalieGloveState(goalieId);
          this.cancel();
          return;
        }

        if (ticksLeft <= 0) {
          if (glovedGoalies.contains(goalieId)) {
            dropGlovedPuck(goalie);
            this.cancel();
            return;
          }
        }

        double seconds = Math.max(0.0, ticksLeft / 20.0);
        goalie.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText("§bPuck glove: §f" + String.format("%.1f", seconds) + "s"));
        if (ticksLeft % 20 == 0) {
          goalie.playSound(goalie.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 20f, 1f);
        }
        ticksLeft -= 2;
      }
    }.runTaskTimer(plugin, 0L, 2L);

    this.gloveTimers.put(goalieId, task);
  }

  private void dropGlovedPuck(Player goalie) {
    UUID id = goalie.getUniqueId();
    Slime puck = goalieGloveSlime.remove(id);
    BukkitTask t = gloveTimers.remove(id);

    if (t != null) {
      t.cancel();
    }

    if (puck == null) {
      return;
    }

    Location drop = getSafeDropLocation(goalie);

    puck.teleport(drop);
    puck.setRotation(0f, 0f);
    glovedGoalies.remove(id);
    updateGoalieGloveIndicator(goalie, false);
    goalie.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));

    puck.setVelocity(new Vector(0, 0.02, 0));
    this.suppressGroundBounceUntilMillis.put(puck.getUniqueId(), nowMillis() + 1200L);
    puck.setNoDamageTicks(0);
    this.goalieGloveReleaseCooldownMillis.put(id, nowMillis() + (GOALIE_GLOVE_REGRAB_COOLDOWN_TICKS * 50L));
    this.goalieOwnHitNoGloveUntilMillis.put(puck.getUniqueId(), nowMillis() + GOALIE_OWN_CLEARANCE_NO_REGLOVE_MS);
    this.goalieOwnHitNoGlovePlayer.put(puck.getUniqueId(), id);
  }

  private void clearGoalieGloveState(UUID goalieId) {
    BukkitTask existingTimer = gloveTimers.remove(goalieId);
    if (existingTimer != null) {
      existingTimer.cancel();
    }

    this.glovedGoalies.remove(goalieId);
    this.goalieGloveSlime.remove(goalieId);
    Player goalie = Bukkit.getPlayer(goalieId);
    if (goalie != null) {
      updateGoalieGloveIndicator(goalie, false);
      goalie.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
    }
  }

  private void updateGoalieGloveIndicator(Player goalie, boolean gloved) {
    int slot = 8;
    if (!gloved) {
      ItemStack item = goalie.getInventory().getItem(slot);
      if (item != null && item.getType() == Material.SLIME_BALL
              && item.hasItemMeta()
              && GLOVED_PUCK_NAME.equals(item.getItemMeta().getDisplayName())) {
        goalie.getInventory().clear(slot);
      }
      return;
    }

    ItemStack glovedItem = new ItemStack(Material.SLIME_BALL);
    ItemMeta meta = glovedItem.getItemMeta();
    meta.setDisplayName(GLOVED_PUCK_NAME);
    glovedItem.setItemMeta(meta);
    goalie.getInventory().setItem(slot, glovedItem);
  }

  @EventHandler
  public void onPuckDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Slime)) {
      return;
    }

    if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
      Slime slime = (Slime) e.getEntity();
      long suppressUntil = this.suppressGroundBounceUntilMillis.getOrDefault(slime.getUniqueId(), 0L);
      if (nowMillis() < suppressUntil) {
        e.setCancelled(true);
        return;
      }
      if (e.getDamage() > 0.0) {
        applyGroundBounce(slime, e.getDamage());
      }
      e.setCancelled(true);
      return;
    }

    if (e.getCause() == EntityDamageEvent.DamageCause.LAVA ||
            e.getCause() == EntityDamageEvent.DamageCause.FIRE ||
            e.getCause() == EntityDamageEvent.DamageCause.VOID) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onDanglePoisonDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player)) {
      return;
    }

    if (e.getCause() != EntityDamageEvent.DamageCause.POISON) {
      return;
    }

    Player player = (Player) e.getEntity();
    if (this.dangleModePlayers.contains(player.getUniqueId())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
    Player player = e.getPlayer();
    ItemStack item = player.getInventory().getItemInMainHand();

    if (player.getGameMode() == GameMode.ADVENTURE || isHockeyStick(item)) {
      e.setCancelled(true);
    }

    if (lobbyManager.isPlayerAKeeper(player) && isHockeyStick(item)) {
      player.getInventory().setItemInOffHand(null);
      Vector current = player.getVelocity();
      player.setVelocity(new Vector(0, Math.min(current.getY(), 0.05), 0));
      return;
    }

    if (isHockeyStick(item)) {
      setDangleMode(player, false);

      player.setLevel(1);
      updateHitLevelEffects(player, 1);

      ItemMeta meta = item.getItemMeta();
      meta.removeEnchant(Enchantment.KNOCKBACK);
      meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

      AttributeModifier attackSpeedModifier = new AttributeModifier(
              UUID.randomUUID(),
              "generic_attack_speed",
              2.5,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlot.HAND
      );
      meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);
      meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
      item.setItemMeta(meta);

      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
      player.getInventory().setItemInMainHand(item);
    }
  }

  /**
   * Runs the updates for when a player is shifting and increases their xp bar.
   */
  public void update() {
    updatePossessionIndicators();

    for (UUID uuid : this.charges.keySet()) {
      Player p = this.plugin.getServer().getPlayer(uuid);
      if (p == null) {
        continue;
      }

      double charge = this.charges.get(uuid);
      if (this.lobbyManager.isPlayerAKeeper(p) && p.getLevel() < 3) {
        this.charges.put(uuid, 0.0);
        p.setExp(0.0f);
        continue;
      }
      double chargeRate = 0.15 - (charge * 0.08);
      double nextCharge = charge + ((1.0 - charge) * chargeRate);
      nextCharge = Math.min(nextCharge, 0.99);

      this.charges.put(uuid, nextCharge);
      p.setExp((float) nextCharge);
    }

    for (UUID uuid : new HashSet<>(this.goalieSlideCooldownTicks.keySet())) {
      Player goalie = this.plugin.getServer().getPlayer(uuid);
      Integer ticksLeft = this.goalieSlideCooldownTicks.get(uuid);
      if (goalie == null || ticksLeft == null || !goalie.isOnline() || !this.lobbyManager.isPlayerAKeeper(goalie)) {
        this.goalieSlideCooldownTicks.remove(uuid);
        continue;
      }

      if (ticksLeft <= 0) {
        goalie.setExp(0.0f);
        this.goalieSlideCooldownTicks.remove(uuid);
        continue;
      }

      goalie.setExp(Math.min(1.0f, ticksLeft / (float) GOALIE_SLIDE_COOLDOWN_TICKS));
      this.goalieSlideCooldownTicks.put(uuid, ticksLeft - 1);
    }

    Set<UUID> livePucks = new HashSet<>();
    for (World world : Bukkit.getWorlds()) {
      for (Slime slime : world.getEntitiesByClass(Slime.class)) {
        UUID slimeId = slime.getUniqueId();
        livePucks.add(slimeId);
        for (Player viewer : world.getPlayers()) {
          getCosmeticsManager().spawnParticleFor(viewer, slime.getLocation());
        }
        slime.setRotation(0f, 0f);
        Vector previousVelocity = this.lastPuckVelocity.getOrDefault(
                slimeId,
                slime.getVelocity().clone()
        );
        applyBoardBounce(slime, previousVelocity);
        applyGoalieBodyBounce(slime);
        this.lastPuckVelocity.put(slimeId, slime.getVelocity().clone());
      }
    }
    this.lastPuckVelocity.keySet().retainAll(livePucks);
    this.recentGoalieBounceMillis.keySet().retainAll(livePucks);
    this.recentGoalieBouncePlayer.keySet().retainAll(livePucks);
    this.recentShotOnTargetCreditMillis.keySet().retainAll(livePucks);
    this.suppressGroundBounceUntilMillis.keySet().retainAll(livePucks);
    this.goalieOwnHitNoGloveUntilMillis.keySet().retainAll(livePucks);
    this.goalieOwnHitNoGlovePlayer.keySet().retainAll(livePucks);
    this.goalieGloveReleaseCooldownMillis.entrySet().removeIf(entry -> nowMillis() > entry.getValue() + 5000L);
    this.playerHitCooldownMillis.entrySet().removeIf(entry -> nowMillis() > entry.getValue() + 1000L);
    this.shiftLiftHitCooldownMillis.entrySet().removeIf(entry -> nowMillis() > entry.getValue());

    for (UUID uuid : new HashSet<>(this.glovedGoalies)) {
      Player goalie = this.plugin.getServer().getPlayer(uuid);
      if (goalie == null || !goalie.isOnline()) {
        clearGoalieGloveState(uuid);
        continue;
      }

      if (this.lobbyManager.isPlayerInLobby(goalie) || this.lobbyManager.getPlayerRink(goalie) == null) {
        clearGoalieGloveState(uuid);
        continue;
      }

      Rink rink = this.lobbyManager.getPlayerRink(goalie);
      if (rink.getGameState() != GameState.GAME || rink.getGame() == null) {
        continue;
      }

      Location goalieBlock = goalie.getLocation().getBlock().getLocation();
      if (rink.getHomeGoalZone().contains(goalieBlock)) {
        clearGoalieGloveState(uuid);
        rink.forceGoal("away");
      }
      else if (rink.getAwayGoalZone().contains(goalieBlock)) {
        clearGoalieGloveState(uuid);
        rink.forceGoal("home");
      }
    }

    enforceGoalieHalfLineRule();

    updateGoalCelebrationsAndTrails();
    updateSpectatorCameras();
  }

  private void updateGoalCelebrationsAndTrails() {
    Set<String> activeRinks = new HashSet<>();
    for (Rink rink : this.lobbyManager.getRinks()) {
      String rinkKey = rink.getName().toLowerCase();
      activeRinks.add(rinkKey);
      if (rink.getGameState() != GameState.GAME || rink.getGame() == null) {
        clearGoalCelebrationMount(rinkKey);
        continue;
      }

      int intermissionLeft = rink.getGame().getIntermissionTimeLeft();
      String intermissionLabel = rink.getGame().getIntermissionLabel();
      if (intermissionLeft <= 0 || !"Faceoff".equalsIgnoreCase(intermissionLabel)) {
        clearGoalCelebrationMount(rinkKey);
        continue;
      }

      Player scorer = rink.getGame().getMostRecentGoalScorer();
      if (scorer == null || !scorer.isOnline()) {
        clearGoalCelebrationMount(rinkKey);
        continue;
      }

      spawnGoalTrailForScorer(rink, scorer);
      ensureGoalCelebrationMount(rinkKey, scorer);
      driveGoalCelebrationMount(scorer, this.activeGoalCelebrationMounts.get(rinkKey));
    }

    for (String rinkName : new HashSet<>(this.activeGoalCelebrationMounts.keySet())) {
      if (!activeRinks.contains(rinkName)) {
        clearGoalCelebrationMount(rinkName);
      }
    }
  }

  private void spawnGoalTrailForScorer(Rink rink, Player scorer) {
    CosmeticsManager.GoalTrailType trail = getCosmeticsManager().getGoalTrailType(scorer.getUniqueId());
    Location base = scorer.getLocation().clone().add(0, 0.9, 0);
    World world = base.getWorld();
    if (world == null) {
      return;
    }
    CosmeticsManager.TrailParticle selected = trail.particle;
    if (selected == CosmeticsManager.TrailParticle.NOTE) {
      double noteColor = (this.goalTrailTick % 24) / 24.0;
      this.goalTrailTick++;
      world.spawnParticle(Particle.NOTE, base, 0, noteColor, 0, 0, 1.0);
      return;
    }
    if (selected == CosmeticsManager.TrailParticle.RAINBOW) {
      Particle.DustOptions[] rainbowDust = {
          new Particle.DustOptions(org.bukkit.Color.RED, 1.25f),
          new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.25f),
          new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.25f),
          new Particle.DustOptions(org.bukkit.Color.LIME, 1.25f),
          new Particle.DustOptions(org.bukkit.Color.AQUA, 1.25f),
          new Particle.DustOptions(org.bukkit.Color.BLUE, 1.25f),
          new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.25f)
      };
      Particle.DustOptions dust = rainbowDust[(this.goalTrailTick / 2) % rainbowDust.length];
      this.goalTrailTick++;
      world.spawnParticle(Particle.REDSTONE, base, 7, 0.2, 0.22, 0.2, 0.01, dust);
      return;
    }
    if (selected.dustOptions != null) {
      world.spawnParticle(selected.particle, base, selected.count, selected.offsetX, selected.offsetY,
              selected.offsetZ, selected.speed, selected.dustOptions);
      return;
    }
    if (selected.dataMaterial != null) {
      world.spawnParticle(selected.particle, base, selected.count, selected.offsetX, selected.offsetY,
              selected.offsetZ, selected.speed, Bukkit.createBlockData(selected.dataMaterial));
      return;
    }
    world.spawnParticle(selected.particle, base, selected.count, selected.offsetX, selected.offsetY,
            selected.offsetZ, selected.speed);
  }

  private void ensureGoalCelebrationMount(String rinkKey, Player scorer) {
    Entity existing = this.activeGoalCelebrationMounts.get(rinkKey);
    if (existing != null && existing.isValid()) {
      if (!existing.getPassengers().contains(scorer)) {
        existing.addPassenger(scorer);
      }
      return;
    }

    Entity mount = spawnCelebrationMount(scorer);
    if (mount == null) {
      return;
    }
    this.activeGoalCelebrationMounts.put(rinkKey, mount);
    mount.addPassenger(scorer);
  }

  private Entity spawnCelebrationMount(Player scorer) {
    Location spawnLoc = scorer.getLocation().clone();
    World world = spawnLoc.getWorld();
    if (world == null) {
      return null;
    }

    CosmeticsManager.GoalCelebrationOption type = getCosmeticsManager().getGoalCelebrationType(scorer.getUniqueId());
    Entity mount = world.spawnEntity(spawnLoc, type.entityType);
    getCosmeticsManager().configureCelebrationMount(mount);
    return mount;
  }

  private void driveGoalCelebrationMount(Player scorer, Entity mount) {
    if (mount == null || !mount.isValid() || scorer == null || !scorer.isOnline()) {
      return;
    }
    Location look = scorer.getLocation();
    Vector direction = look.getDirection().setY(0);
    if (direction.lengthSquared() < 0.0001) {
      return;
    }
    direction.normalize().multiply(0.55);
    direction.setY(-0.08);
    mount.setVelocity(direction);
  }

  private void clearGoalCelebrationMount(String rinkKey) {
    Entity mount = this.activeGoalCelebrationMounts.remove(rinkKey);
    if (mount == null || !mount.isValid()) {
      return;
    }
    mount.eject();
    mount.remove();
  }

  private void removeGoalCelebrationMountForPlayer(Player player) {
    for (String rinkKey : new HashSet<>(this.activeGoalCelebrationMounts.keySet())) {
      Entity mount = this.activeGoalCelebrationMounts.get(rinkKey);
      if (mount == null || !mount.isValid()) {
        this.activeGoalCelebrationMounts.remove(rinkKey);
        continue;
      }
      if (!mount.getPassengers().contains(player)) {
        continue;
      }
      mount.removePassenger(player);
      clearGoalCelebrationMount(rinkKey);
    }
  }

  private void updateSpectatorCameras() {
    Set<String> activeRinks = new HashSet<>();
    for (Rink rink : this.lobbyManager.getRinks()) {
      activeRinks.add(rink.getName().toLowerCase());
      updateSpectatorCameraForRink(rink);
    }

    for (String rinkName : new HashSet<>(this.spectatorCamStands.keySet())) {
      if (activeRinks.contains(rinkName)) {
        continue;
      }
      ArmorStand stand = this.spectatorCamStands.remove(rinkName);
      if (stand != null && stand.isValid()) {
        stand.remove();
      }
    }
  }

  private void updateSpectatorCameraForRink(Rink rink) {
    ArmorStand camera = ensureSpectatorCamera(rink);
    if (camera == null || !camera.isValid()) {
      return;
    }

    Location cameraBase = getSpectatorCameraBaseLocation(rink);
    Location focusTarget = getSpectatorFocusLocation(rink);
    setYawPitchToward(cameraBase, focusTarget);
    camera.teleport(cameraBase);

    for (Player player : rink.getAllPlayers()) {
      if (player.getGameMode() == GameMode.SPECTATOR) {
        if (!this.releasedSpectatorCamera.contains(player.getUniqueId())) {
          player.setSpectatorTarget(camera);
        }
      } else {
        this.releasedSpectatorCamera.remove(player.getUniqueId());
      }
    }
  }

  private ArmorStand ensureSpectatorCamera(Rink rink) {
    String key = rink.getName().toLowerCase();
    ArmorStand existing = this.spectatorCamStands.get(key);
    if (existing != null && existing.isValid()) {
      return existing;
    }

    Location spawn = getSpectatorCameraBaseLocation(rink);
    if (spawn.getWorld() == null) {
      return null;
    }
    ArmorStand stand = spawn.getWorld().spawn(spawn, ArmorStand.class, armorStand -> {
      armorStand.setInvisible(true);
      armorStand.setGravity(false);
      armorStand.setMarker(true);
      armorStand.setSmall(true);
      armorStand.setBasePlate(false);
      armorStand.setArms(false);
      armorStand.setSilent(true);
      armorStand.setInvulnerable(true);
      armorStand.setCollidable(false);
      armorStand.setPersistent(true);
    });
    this.spectatorCamStands.put(key, stand);
    return stand;
  }

  private Location getSpectatorCameraBaseLocation(Rink rink) {
    Location center = rink.getCenterIce().clone();
    Location homeBench = rink.getHomeBench();
    Location awayBench = rink.getAwayBench();
    Vector towardBench = homeBench.toVector().add(awayBench.toVector()).multiply(0.5).subtract(center.toVector()).setY(0);
    if (towardBench.lengthSquared() < 0.0001) {
      towardBench = rink.getHomeGoalCenter().toVector().subtract(rink.getAwayGoalCenter().toVector()).setY(0);
    }
    if (towardBench.lengthSquared() < 0.0001) {
      towardBench = new Vector(1, 0, 0);
    }
    towardBench.normalize();
    return center.add(towardBench.multiply(16)).add(0, 9, 0);
  }

  private Location getSpectatorFocusLocation(Rink rink) {
    Location centerFocus = rink.getCenterIce().clone().add(0, 1.1, 0);
    if (rink.getGameState() != GameState.GAME || rink.getGame() == null) {
      return centerFocus;
    }

    if (rink.getGame().isFaceoffCountdownActive()) {
      rink.clearSpectatorFocusPlayer();
      return centerFocus;
    }

    int intermissionLeft = rink.getGame().getIntermissionTimeLeft();
    String intermissionLabel = rink.getGame().getIntermissionLabel();
    if (intermissionLeft > 0) {
      if ("Faceoff".equalsIgnoreCase(intermissionLabel)) {
        Player scorer = rink.getGame().getMostRecentGoalScorer();
        if (scorer != null && scorer.isOnline()) {
          return scorer.getEyeLocation();
        }
      }
      return centerFocus;
    }

    Player manualFocus = rink.getSpectatorFocusPlayer();
    if (manualFocus != null && manualFocus.isOnline()) {
      return manualFocus.getEyeLocation();
    }

    for (UUID goalieId : this.glovedGoalies) {
      Player goalie = Bukkit.getPlayer(goalieId);
      if (goalie != null && goalie.isOnline() && rink.equals(this.lobbyManager.getPlayerRink(goalie))) {
        return goalie.getEyeLocation();
      }
    }

    Entity puck = rink.getGame().getActivePuck();
    if (puck != null && puck.isValid()) {
      return puck.getLocation().clone().add(0, 0.3, 0);
    }

    return centerFocus;
  }

  private void setYawPitchToward(Location origin, Location target) {
    if (origin.getWorld() == null || target == null) {
      return;
    }
    Vector direction = target.toVector().subtract(origin.toVector());
    if (direction.lengthSquared() < 0.0001) {
      return;
    }
    origin.setDirection(direction.normalize());
  }

  private void updatePossessionIndicators() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (this.lobbyManager.isPlayerInLobby(player)) {
        continue;
      }

      Rink rink = this.lobbyManager.getPlayerRink(player);
      if (rink == null || !this.lobbyManager.isPlayerOnTeam(player)) {
        continue;
      }

      boolean hasPossession = rink.hasPossession(player);
      if (rink.getGameState() == GameState.GAME) {
        ItemStack indicator = new ItemStack(hasPossession ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta meta = indicator.getItemMeta();
        if (meta != null) {
          meta.setDisplayName(hasPossession ? POSSESSION_YES : POSSESSION_NO);
          indicator.setItemMeta(meta);
        }
        player.getInventory().setItem(4, indicator);
      } else {
        ItemStack slotItem = player.getInventory().getItem(4);
        if (slotItem != null && (slotItem.getType() == Material.LIME_DYE || slotItem.getType() == Material.RED_DYE)) {
          player.getInventory().clear(4);
        }
      }

      if (rink.getGameState() == GameState.GAME
              && this.dangleModePlayers.contains(player.getUniqueId())
              && !hasPossession) {
        setDangleMode(player, false);
      }
    }
  }

  private void setDangleMode(Player player, boolean enabled) {
    UUID playerId = player.getUniqueId();
    if (enabled) {
      if (!this.dangleModePlayers.add(playerId)) {
        return;
      }
      player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, Integer.MAX_VALUE, 0, false, false, false));
      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
      return;
    }

    if (!this.dangleModePlayers.remove(playerId)) {
      return;
    }
    player.removePotionEffect(PotionEffectType.POISON);
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.0f);
  }

  /**
   * Adds a slight vertical puck bounce when the puck takes fall damage.
   * @param slime is the puck
   * @param fallDamage is the amount of fall damage that would have been applied
   */
  private void applyGroundBounce(Slime slime, double fallDamage) {
    if (slime.isDead() || !slime.isValid()) {
      return;
    }

    double bounceY = Math.min(0.44, 0.14 + (fallDamage * 0.07));
    if (bounceY < 0.12) {
      return;
    }
    slime.setNoDamageTicks(0);

    Bukkit.getScheduler().runTask(this.plugin, () -> {
      if (slime.isDead() || !slime.isValid()) {
        return;
      }
      Vector velocity = slime.getVelocity();
      Vector newVelocity = velocity.clone();
      newVelocity.setY(Math.max(velocity.getY(), bounceY));
      newVelocity.setX(newVelocity.getX() * 0.96);
      newVelocity.setZ(newVelocity.getZ() * 0.96);
      slime.setVelocity(newVelocity);
      slime.setNoDamageTicks(0);
    });
    slime.getWorld().playSound(slime.getLocation(), Sound.BLOCK_BASALT_STEP, 15f, 1.5f);
  }

  /**
   * Method to apply the board bounce and normal slime physics.
   * @param slime is the slime to apply it to
   */
  private void applyBoardBounce(Slime slime, Vector oldVelocity) {
    if (slime.isDead() || !slime.isValid()) {
      return;
    }

    Location puckLoc = slime.getLocation();
    Vector velocity = slime.getVelocity();
    Vector newVelocity = velocity.clone();
    double horizontal = Math.hypot(newVelocity.getX(), newVelocity.getZ());
    if (slime.isOnGround() && horizontal > 1.35) {
      double clamp = 1.35 / horizontal;
      newVelocity.setX(newVelocity.getX() * clamp);
      newVelocity.setZ(newVelocity.getZ() * clamp);
      velocity = newVelocity.clone();
    }
    boolean bounceX = false;
    boolean bounceZ = false;

    double lookAheadX = Math.min(0.58, Math.abs(velocity.getX()) * 1.9);
    double lookAheadZ = Math.min(0.58, Math.abs(velocity.getZ()) * 1.9);

    if (velocity.getX() > 0 && (isSolidAtOffset(puckLoc, 0.34, 0) || isSolidAtOffset(puckLoc, 0.34 + lookAheadX, 0))) {
      bounceX = true;
      newVelocity.setX(-Math.abs(velocity.getX()) * 0.62);
    } else if (velocity.getX() < 0 && (isSolidAtOffset(puckLoc, -0.34, 0) || isSolidAtOffset(puckLoc, -0.34 - lookAheadX, 0))) {
      bounceX = true;
      newVelocity.setX(Math.abs(velocity.getX()) * 0.62);
    }

    if (velocity.getZ() > 0 && (isSolidAtOffset(puckLoc, 0, 0.34) || isSolidAtOffset(puckLoc, 0, 0.34 + lookAheadZ))) {
      bounceZ = true;
      newVelocity.setZ(-Math.abs(velocity.getZ()) * 0.62);
    } else if (velocity.getZ() < 0 && (isSolidAtOffset(puckLoc, 0, -0.34) || isSolidAtOffset(puckLoc, 0, -0.34 - lookAheadZ))) {
      bounceZ = true;
      newVelocity.setZ(Math.abs(velocity.getZ()) * 0.62);
    }

    if (!bounceX
            && Math.abs(oldVelocity.getX()) > 0.08
            && Math.abs(velocity.getX()) < 0.0001
            && hasNearbyWallX(puckLoc)) {
      bounceX = true;
      newVelocity.setX(-oldVelocity.getX() * 0.8);
    }

    if (!bounceZ
            && Math.abs(oldVelocity.getZ()) > 0.08
            && Math.abs(velocity.getZ()) < 0.0001
            && hasNearbyWallZ(puckLoc)) {
      bounceZ = true;
      newVelocity.setZ(-oldVelocity.getZ() * 0.8);
    }

    if (slime.isOnGround() && oldVelocity.getY() < -0.16) {
      double flatFloorBounce = Math.min(0.09, Math.abs(oldVelocity.getY()) * 0.22);
      newVelocity.setY(Math.max(newVelocity.getY(), flatFloorBounce));
    }

    if (bounceX || bounceZ) {
      if (Math.abs(newVelocity.getX()) < 0.05) {
        newVelocity.setX((newVelocity.getX() >= 0 ? 1 : -1) * 0.05);
      }
      if (Math.abs(newVelocity.getZ()) < 0.05) {
        newVelocity.setZ((newVelocity.getZ() >= 0 ? 1 : -1) * 0.05);
      }

      if (bounceX) {
        BlockFace pushOutFace = velocity.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        pushPuckOutOfBlock(slime, pushOutFace);
      }
      if (bounceZ) {
        BlockFace pushOutFace = velocity.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        pushPuckOutOfBlock(slime, pushOutFace);
      }
      Sound boardBounceSound = getBoardBounceSound(puckLoc, velocity, bounceX, bounceZ, lookAheadX, lookAheadZ);
      slime.getWorld().playSound(puckLoc, boardBounceSound, 100f, 0.2f);
      slime.setNoDamageTicks(BOARD_BOUNCE_NO_DAMAGE_TICKS);
      slime.setVelocity(newVelocity);
    }

    if (slime.isOnGround()) {
      newVelocity.setX(newVelocity.getX() * 0.965);
      newVelocity.setZ(newVelocity.getZ() * 0.965);
    }
  }

  private Sound getBoardBounceSound(Location puckLoc, Vector velocity, boolean bounceX, boolean bounceZ,
      double lookAheadX, double lookAheadZ) {
    List<Material> collisionMaterials = new ArrayList<>();

    if (bounceX) {
      if (velocity.getX() > 0) {
        addCollisionMaterial(collisionMaterials, puckLoc, 0.34, 0);
        addCollisionMaterial(collisionMaterials, puckLoc, 0.34 + lookAheadX, 0);
      } else if (velocity.getX() < 0) {
        addCollisionMaterial(collisionMaterials, puckLoc, -0.34, 0);
        addCollisionMaterial(collisionMaterials, puckLoc, -0.34 - lookAheadX, 0);
      }
    }

    if (bounceZ) {
      if (velocity.getZ() > 0) {
        addCollisionMaterial(collisionMaterials, puckLoc, 0, 0.34);
        addCollisionMaterial(collisionMaterials, puckLoc, 0, 0.34 + lookAheadZ);
      } else if (velocity.getZ() < 0) {
        addCollisionMaterial(collisionMaterials, puckLoc, 0, -0.34);
        addCollisionMaterial(collisionMaterials, puckLoc, 0, -0.34 - lookAheadZ);
      }
    }

    for (Material material : collisionMaterials) {
      if (material == Material.RED_CONCRETE || material == Material.BLUE_CONCRETE
      || material == Material.RED_STAINED_GLASS || material == Material.BLUE_STAINED_GLASS) {
        return Sound.BLOCK_BELL_USE;
      }
    }

    for (Material material : collisionMaterials) {
      if (material.name().contains("GLASS")) {
        return Sound.BLOCK_GLASS_PLACE;
      }
    }

    return Sound.BLOCK_DEEPSLATE_TILES_BREAK;
  }

  private boolean isSolidAtOffset(Location center, double xOffset, double zOffset) {
    double[] yOffsets = {0.08, 0.22, 0.40, 0.62};
    for (double yOffset : yOffsets) {
      Block check = center.clone().add(xOffset, yOffset, zOffset).getBlock();
      if (isSolidCollision(check)) {
        return true;
      }
    }
    return false;
  }

  private void addCollisionMaterial(List<Material> collisionMaterials, Location center, double xOffset, double zOffset) {
    double[] yOffsets = {0.08, 0.22, 0.40, 0.62};
    for (double yOffset : yOffsets) {
      Block check = center.clone().add(xOffset, yOffset, zOffset).getBlock();
      if (isSolidCollision(check)) {
        collisionMaterials.add(check.getType());
        return;
      }
    }
  }

  private boolean isSolidCollision(Block block) {
    Material type = block.getType();
    return type.isSolid() && !type.isAir();
  }

  private boolean hasNearbyWallX(Location center) {
    return isSolidAtOffset(center, 0.34, 0)
            || isSolidAtOffset(center, 0.58, 0)
            || isSolidAtOffset(center, -0.34, 0)
            || isSolidAtOffset(center, -0.58, 0);
  }

  private boolean hasNearbyWallZ(Location center) {
    return isSolidAtOffset(center, 0, 0.34)
            || isSolidAtOffset(center, 0, 0.58)
            || isSolidAtOffset(center, 0, -0.34)
            || isSolidAtOffset(center, 0, -0.58);
  }

  private void pushPuckOutOfBlock(Slime slime, BlockFace wallFace) {
    Location current = slime.getLocation();
    Location adjusted = current.clone();
    double nudge = 0.12;

    switch (wallFace) {
      case EAST:
        adjusted.setX(adjusted.getX() - nudge);
        break;
      case WEST:
        adjusted.setX(adjusted.getX() + nudge);
        break;
      case SOUTH:
        adjusted.setZ(adjusted.getZ() - nudge);
        break;
      case NORTH:
        adjusted.setZ(adjusted.getZ() + nudge);
        break;
      default:
        return;
    }

    slime.teleport(adjusted);
  }

  private void applyGoalieBodyBounce(Slime slime) {
    if (slime.isDead() || !slime.isValid()) {
      return;
    }

    Vector velocity = slime.getVelocity().clone();
    double horizontalSpeed = velocity.clone().setY(0).length();
    if (horizontalSpeed < 0.04) {
      return;
    }

    UUID slimeId = slime.getUniqueId();
    long now = System.currentTimeMillis();
    Location puckLoc = slime.getLocation();
    for (Player player : slime.getWorld().getPlayers()) {
      if (!lobbyManager.isPlayerAKeeper(player) || lobbyManager.isPlayerInLobby(player)) {
        continue;
      }

      double horizontalHalf = player.isSneaking() ? 1.02 : 0.78;
      double topY = player.getLocation().getY() + 2.2;
      double bottomY = player.getLocation().getY() - 0.2;

      Vector lookAhead = velocity.clone().setY(0);
      if (lookAhead.lengthSquared() > 0.0001) {
        lookAhead.normalize().multiply(Math.min(0.42, horizontalSpeed * 0.7));
      }
      Location adjustedPuckLoc = puckLoc.clone().add(lookAhead);
      double xDiff = Math.abs(adjustedPuckLoc.getX() - player.getLocation().getX());
      double zDiff = Math.abs(adjustedPuckLoc.getZ() - player.getLocation().getZ());
      if (xDiff > horizontalHalf || zDiff > horizontalHalf) {
        continue;
      }
      if (puckLoc.getY() < bottomY || puckLoc.getY() > topY) {
        continue;
      }

      long lastBounce = this.recentGoalieBounceMillis.getOrDefault(slimeId, 0L);
      UUID lastGoalie = this.recentGoalieBouncePlayer.get(slimeId);
      if (now - lastBounce < 220L && player.getUniqueId().equals(lastGoalie)) {
        continue;
      }

      float yawRadians = (float) Math.toRadians(player.getLocation().getYaw());
      Vector yawRedirect = new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
      if (yawRedirect.lengthSquared() < 0.0001) {
        yawRedirect = player.getLocation().getDirection().setY(0);
      }
      if (yawRedirect.lengthSquared() < 0.0001) {
        yawRedirect = new Vector(0, 0, 1);
      }
      yawRedirect.normalize();

      Vector bounced = yawRedirect.multiply(Math.max(0.22, horizontalSpeed * 0.9));
      bounced.setY(Math.max(0.04, velocity.getY() * 0.95));
      slime.setVelocity(bounced);

      Vector pushDirection = yawRedirect.clone().multiply(0.09).setY(0);
      Location current = slime.getLocation();
      if (!isPassableForPuck(current)) {
        Location pushOut = current.clone().add(pushDirection);
        if (isPassableForPuck(pushOut)) {
          slime.teleport(pushOut);
        } else {
          Location elevatedPushOut = pushOut.clone().add(0, 0.15, 0);
          if (isPassableForPuck(elevatedPushOut)) {
            slime.teleport(elevatedPushOut);
          }
        }
      }
      slime.getWorld().playSound(puckLoc, Sound.BLOCK_NETHERITE_BLOCK_HIT, 35f, 1.1f);
      maybeCreditGoalieSave(player, slime, velocity);

      this.recentGoalieBounceMillis.put(slimeId, now);
      this.recentGoalieBouncePlayer.put(slimeId, player.getUniqueId());
      break;
    }
  }

  private boolean isPassableForPuck(Location location) {
    Block feet = location.getBlock();
    Block above = location.clone().add(0, 1, 0).getBlock();
    return feet.isPassable() && above.isPassable();
  }

  private void registerShotOnTargetIfNeeded(Player shooter, Slime slime) {
    if (!shooter.isOnline() || slime.isDead() || !slime.isValid()) {
      return;
    }

    Rink rink = this.lobbyManager.getPlayerRink(shooter);
    if (rink == null || rink.getGameState() != GameState.GAME || rink.getGame() == null) {
      return;
    }

    String team = rink.getTeam(shooter);
    if (team == null) {
      return;
    }
    if (!team.equalsIgnoreCase("home") && !team.equalsIgnoreCase("away")) {
      return;
    }
    UUID slimeId = slime.getUniqueId();
    long now = nowMillis();
    long recentCredit = this.recentShotOnTargetCreditMillis.getOrDefault(slimeId, 0L);
    if (now - recentCredit < 700L) {
      return;
    }

    Location target = team.equalsIgnoreCase("home") ? rink.getAwayGoalCenter() : rink.getHomeGoalCenter();
    Location ownGoal = team.equalsIgnoreCase("home") ? rink.getHomeGoalCenter() : rink.getAwayGoalCenter();

    Vector shotVelocity = slime.getVelocity().clone().setY(0);
    if (shotVelocity.lengthSquared() < 0.02) {
      return;
    }

    Location puckLoc = slime.getLocation();
    Vector towardGoal = target.toVector().subtract(puckLoc.toVector()).setY(0);
    if (towardGoal.lengthSquared() < 0.01) {
      return;
    }

    Vector shotDirection = shotVelocity.clone().normalize();
    Vector towardOwnGoal = ownGoal.toVector().subtract(puckLoc.toVector()).setY(0);
    if (towardOwnGoal.lengthSquared() > 0.01
            && shotDirection.dot(towardOwnGoal.normalize()) > 0.86) {
      return;
    }
    Vector goalDirection = towardGoal.clone().normalize();
    if (shotDirection.dot(goalDirection) < 0.78) {
      return;
    }

    double velocityX = shotVelocity.getX();
    double velocityZ = shotVelocity.getZ();
    double t;
    if (Math.abs(towardGoal.getX()) >= Math.abs(towardGoal.getZ())) {
      if (Math.abs(velocityX) < 0.0001) {
        return;
      }
      t = (target.getX() - puckLoc.getX()) / velocityX;
      if (t <= 0) {
        return;
      }
      double projectedZ = puckLoc.getZ() + velocityZ * t;
      if (Math.abs(projectedZ - target.getZ()) > 4.1) {
        return;
      }
    } else {
      if (Math.abs(velocityZ) < 0.0001) {
        return;
      }
      t = (target.getZ() - puckLoc.getZ()) / velocityZ;
      if (t <= 0) {
        return;
      }
      double projectedX = puckLoc.getX() + velocityX * t;
      if (Math.abs(projectedX - target.getX()) > 4.1) {
        return;
      }
    }

    double projectedY = puckLoc.getY() + slime.getVelocity().getY() * t;
    if (projectedY < target.getY() - 0.8 || projectedY > target.getY() + 1.35) {
      return;
    }

    this.recentShotOnTargetCreditMillis.put(slimeId, now);
    rink.addShotOnTarget(shooter);
  }

  private void clearGoaliePads(UUID goalieId) {
    List<ArmorStand> stands = this.goaliePadStands.remove(goalieId);
    if (stands == null) {
      return;
    }

    for (ArmorStand stand : stands) {
      if (stand != null && stand.isValid()) {
        stand.remove();
      }
    }
  }


  @EventHandler
  public void onHitPlayer(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) {
      return;
    }

    Player damaged = (Player) e.getEntity();
    Player damager = (Player) e.getDamager();

    if (lobbyManager.isPlayerInLobby(damaged) || lobbyManager.isPlayerInLobby(damager)) {
      e.setCancelled(true);
      return;
    }

    Rink damagedRink = lobbyManager.getPlayerRink(damaged);
    Rink damagerRink = lobbyManager.getPlayerRink(damager);

    if (damagedRink == null || !damagedRink.equals(damagerRink)) {
      e.setCancelled(true);
      return;
    }

    String damagedTeam = damagedRink.getTeam(damaged);
    String damagerTeam = damagerRink.getTeam(damager);

    if (damagedTeam == null || damagerTeam == null || damagedTeam.equalsIgnoreCase(damagerTeam)) {
      e.setCancelled(true);
      return;
    }

    if (!damagedRink.isHittingAllowed() || damagedRink.getGameState() != GameState.GAME) {
      e.setCancelled(true);
      return;
    }
    if (!isOnIceSurface(damager) || !isOnIceSurface(damaged)) {
      e.setCancelled(true);
      return;
    }
    long now = System.currentTimeMillis();
    long hitCooldownEnd = this.playerHitCooldownMillis.getOrDefault(damager.getUniqueId(), 0L);
    if (now < hitCooldownEnd) {
      e.setCancelled(true);
      return;
    }
    this.playerHitCooldownMillis.put(damager.getUniqueId(), now + PLAYER_HIT_COOLDOWN_MS);


    for (Player p : damagerRink.getRefs()) {
      p.sendMessage("§6[§bBH§6] §e" + damager.getName() + " hit "
              + damaged.getName() + "§7 (Power " + damager.getLevel() + ")");
    }

    int hitLevel = Math.max(1, Math.min(3, damager.getLevel()));
    this.resetPower(damager);
    e.setDamage(0.0);
    damaged.sendMessage("§6[§bBH§6] §cYou were hit by §f" + damager.getName() + " §7-> Power " + hitLevel);
    damager.sendMessage("§6[§bBH§6] §aYou hit §f" + damaged.getName() + " §7-> Power " + hitLevel);
  }

  private boolean canGoalieGlovePuck(Player goalie, Slime puck, long now) {
    UUID puckId = puck.getUniqueId();
    long ownHitLockEnd = this.goalieOwnHitNoGloveUntilMillis.getOrDefault(puckId, 0L);
    UUID ownHitGoalie = this.goalieOwnHitNoGlovePlayer.get(puckId);
    if (ownHitGoalie != null && ownHitGoalie.equals(goalie.getUniqueId()) && now < ownHitLockEnd) {
      return false;
    }
    return true;
  }

  private boolean handleBenchBarrierInteract(Player player, Location barrier) {
    Rink rink = this.lobbyManager.getPlayerRink(player);
    if (rink == null) {
      return false;
    }

    String team = rink.getTeam(player);
    Location teamBench = null;
    if ("home".equalsIgnoreCase(team)) {
      teamBench = rink.getHomeBench();
    } else if ("away".equalsIgnoreCase(team)) {
      teamBench = rink.getAwayBench();
    }
    if (teamBench == null) {
      return false;
    }

    if (!barrier.getWorld().equals(teamBench.getWorld()) || barrier.distanceSquared(teamBench) > 25.0) {
      return false;
    }

    Location destination;
    if (player.getLocation().distanceSquared(teamBench) <= 6.25) {
      Vector toCenter = rink.getCenterIce().toVector().subtract(teamBench.toVector()).setY(0);
      if (toCenter.lengthSquared() < 0.0001) {
        toCenter = player.getLocation().getDirection().setY(0);
      }
      if (toCenter.lengthSquared() < 0.0001) {
        toCenter = new Vector(0, 0, 1);
      }
      toCenter.normalize();
      destination = findSafeBenchExit(teamBench, toCenter);
    } else {
      destination = teamBench.clone().add(0, 0.1, 0);
    }

    destination.setX(destination.getBlockX() + 0.5);
    destination.setZ(destination.getBlockZ() + 0.5);
    destination.setYaw(player.getLocation().getYaw());
    destination.setPitch(player.getLocation().getPitch());
    player.setNoDamageTicks(20);
    player.setFallDistance(0f);
    player.teleport(destination);
    return true;
  }

  private Location findSafeBenchExit(Location teamBench, Vector toCenter) {
    Vector direction = toCenter.clone();
    if (direction.lengthSquared() < 0.0001) {
      direction = new Vector(0, 0, 1);
    }
    direction.normalize();

    for (double distance = 4.0; distance <= 6.0; distance += 0.5) {
      Location attempt = teamBench.clone().add(direction.clone().multiply(distance)).add(0, 0.1, 0);
      Block feet = attempt.getBlock();
      Block head = feet.getRelative(BlockFace.UP);
      if (feet.isPassable() && head.isPassable()) {
        return attempt;
      }
    }

    return teamBench.clone().add(direction.multiply(4.5)).add(0, 0.1, 0);
  }

  private boolean isOnIceSurface(Player player) {
    Location location = player.getLocation();
    Block blockBelow = location.clone().add(0, -0.1, 0).getBlock();
    return ICE_SURFACES.contains(blockBelow.getType());
  }

  private void maybeCreditGoalieSave(Player goalie, Slime slime, Vector incomingVelocity) {
    Rink rink = this.lobbyManager.getPlayerRink(goalie);
    if (rink == null || rink.getGameState() != GameState.GAME || rink.getGame() == null) {
      return;
    }

    Player shooter = rink.getGame().getLastTouchPlayer();
    if (shooter == null || shooter.equals(goalie)) {
      return;
    }

    String goalieTeam = rink.getTeam(goalie);
    String shooterTeam = rink.getTeam(shooter);
    if (!"home".equalsIgnoreCase(goalieTeam) && !"away".equalsIgnoreCase(goalieTeam)) {
      return;
    }
    if (!"home".equalsIgnoreCase(shooterTeam) && !"away".equalsIgnoreCase(shooterTeam)) {
      return;
    }
    if (goalieTeam.equalsIgnoreCase(shooterTeam)) {
      return;
    }

    Location defendedGoal = goalieTeam.equalsIgnoreCase("home") ? rink.getHomeGoalCenter() : rink.getAwayGoalCenter();
    Vector towardGoal = defendedGoal.toVector().subtract(slime.getLocation().toVector()).setY(0);
    Vector flatIncoming = incomingVelocity.clone().setY(0);
    if (towardGoal.lengthSquared() < 0.01 || flatIncoming.lengthSquared() < 0.02) {
      return;
    }
    if (flatIncoming.normalize().dot(towardGoal.normalize()) < 0.72) {
      return;
    }

    rink.addGoalieSave(goalie);
  }

  private void enforceGoalieHalfLineRule() {
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (!this.lobbyManager.isPlayerAKeeper(online) || this.lobbyManager.isPlayerInLobby(online)) {
        continue;
      }

      Rink rink = this.lobbyManager.getPlayerRink(online);
      if (rink == null) {
        continue;
      }
      if (rink.getGameState() != GameState.GAME) {
        continue;
      }

      String team = rink.getTeam(online);
      if (!"home".equalsIgnoreCase(team) && !"away".equalsIgnoreCase(team)) {
        continue;
      }

      Location center = rink.getCenterIce();
      Location homeGoal = rink.getHomeGoalCenter();
      Location awayGoal = rink.getAwayGoalCenter();
      Location playerLoc = online.getLocation();

      double homeAxis = Math.abs(homeGoal.getX() - center.getX()) >= Math.abs(homeGoal.getZ() - center.getZ())
              ? homeGoal.getX() : homeGoal.getZ();
      double awayAxis = Math.abs(awayGoal.getX() - center.getX()) >= Math.abs(awayGoal.getZ() - center.getZ())
              ? awayGoal.getX() : awayGoal.getZ();
      boolean useXAxis = Math.abs(homeGoal.getX() - center.getX()) >= Math.abs(homeGoal.getZ() - center.getZ());

      double centerAxis = useXAxis ? center.getX() : center.getZ();
      double goalieAxis = useXAxis ? playerLoc.getX() : playerLoc.getZ();
      boolean homeShouldBeLess = homeAxis < awayAxis;

      boolean crossed = "home".equalsIgnoreCase(team)
              ? (homeShouldBeLess ? goalieAxis > centerAxis + 0.2 : goalieAxis < centerAxis - 0.2)
              : (homeShouldBeLess ? goalieAxis < centerAxis - 0.2 : goalieAxis > centerAxis + 0.2);
      if (!crossed) {
        continue;
      }

      Location ownGoal = "home".equalsIgnoreCase(team) ? homeGoal : awayGoal;
      Vector retreatDir = ownGoal.toVector().subtract(playerLoc.toVector()).setY(0);
      if (retreatDir.lengthSquared() < 0.0001) {
        retreatDir = ownGoal.toVector().subtract(center.toVector()).setY(0);
      }
      retreatDir.normalize();
      online.setVelocity(retreatDir.multiply(0.8).setY(0.08));
      online.playSound(playerLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
    }
  }

  private CosmeticsManager getCosmeticsManager() {
    return ((BenHockey) this.plugin).getCosmeticsManager();
  }

  private long nowMillis() {
    return System.currentTimeMillis();
  }

  /**
   * Helper method to reset a player's power when they hit with a stick.
   * @param p is the player in question
   */
  private void resetPower(Player p) {
    ItemStack item = p.getInventory().getItemInMainHand();
    ItemMeta meta = isHockeyStick(item) ? item.getItemMeta() : null;

    if (meta == null || !meta.hasDisplayName() || !isHockeyStickName(meta.getDisplayName())) {
      return;
    }

    meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

    AttributeModifier attackSpeedModifier = new AttributeModifier(
            UUID.randomUUID(),
            "generic_attack_speed",
            2.5,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
    );
    meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);

    p.setLevel(1);
    updateHitLevelEffects(p, 1);
    meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
    item.setItemMeta(meta);
    p.getInventory().setItemInMainHand(item);
  }

  private void updateHitLevelEffects(Player player, int hitLevel) {
    if (hitLevel >= 3) {
      player.addPotionEffect(new PotionEffect(
              PotionEffectType.SLOW,
              Integer.MAX_VALUE,
              HIT_LEVEL_THREE_SLOWNESS_AMPLIFIER,
              false,
              false,
              true
      ));
      return;
    }

    player.removePotionEffect(PotionEffectType.SLOW);
  }

  private boolean isHockeyStick(ItemStack item) {
    if (item == null || !CosmeticsManager.isValidStickMaterial(item.getType()) || !item.hasItemMeta()) {
      return false;
    }

    ItemMeta meta = item.getItemMeta();
    return meta != null && meta.hasDisplayName() && isHockeyStickName(meta.getDisplayName());
  }

  private boolean isHockeyStickName(String displayName) {
    return HOCKEY_STICK_NAME.equals(displayName) || GOALIE_STICK_NAME.equals(displayName);
  }

  private boolean isIceSurface(Material material) {
    return ICE_SURFACES.contains(material);
  }

  private Location findNearestIceDrop(Player goalie, int maxRadius) {
    Location origin = goalie.getLocation();
    World world = origin.getWorld();
    if (world == null) {
      return null;
    }

    Location best = null;
    double bestDistance = Double.MAX_VALUE;
    int baseX = origin.getBlockX();
    int baseY = origin.getBlockY();
    int baseZ = origin.getBlockZ();

    for (int radius = 0; radius <= maxRadius; radius++) {
      for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
          int worldX = baseX + x;
          int worldZ = baseZ + z;

          for (int y = baseY + 2; y >= baseY - 6; y--) {
            Block below = world.getBlockAt(worldX, y, worldZ);
            if (!isIceSurface(below.getType())) {
              continue;
            }

            Block feet = below.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            if (!feet.isPassable() || !head.isPassable()) {
              continue;
            }

            Location candidate = below.getLocation().add(0.5, 1.02, 0.5);
            double distance = candidate.distanceSquared(origin);
            if (distance < bestDistance) {
              bestDistance = distance;
              best = candidate;
            }
            break;
          }
        }
      }

      if (best != null) {
        return best;
      }
    }

    return null;
  }
}
