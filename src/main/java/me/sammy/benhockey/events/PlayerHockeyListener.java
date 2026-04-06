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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import me.sammy.benhockey.game.GameState;
import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;

/**
 * Deals with the logic with the hockey stick.
 */
public class PlayerHockeyListener implements Listener {

  private static final int GOALIE_SLIDE_COOLDOWN_TICKS = 10;
  private static final int GOALIE_GLOVE_REGRAB_COOLDOWN_TICKS = 10;
  private static final int HIT_LEVEL_THREE_SLOWNESS_AMPLIFIER = 0;
  private static final long PLAYER_HIT_COOLDOWN_MS = 350L;
  private static final int BOARD_BOUNCE_NO_DAMAGE_TICKS = 10;
  private final LobbyManager lobbyManager;
  private HashMap<UUID, Double> charges = new HashMap();
  private final JavaPlugin plugin;
  private final Set<UUID> rightClickCooldown = new HashSet<>();
  private final Set<UUID> slideCooldown = new HashSet<>();
  private final Map<UUID, Integer> goalieSlideCooldownTicks = new HashMap<>();
  private final Map<UUID, Slime> goalieGloveSlime = new HashMap<>();
  private final Map<UUID, BukkitTask> gloveTimers = new HashMap<>();
  private final Set<UUID> glovedGoalies = new HashSet<>();
  private final Map<UUID, Long> recentGoalieBounceMillis = new HashMap<>();
  private final Map<UUID, UUID> recentGoalieBouncePlayer = new HashMap<>();
  private final Map<UUID, Long> boardBounceCooldownMillis = new HashMap<>();
  private final Map<UUID, Long> goalieGloveReleaseCooldownMillis = new HashMap<>();
  private final Map<UUID, List<ArmorStand>> goaliePadStands = new HashMap<>();
  private final Map<UUID, Double> lastPuckVerticalVelocity = new HashMap<>();
  private final Map<UUID, Vector> lastPuckVelocity = new HashMap<>();
  private final Map<UUID, Double> puckAirbornePeakY = new HashMap<>();
  private final Set<UUID> dangleModePlayers = new HashSet<>();
  private final Map<UUID, Long> playerHitCooldownMillis = new HashMap<>();
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
    clearGoalieGloveState(e.getPlayer().getUniqueId());
    clearGoaliePads(e.getPlayer().getUniqueId());
    this.recentGoalieBouncePlayer.values().removeIf(id -> id.equals(e.getPlayer().getUniqueId()));
  }

  @EventHandler
  public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
    Player player = e.getPlayer();
    String message = e.getMessage().toLowerCase();
    if (message.startsWith("/join") || message.startsWith("/team") || message.startsWith("/goalie")) {
      setDangleMode(player, false);
      this.resetPower(player);
    }
  }

  @EventHandler
  public void onGoalieMove(PlayerMoveEvent e) {
    Player player = e.getPlayer();
    if (!this.lobbyManager.isPlayerAKeeper(player) || !player.isSneaking()) {
      return;
    }

    if (e.getTo() == null || e.getFrom().distanceSquared(e.getTo()) < 0.0004) {
      return;
    }
    ensureAndPositionGoaliePads(player);
  }

  @EventHandler
  public void onSneak(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();

    if (lobbyManager.isPlayerInLobby(p)) {
      return;
    }

    if (e.isSneaking() && lobbyManager.isPlayerOnTeam(p) && !lobbyManager.isPlayerAKeeper(p)) {
      charges.put(p.getUniqueId(), 0.0);
    } else {
      p.setExp(0.0f);
      charges.remove(p.getUniqueId());
    }

    if (lobbyManager.isPlayerAKeeper(p)) {
      if (e.isSneaking()) {
        ensureAndPositionGoaliePads(p);
      } else {
        clearGoaliePads(p.getUniqueId());
      }
    }
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
    ItemMeta meta = (item != null && item.getType() == Material.STICK)
            ? item.getItemMeta() : null;

    if (meta == null || !meta.hasDisplayName() || !isHockeyStickName(meta.getDisplayName())) {
      return;
    }

    long now = System.currentTimeMillis();
    double speed = slime.getVelocity().length();
    long gloveCooldownEnd = this.goalieGloveReleaseCooldownMillis.getOrDefault(player.getUniqueId(), 0L);
    if (lobbyManager.isPlayerAKeeper(player) && speed > 0.3 && now >= gloveCooldownEnd) {
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
    if (dangleMode) {
      slime.playEffect(EntityEffect.HURT);
      if (hitLevel <= 2) {
        slime.setNoDamageTicks(0);
      }
    }

    double charge = getShiftCharge(player);
    boolean shiftLift = player.isSneaking() && charge > 0.02;
    this.resetPower(player);

    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
      if (slime.isDead() || !slime.isValid()) {
        return;
      }
      if (dangleMode && hitLevel <= 2) {
        slime.setNoDamageTicks(0);
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
        double forwardImpulse;
        if (hitLevel == 1) {
          forwardImpulse = 0.72;
        } else if (hitLevel == 2) {
          forwardImpulse = 0.92;
        } else {
          forwardImpulse = 1.12;
        }
        Vector boosted = existingVelocity.clone().add(flatForward.multiply(forwardImpulse));

        double basePop = 0.07 + (hitLevel * 0.03);
        if (shiftLift) {
          boosted.setY(Math.max(Math.max(existingVelocity.getY(), basePop), getShiftLift(charge)));
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
        sideShuffle.multiply(0.72);
        sideShuffle.setY(shiftLift ? Math.max(updatedVelocity.getY(), getShiftLift(charge)) : 0.0);
        slime.setVelocity(sideShuffle);
        Vector playerSlide = sideShuffle.clone().multiply(0.62);
        player.setVelocity(playerSlide);
        return;
      }

      if (hitLevel == 2) {
        Vector pull = updatedVelocity.clone().setY(0).multiply(-0.18);
        Vector backward = forward.clone().multiply(-0.42);
        pull.add(backward);
        if (pull.lengthSquared() < 0.06) {
          pull = backward;
        }
        pull.setY(shiftLift ? getShiftLift(charge) : 0.0);
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

      Vector boosted = horizontalMomentum.clone().add(shotDirection.multiply(1.45));
      double basePop = 0.07 + (hitLevel * 0.03);
      if (shiftLift) {
        boosted.setY(Math.max(Math.max(existingVelocity.getY(), basePop), getShiftLift(charge)));
      } else {
        boosted.setY(Math.max(existingVelocity.getY(), basePop));
      }
      slime.setVelocity(boosted);
    }, 1L);
  }

  private double getShiftCharge(Player player) {
    double charged = this.charges.getOrDefault(player.getUniqueId(), 0.0);
    return Math.max(charged, player.getExp());
  }

  private double getShiftLift(double charge) {
    double clampedCharge = Math.max(0.0, Math.min(1.0, charge));
    return 0.20 + (clampedCharge * 0.64);
  }

  private double getPuckLiftY(Slime slime, double currentY, double basePop, double requestedLift) {
    if (slime.isOnGround()) {
      return Math.max(Math.max(currentY, basePop), requestedLift);
    }

    double marginalIncrease = Math.max(currentY + 0.05, requestedLift * 0.72);
    return Math.max(Math.max(currentY, basePop), Math.min(marginalIncrease, 0.48));
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
      int seconds = 3;

      @Override
      public void run() {
        if (!goalie.isOnline()) {
          clearGoalieGloveState(goalieId);
          this.cancel();
          return;
        }

        if (seconds == 0) {
          if (glovedGoalies.contains(goalieId)) {
            dropGlovedPuck(goalie);
            this.cancel();
            return;
          }
        }

        goalie.playSound(goalie.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 20f, 1f);
        seconds--;
      }
    }.runTaskTimer(plugin, 0L, 20L);

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

    puck.setVelocity(new Vector(0, 0.02, 0));
    this.goalieGloveReleaseCooldownMillis.put(id, nowMillis() + (GOALIE_GLOVE_REGRAB_COOLDOWN_TICKS * 50L));
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

    if (e.getCause() == EntityDamageEvent.DamageCause.FALL ||
            e.getCause() == EntityDamageEvent.DamageCause.LAVA ||
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
        world.spawnParticle(Particle.FLAME, slime.getLocation(), 1, 0, 0, 0, 0);
        slime.setRotation(0f, 0f);
        if (!slime.isOnGround()) {
          double y = slime.getLocation().getY();
          this.puckAirbornePeakY.merge(slimeId, y, Math::max);
        }
        double previousVertical = this.lastPuckVerticalVelocity.getOrDefault(
                slimeId,
                slime.getVelocity().getY()
        );
        Vector previousVelocity = this.lastPuckVelocity.getOrDefault(
                slimeId,
                slime.getVelocity().clone()
        );
        applyBoardBounce(slime, previousVelocity);
        applyGroundBounce(slime, previousVertical);
        applyGoalieBodyBounce(slime);
        this.lastPuckVelocity.put(slimeId, slime.getVelocity().clone());
        this.lastPuckVerticalVelocity.put(slimeId, slime.getVelocity().getY());
      }
    }
    this.lastPuckVerticalVelocity.keySet().retainAll(livePucks);
    this.lastPuckVelocity.keySet().retainAll(livePucks);
    this.puckAirbornePeakY.keySet().retainAll(livePucks);
    this.recentGoalieBounceMillis.keySet().retainAll(livePucks);
    this.recentGoalieBouncePlayer.keySet().retainAll(livePucks);
    this.boardBounceCooldownMillis.keySet().retainAll(livePucks);
    this.goalieGloveReleaseCooldownMillis.entrySet().removeIf(entry -> nowMillis() > entry.getValue() + 5000L);
    this.playerHitCooldownMillis.entrySet().removeIf(entry -> nowMillis() > entry.getValue() + 1000L);

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

    updateGoaliePads();
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
   * Adds a slight vertical puck bounce when the puck lands from a high-enough pop.
   * @param slime is the puck
   * @param previousVerticalVelocity is the puck's vertical velocity from the previous tick
   */
  private void applyGroundBounce(Slime slime, double previousVerticalVelocity) {
    if (slime.isDead() || !slime.isValid() || !slime.isOnGround()) {
      return;
    }

    UUID slimeId = slime.getUniqueId();
    double landingY = slime.getLocation().getY();
    Double peakY = this.puckAirbornePeakY.get(slimeId);
    if (peakY == null || peakY - landingY < 0.30) {
      this.puckAirbornePeakY.remove(slimeId);
      return;
    }

    Vector velocity = slime.getVelocity();
    if (velocity.getY() > 0.05 || previousVerticalVelocity > -0.08) {
      return;
    }

    double bounceY = Math.min(0.20, Math.abs(previousVerticalVelocity) * 0.34);
    if (bounceY < 0.05) {
      return;
    }

    Vector newVelocity = velocity.clone();
    newVelocity.setY(bounceY);
    newVelocity.setX(newVelocity.getX() * 0.95);
    newVelocity.setZ(newVelocity.getZ() * 0.95);
    slime.setVelocity(newVelocity);
    this.puckAirbornePeakY.remove(slimeId);
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
      slime.setVelocity(newVelocity);
    }

    if (slime.isOnGround()) {
      newVelocity.setX(newVelocity.getX() * 0.965);
      newVelocity.setZ(newVelocity.getZ() * 0.965);
    }

    Sound boardBounceSound = getBoardBounceSound(puckLoc, velocity, bounceX, bounceZ, lookAheadX, lookAheadZ);
    slime.getWorld().playSound(puckLoc, boardBounceSound, 100f, 0.2f);
    slime.setVelocity(newVelocity);
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

      Vector contactNormal = puckLoc.toVector().subtract(player.getLocation().toVector()).setY(0);
      if (contactNormal.lengthSquared() < 0.0001) {
        float yawRadians = (float) Math.toRadians(player.getLocation().getYaw());
        contactNormal = new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
      }
      contactNormal.normalize();

      Vector incomingHorizontal = velocity.clone().setY(0);
      if (incomingHorizontal.lengthSquared() < 0.0001) {
        incomingHorizontal = contactNormal.clone().multiply(-horizontalSpeed);
      }

      double towardGoalie = incomingHorizontal.dot(contactNormal);
      if (towardGoalie >= 0) {
        continue;
      }

      Vector reflected = incomingHorizontal.clone().subtract(contactNormal.clone().multiply(2 * towardGoalie));
      if (reflected.lengthSquared() < 0.0001) {
        reflected = contactNormal.clone();
      } else {
        reflected.normalize();
      }

      Vector bounced = reflected.clone().multiply(Math.max(0.26, horizontalSpeed * 0.98));
      bounced.setY(Math.max(0.04, velocity.getY()));
      slime.setVelocity(bounced);

      Vector pushDirection = reflected.clone().multiply(0.66).setY(0);
      Location pushOut = puckLoc.clone().add(pushDirection);
      if (isPassableForPuck(pushOut)) {
        slime.teleport(pushOut);
      } else {
        Location elevatedPushOut = pushOut.clone().add(0, 0.15, 0);
        if (isPassableForPuck(elevatedPushOut)) {
          slime.teleport(elevatedPushOut);
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
    if (!team.equalsIgnoreCase("home") && !team.equalsIgnoreCase("away")) {
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
            && shotDirection.dot(towardOwnGoal.normalize()) > 0.88) {
      return;
    }
    Vector goalDirection = towardGoal.clone().normalize();
    if (shotDirection.dot(goalDirection) < 0.93) {
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
      if (Math.abs(projectedZ - target.getZ()) > 2.6) {
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
      if (Math.abs(projectedX - target.getX()) > 2.6) {
        return;
      }
    }

    double projectedY = puckLoc.getY() + slime.getVelocity().getY() * t;
    if (projectedY < target.getY() - 0.8 || projectedY > target.getY() + 2.3) {
      return;
    }

    rink.addShotOnTarget(shooter);
  }

  private void updateGoaliePads() {
    Set<UUID> activePadGoalies = new HashSet<>();
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (!this.lobbyManager.isPlayerAKeeper(online) || !online.isSneaking()) {
        continue;
      }

      Rink rink = this.lobbyManager.getPlayerRink(online);
      if (rink == null) {
        continue;
      }

      activePadGoalies.add(online.getUniqueId());
      ensureAndPositionGoaliePads(online);
    }

    for (UUID goalieId : new HashSet<>(this.goaliePadStands.keySet())) {
      if (!activePadGoalies.contains(goalieId)) {
        clearGoaliePads(goalieId);
      }
    }
  }

  private void ensureAndPositionGoaliePads(Player goalie) {
    List<ArmorStand> pads = this.goaliePadStands.get(goalie.getUniqueId());
    if (pads == null) {
      pads = new ArrayList<>();
      pads.add(spawnGoaliePad(goalie.getLocation()));
      pads.add(spawnGoaliePad(goalie.getLocation()));
      this.goaliePadStands.put(goalie.getUniqueId(), pads);
    }
    if (pads.size() != 2) {
      clearGoaliePads(goalie.getUniqueId());
      pads = new ArrayList<>();
      pads.add(spawnGoaliePad(goalie.getLocation()));
      pads.add(spawnGoaliePad(goalie.getLocation()));
      this.goaliePadStands.put(goalie.getUniqueId(), pads);
    }

    Vector forward = goalie.getLocation().getDirection().setY(0);
    if (forward.lengthSquared() < 0.0001) {
      forward = new Vector(0, 0, 1);
    }
    forward.normalize();
    Vector left = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
    Vector goalieVelocity = goalie.getVelocity().clone().setY(0);
    if (goalieVelocity.lengthSquared() > 0.0001) {
      goalieVelocity.multiply(0.8);
    }

    Location base = goalie.getLocation().clone()
            .add(forward.clone().multiply(0.52))
            .add(goalieVelocity);
    float padYaw = goalie.getLocation().getYaw();

    Location leftPad = base.clone().add(left.clone().multiply(0.22));
    Location rightPad = base.clone().subtract(left.clone().multiply(0.22));
    leftPad.setYaw(padYaw + 16f);
    rightPad.setYaw(padYaw - 16f);
    leftPad.setPitch(0f);
    rightPad.setPitch(0f);
    pads.get(0).teleport(leftPad);
    pads.get(1).teleport(rightPad);
    for (ArmorStand pad : pads) {
      pad.setHeadPose(new EulerAngle(0, 0, 0));
      pad.setBodyPose(new EulerAngle(0, 0, 0));
      pad.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
      pad.setLeftArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
    }
  }

  private ArmorStand spawnGoaliePad(Location location) {
    ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, armorStand -> {
      armorStand.setInvisible(true);
      armorStand.setGravity(false);
      armorStand.setMarker(true);
      armorStand.setSmall(false);
      armorStand.setBasePlate(false);
      armorStand.setArms(true);
      armorStand.setSilent(true);
      armorStand.setInvulnerable(true);
      armorStand.setCollidable(false);
      armorStand.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
      armorStand.getEquipment().setItemInMainHand(null);
      armorStand.getEquipment().setItemInOffHand(null);
    });
    return stand;
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
    e.setCancelled(true);
    Vector push = damaged.getLocation().toVector().subtract(damager.getLocation().toVector()).setY(0);
    if (push.lengthSquared() < 0.0001) {
      push = damager.getLocation().getDirection().setY(0);
    }
    if (push.lengthSquared() < 0.0001) {
      push = new Vector(0, 0, 1);
    }

    double horizontalStrength = hitLevel == 3 ? 1.2 : (0.95 + (hitLevel * 0.45));
    Vector knockback = push.normalize().multiply(horizontalStrength);

    double currentY = damaged.getVelocity().getY();
    boolean liftHit = damager.isSneaking();
    double lift = damaged.isOnGround()
            ? (0.08 + hitLevel * 0.06)
            : (liftHit ? (0.05 + hitLevel * 0.03) : (0.13 + hitLevel * 0.05));
    knockback.setY(Math.max(currentY, lift));

    damaged.setVelocity(knockback);
    damaged.playEffect(EntityEffect.HURT);
    damaged.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, damaged.getLocation().add(0, 1.05, 0), 8, 0.25, 0.35, 0.25, 0.0);
    damaged.getWorld().playSound(damaged.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f + (hitLevel * 0.07f));
    damaged.sendMessage("§6[§bBH§6] §cYou were hit by §f" + damager.getName() + " §7-> Power " + hitLevel);
    damager.sendMessage("§6[§bBH§6] §aYou hit §f" + damaged.getName() + " §7-> Power " + hitLevel);
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

  private long nowMillis() {
    return System.currentTimeMillis();
  }

  /**
   * Helper method to reset a player's power when they hit with a stick.
   * @param p is the player in question
   */
  private void resetPower(Player p) {
    ItemStack item = p.getInventory().getItemInMainHand();
    ItemMeta meta = item.getType() == Material.STICK
            ? item.getItemMeta() : null;

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
    if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
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
