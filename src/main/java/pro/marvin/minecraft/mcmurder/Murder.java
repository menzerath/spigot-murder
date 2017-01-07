package pro.marvin.minecraft.mcmurder;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Murder extends JavaPlugin implements Listener {
	// Game-stats ...
	public HashMap<Integer, Boolean> arenaConfig = new HashMap<>();
	private HashMap<Integer, Block> updateSigns = new HashMap<>();

	private HashMap<Integer, Boolean> gameStarted = new HashMap<>();
	private HashMap<Integer, Integer> countdown = new HashMap<>();

	public List<Player> playersList = new ArrayList<>();
	public HashMap<String, Integer> playersMap = new HashMap<>();
	public HashMap<String, String> playersTeam = new HashMap<>();
	public HashMap<String, Boolean> playersAlive = new HashMap<>();

	private HashMap<String, Long> reloadTime = new HashMap<>();

	// Config-file
	public static int maxPlayers = 8;
	public static String gameWorldPrefix;

	/**
	 * Set-up important timers and other stuff
	 */
	@Override
	public void onEnable() {
		getCommand("mcm").setExecutor(new CommandManager(this));
		getServer().getPluginManager().registerEvents(this, this);

		init();

		loadConfig();

		// Start repeating tasks
		countdown();
		reloadTime();
		updateSigns();
	}

	/**
	 * Remove every player from the arena
	 */
	@Override
	public void onDisable() {
		for (Player p : playersList) {
			p.setGameMode(GameMode.ADVENTURE);
			p.getInventory().clear();
			p.teleport(mcmSpawn());
			p.removePotionEffect(PotionEffectType.BLINDNESS);
			p.removePotionEffect(PotionEffectType.SLOW);
			p.setExp(0);
			p.setLevel(0);

			List<Entity> entList = p.getWorld().getEntities();
			for (Entity current : entList) {
				if (current instanceof Item) {
					current.remove();
				}
			}
		}

		// Show everyone to everyone
		for (Player playersInMap : playersList) {
			for (Player playersInMap2 : playersList) {
				playersInMap.showPlayer(playersInMap2);
				playersInMap2.showPlayer(playersInMap);
			}
		}
	}

	/**
	 * Prepare the HashMaps
	 */
	private void init() {
		for (int i = 1; i < 100; i++) {
			gameStarted.put(i, false);
			countdown.put(i, -1);
			arenaConfig.put(i, false);
		}
	}

	/**
	 * A game will start. Prepare the players and send them in the arena.
	 *
	 * @param mapId Which map / arena will start
	 */
	public void startGame(int mapId) {
		countdown.put(mapId, -1);

		// Prepare world
		World w = getServer().getWorld(gameWorldPrefix + mapId);
		w.setTime(6000);
		w.setDifficulty(Difficulty.PEACEFUL);
		w.setStorm(false);

		// Show everyone to everyone
		for (Player playersInMap : getPlayerInArena(mapId)) {
			for (Player playersInMap2 : getPlayerInArena(mapId)) {
				playersInMap.showPlayer(playersInMap2);
				playersInMap2.showPlayer(playersInMap);
			}
		}

		// Prepare players
		for (Player p : getPlayerInArena(mapId)) {
			preparePlayer(p);
		}

		chooseMurdererAndBystanderWithWeapon(mapId);

		// Show player's team
		for (Player p : getPlayerInArena(mapId)) {
			if (playerIsMurderer(p)) {
				p.sendMessage(Texts.PRE_TEXT + Texts.GAME_MURDERER);
			} else if (playerIsBystanderWithWeapon(p)) {
				p.sendMessage(Texts.PRE_TEXT + Texts.GAME_BYSTANDER_WEAPON);
			} else if (playerIsBystander(p)) {
				p.sendMessage(Texts.PRE_TEXT + Texts.GAME_BYSTANDER);
			}
			p.playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 1, 1);
		}

		// Start!
		gameStarted.put(mapId, true);
	}

	/**
	 * A game will stop. Remove weapons and start a firework.
	 *
	 * @param mapId Which map / arena will stop
	 */
	public void stopGame(final int mapId, boolean restart) {
		if (!getGameStarted(mapId)) return;

		// Get the murderer
		Player murderer = getPlayerInArena(mapId).get(0); // Dummy
		for (Player players : getPlayerInArena(mapId)) {
			if (playerIsMurderer(players)) murderer = players;
		}

		// Send a message with the score
		for (Player p : getPlayerInArena(mapId)) {
			reloadTime.put(p.getName(), System.currentTimeMillis() - 5000);
			playersAlive.remove(p.getName());
			p.getInventory().clear();
			p.removePotionEffect(PotionEffectType.SLOW);
			p.sendMessage(Texts.PRE_TEXT + Texts.GAME_ENDED.replace("%murderer", murderer.getDisplayName()));
		}

		// Remove Players' Team
		for (Player p : getPlayerInArena(mapId)) {
			playersTeam.remove(p.getName());
		}

		// Remove every item on the ground
		World world = getServer().getWorld(gameWorldPrefix + mapId);
		List<Entity> entList = world.getEntities();
		for (Entity current : entList) {
			if (current instanceof Item) {
				current.remove();
			}
		}

		gameStarted.put(mapId, false);
		if (restart) {
			countdown.put(mapId, 30);
		} else {
			for (Player p : getPlayerInArena(mapId)) {
				p.teleport(mcmSpawn());
				p.setAllowFlight(false);
				playersList.remove(p);
				playersMap.remove(p.getName());
				playersTeam.remove(p.getName());
				playersAlive.remove(p.getName());
				reloadTime.remove(p.getName());
				p.getInventory().clear();
				p.setExp(0);
				p.setLevel(0);
			}
		}
	}

	private void chooseMurdererAndBystanderWithWeapon(int mapId) {
		Object[] players = getPlayerInArena(mapId).toArray();
		Random r = new Random();
		int randomMurderer = r.nextInt(players.length);
		int randomWeapon = r.nextInt(players.length);

		if (randomMurderer != randomWeapon) {
			playersTeam.put(((Player) players[randomMurderer]).getName(), "murderer");
			playersTeam.put(((Player) players[randomWeapon]).getName(), "bystanderWeapon");

			for (int i = 0; i < players.length; i++) {
				if (i != randomMurderer && i != randomWeapon) {
					playersTeam.put(((Player) players[i]).getName(), "bystander");
				}
			}
		} else {
			chooseMurdererAndBystanderWithWeapon(mapId);
		}
	}

	/**
	 * A player joined the arena. Start the countdown (if possible) and put him/her in a team
	 *
	 * @param p     Player which joined
	 * @param mapId Which map / arena he/she joined
	 */
	public void playerJoinedArena(final Player p, final int mapId) {
		if (playersList.contains(p)) {
			// Player is already in game
			p.sendMessage(Texts.PRE_TEXT + Texts.GAME_ALREADY_INGAME);
			return;
		}
		if (getPlayerInArena(mapId).size() > maxPlayers - 1) {
			// Arena full
			p.sendMessage(Texts.PRE_TEXT + Texts.GAME_ARENA_FULL);
			return;
		}

		// Prepare player
		playersList.add(p);
		playersMap.put(p.getName(), mapId);
		reloadTime.put(p.getName(), System.currentTimeMillis() - 5000);
		p.getInventory().clear();
		p.setAllowFlight(false);
		p.setGameMode(GameMode.ADVENTURE);
		p.teleport(randomSpawn(mapId));

		// Announce the new player
		for (Player player : getPlayerInArena(mapId)) {
			player.sendMessage(Texts.PRE_TEXT + p.getDisplayName() + Texts.GAME_PLAYER_JOINED);
		}

		// Not enough players to start the countdown
		if (getPlayerInArena(mapId).size() < 2) {
			p.sendMessage(Texts.PRE_TEXT + Texts.GAME_NOT_ENOUGH_PLAYERS);
			return;
		}

		if (countdown.get(mapId) != -1) {
			return;
		}

		countdown.put(mapId, 30);
	}

	/**
	 * Load data from the config-file
	 */
	public void loadConfig() {
		saveDefaultConfig();
		reloadConfig();
		gameWorldPrefix = getConfig().getString("gameWorldPrefix");

		updateSigns.clear();
		arenaConfig.clear();
		for (int i = 1; i < 100; i++) {
			List<Double> sign = getConfig().getDoubleList("maps." + i + ".sign");
			if (!sign.isEmpty()) {
				World w = Bukkit.getWorld(getConfig().getString("maps." + i + ".signWorld"));
				updateSigns.put(i, w.getBlockAt(new Location(w, sign.get(0), sign.get(1), sign.get(2))));
			}

			arenaConfig.put(i, getConfig().getBoolean("maps." + i + ".enabled"));
		}
	}

	/**
	 * A player died or the game started: He/She needs stuff and a teleport
	 *
	 * @param p Player which will be prepared
	 */
	private void preparePlayer(final Player p) {
		getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
			p.setAllowFlight(false);
			p.setGameMode(GameMode.ADVENTURE);
			p.setAllowFlight(false);
			p.setHealth(20);
			p.setFoodLevel(20);
			p.setLevel(0);
			p.teleport(randomSpawn(getPlayersArena(p)));
			p.getInventory().clear();

			playersAlive.put(p.getName(), true);
			reloadTime.put(p.getName(), System.currentTimeMillis() - 5000);

			if (!getGameStarted(getPlayersArena(p))) {
				return;
			}

			if (playerIsMurderer(p)) {
				p.getInventory().setHeldItemSlot(1);
				List<String> lsKnife = new ArrayList<>();
				lsKnife.add(Texts.GAME_HOWTO_KNIFE);
				p.getInventory().addItem(setName(new ItemStack(Material.STONE_AXE, 1), "§2Knife", lsKnife, 1));
			} else if (playerIsBystanderWithWeapon(p)) {
				p.getInventory().setHeldItemSlot(1);
				List<String> lsWeapon = new ArrayList<>();
				lsWeapon.add(Texts.GAME_HOWTO_WEAPON);
				lsWeapon.add(Texts.GAME_RELOAD_WEAPON + "5s");
				p.getInventory().addItem(setName(new ItemStack(Material.WOOD_HOE, 1), "§2Gun", lsWeapon, 1));
			} else {
				p.getInventory().setHeldItemSlot(0);
			}
		});
	}

	/**
	 * Remove player from arena and teleport him/her back to the lobby
	 *
	 * @param p Which player will be removed
	 */
	public void playerLeave(Player p) {
		int oldArena = getPlayersArena(p);
		if (oldArena == 0) return;

		playersList.remove(p);

		// Announce event
		for (Player player : getPlayerInArena(getPlayersArena(p))) {
			player.sendMessage(Texts.PRE_TEXT + p.getDisplayName() + Texts.GAME_PLAYER_LEFT);
			player.showPlayer(p);
		}

		for (PotionEffect effect : p.getActivePotionEffects()) {
			p.removePotionEffect(effect.getType());
		}

		// Does the game need to end?
		if (getPlayerInArena(oldArena).size() < 2 && getGameStarted(oldArena)) {
			for (Player player : getPlayerInArena(oldArena)) {
				player.sendMessage(Texts.PRE_TEXT + Texts.GAME_STOPPED);
			}
			stopGame(oldArena, true);
			return;
		}

		// Does the game need to end?
		if (getPlayerInArena(oldArena).size() >= 2 && getGameStarted(oldArena)) {
			if (playerIsMurderer(p)) {
				stopGame(oldArena, true);
			} else if (p.getInventory().contains(Material.WOOD_HOE)) {
				List<String> lsWeapon = new ArrayList<>();
				lsWeapon.add(Texts.GAME_HOWTO_WEAPON);
				lsWeapon.add(Texts.GAME_RELOAD_WEAPON + "5s");
				p.getWorld().dropItemNaturally(p.getLocation(), setName(new ItemStack(Material.WOOD_HOE, 1), "§2Gun", lsWeapon, 1));
				p.setItemInHand(new ItemStack(Material.AIR));
				reloadTime.put(p.getName(), System.currentTimeMillis());
			}
		}

		// Teleport player back and remove his/her stuff
		p.teleport(mcmSpawn());
		p.setAllowFlight(false);
		playersMap.remove(p.getName());
		playersTeam.remove(p.getName());
		playersAlive.remove(p.getName());
		reloadTime.remove(p.getName());
		p.getInventory().clear();
		p.setExp(0);
		p.setLevel(0);
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		// It's the knife! Throw it!
		if (getPlayerInGame(e.getPlayer()) && e.getPlayer().getItemInHand().getType() == Material.STONE_AXE && playerIsMurderer(e.getPlayer()) && getGameStarted(getPlayersArena(e.getPlayer()))) {
			if (e.getAction() == Action.RIGHT_CLICK_AIR || (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() != Material.WOOD_BUTTON)) {
				if (e.getPlayer().getItemInHand().hasItemMeta() && e.getPlayer().getItemInHand().getItemMeta().getDisplayName().equals("§2Knife")) {
					final Player p = e.getPlayer();

					ItemStack playersAxe = e.getPlayer().getItemInHand();
					Location l = p.getEyeLocation().subtract(0, 0.4, 0);
					final Item i = p.getWorld().dropItem(l, playersAxe);
					i.setVelocity(p.getLocation().getDirection().multiply(1.2));
					p.setItemInHand(new ItemStack(Material.AIR));

					BukkitRunnable runnable = new BukkitRunnable() {
						@Override
						public void run() {
							if (i.isOnGround()) {
								i.getWorld().playSound(i.getLocation(), Sound.BLOCK_ANVIL_LAND, (float) 0.1, (float) -5);
								cancel();
								return;
							}

							if (!i.getNearbyEntities(1.5D, 1.5D, 1.5D).isEmpty()) {
								for (Player victim : getPlayerInArena(getPlayersArena(p))) {
									if (victim.getLocation().distance(i.getLocation()) <= 1.5D && !playerIsMurderer(victim) && playerIsAlive(victim) && getGameStarted(getPlayersArena(victim))) {
										if (victim.getInventory().contains(Material.WOOD_HOE)) {
											List<String> lsWeapon = new ArrayList<>();
											lsWeapon.add(Texts.GAME_HOWTO_WEAPON);
											lsWeapon.add(Texts.GAME_RELOAD_WEAPON + "5s");
											victim.getWorld().dropItemNaturally(victim.getLocation(), setName(new ItemStack(Material.WOOD_HOE, 1), "§2Gun", lsWeapon, 1));
											victim.setItemInHand(new ItemStack(Material.AIR));
										}

										p.playSound(victim.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 3, 1);
										victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 3, 1);
										victim.sendMessage(Texts.PRE_TEXT + Texts.GAME_DEATH);
										playerDeath(victim);

										// Last one alive?
										int playersAlive = 0;
										for (Player players : getPlayerInArena(getPlayersArena(p))) {
											if (playerIsAlive(players)) playersAlive++;
										}
										if (playersAlive < 2) {
											for (Player p : getPlayerInArena(getPlayersArena(victim))) {
												p.sendMessage(Texts.PRE_TEXT + Texts.GAME_MURDERER_WINS);
												p.playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 3, 1);
											}
											stopGame(getPlayersArena(p), true);
										}
										i.setVelocity(p.getLocation().getDirection().zero());
										cancel();
									}
								}
							}
						}
					};
					runnable.runTaskTimer(this, 1, 1);
					p.getWorld().playSound(e.getPlayer().getLocation(), Sound.ENTITY_ENDERDRAGON_HURT, (float) 0.5, 1);

					BukkitRunnable runnableWeaponBack = new BukkitRunnable() {
						int ticksUntilWeaponBack = 20 * 20;

						@Override
						public void run() {
							if (ticksUntilWeaponBack == 0) {
								if (!p.getInventory().contains(Material.STONE_AXE) && getGameStarted(getPlayersArena(p)) && playerIsAlive(p)) {
									p.getInventory().setHeldItemSlot(1);
									List<String> lsKnife = new ArrayList<>();
									lsKnife.add(Texts.GAME_HOWTO_KNIFE);
									p.getInventory().addItem(setName(new ItemStack(Material.STONE_AXE, 1), "§2Knife", lsKnife, 1));

									List<Entity> entList = p.getWorld().getEntities();
									for (Entity current : entList) {
										if (current instanceof Item && ((Item) current).getItemStack().getType() == Material.STONE_AXE) {
											current.remove();
										}
									}
								}
								cancel();
							} else {
								ticksUntilWeaponBack--;
							}
						}
					};
					runnableWeaponBack.runTaskTimer(this, 0, 1);
				}
			}
		}

		// It's the gun! Throw the bullet!
		if (getPlayerInGame(e.getPlayer()) && e.getPlayer().getItemInHand().getType() == Material.WOOD_HOE && getGameStarted(getPlayersArena(e.getPlayer()))) {
			if (e.getAction() == Action.RIGHT_CLICK_AIR || (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() != Material.WOOD_BUTTON)) {
				if (e.getPlayer().getItemInHand().hasItemMeta() && e.getPlayer().getItemInHand().getItemMeta().getDisplayName().equals("§2Gun")) {
					Player p = e.getPlayer();
					if (System.currentTimeMillis() - (reloadTime.get(e.getPlayer().getName())) >= 5000) {
						Vector vec = p.getLocation().getDirection().multiply(5);
						Snowball ball = p.getWorld().spawn(p.getEyeLocation(), Snowball.class);
						ball.setShooter(p);
						ball.setVelocity(vec);

						e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ENTITY_ENDERDRAGON_HURT, 1, 1);
						reloadTime.put(p.getName(), System.currentTimeMillis());
					} else {
						p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
					}
				}
			}
		}

		// Do not allow anything else except WOOD_BUTTON
		if (getPlayerInGame(e.getPlayer())) {
			// Its a wooden button!
			if (!(e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.WOOD_BUTTON)) {
				e.setCancelled(true);
			}
		}

		// Player clicked a block. If this is a join-sign, join!
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && isSign(e.getClickedBlock())) {
			Sign theSign = (Sign) e.getClickedBlock().getState();
			if (theSign.getLine(0).equals(ChatColor.GREEN + "[Murder]")) {
				for (int i = 1; i < 100; i++) {
					if (theSign.getLine(3).startsWith(ChatColor.BLUE + "A" + i + ": ")) {
						e.getPlayer().performCommand("mcm join " + i);
					}
				}

				// Teleport player to the lobby
				if (theSign.getLine(2).equals(ChatColor.DARK_GREEN + "--> Lobby <--")) {
					if (getPlayerInGame(e.getPlayer())) {
						playersList.remove(e.getPlayer());
						playersTeam.remove(e.getPlayer().getName());
						playersMap.remove(e.getPlayer().getName());
					}
					e.getPlayer().performCommand("mcm lobby");

					for (Player player : getPlayerInArena(getPlayersArena(e.getPlayer()))) {
						player.sendMessage(Texts.PRE_TEXT + "§6" + e.getPlayer().getDisplayName() + Texts.GAME_PLAYER_LEFT);
					}
				}
			}
		}
	}

	@EventHandler
	public void onHit(EntityDamageByEntityEvent e) {
		// Killed by murderer
		if (e.getEntity() instanceof Player && e.getDamager() instanceof Player && getPlayerInGame((Player) e.getEntity()) && getPlayerInGame((Player) e.getDamager())) {
			Player victim = (Player) e.getEntity();
			Player damager = (Player) e.getDamager();

			if (playerIsMurderer(damager) && damager.getItemInHand().getType() == Material.STONE_AXE && playerIsAlive(victim)) {
				if (victim.getInventory().contains(Material.WOOD_HOE)) {
					List<String> lsWeapon = new ArrayList<>();
					lsWeapon.add(Texts.GAME_HOWTO_WEAPON);
					lsWeapon.add(Texts.GAME_RELOAD_WEAPON + "5s");
					victim.getWorld().dropItemNaturally(victim.getLocation(), setName(new ItemStack(Material.WOOD_HOE, 1), "§2Gun", lsWeapon, 1));
					victim.setItemInHand(new ItemStack(Material.AIR));
				}

				damager.playSound(victim.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 3, 1);
				victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 3, 1);
				victim.sendMessage(Texts.PRE_TEXT + Texts.GAME_DEATH);
				playerDeath(victim);

				// Last one alive?
				int playersAlive = 0;
				for (Player players : getPlayerInArena(getPlayersArena(damager))) {
					if (playerIsAlive(players)) playersAlive++;
				}
				if (playersAlive < 2) {
					for (Player p : getPlayerInArena(getPlayersArena(victim))) {
						p.sendMessage(Texts.PRE_TEXT + Texts.GAME_MURDERER_WINS);
						p.playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 3, 1);
					}
					stopGame(getPlayersArena(damager), true);
				}
			}
			e.setCancelled(true);
			return;
		}

		if (e.getDamager() instanceof Snowball && e.getEntity() instanceof Player && getPlayerInGame((Player) e.getEntity())) {
			// A player shot you
			final Player damager = (Player) ((Snowball) e.getDamager()).getShooter();
			final Player victim = (Player) e.getEntity();

			if (playerIsMurderer(victim)) {
				// Killed the murderer!
				for (Player p : getPlayerInArena(getPlayersArena(victim))) {
					p.sendMessage(Texts.PRE_TEXT + Texts.GAME_KILLED_MURDERER.replace("%killer", damager.getDisplayName()));
					p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 3, 1);
				}
				victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 3, 1);
				playerDeath(victim);
				stopGame(getPlayersArena(victim), true);
			} else if ((playerIsBystander(victim) || playerIsBystanderWithWeapon(victim)) && playerIsAlive(victim)) {
				// Killed a bystander...
				damager.sendMessage(Texts.PRE_TEXT + Texts.GAME_KILLED_BYSTANDER);
				victim.sendMessage(Texts.PRE_TEXT + Texts.GAME_KILLED_BY_BYSTANDER);

				if (damager.getInventory().contains(Material.WOOD_HOE)) {
					List<String> lsWeapon = new ArrayList<>();
					lsWeapon.add(Texts.GAME_HOWTO_WEAPON);
					lsWeapon.add(Texts.GAME_RELOAD_WEAPON + "5s");
					damager.getWorld().dropItemNaturally(damager.getLocation(), setName(new ItemStack(Material.WOOD_HOE, 1), "§2Gun", lsWeapon, 1));
					damager.setItemInHand(new ItemStack(Material.AIR));
					reloadTime.put(damager.getName(), System.currentTimeMillis());
				}

				victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 3, 1);
				damager.playSound(damager.getLocation(), Sound.ENTITY_GHAST_HURT, 3, 1);

				damager.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 50));
				damager.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 20, 1));

				playerDeath(victim);
			}
			e.setCancelled(true);
		}
	}

	private void playerDeath(Player p) {
		p.getLocation().getWorld().playEffect(p.getLocation(), Effect.STEP_SOUND, Material.REDSTONE_WIRE);

		playersAlive.put(p.getName(), false);
		p.getInventory().clear();
		p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 20, 50));
		p.removePotionEffect(PotionEffectType.SLOW);
		p.setAllowFlight(true);

		for (Player playersInMap : getPlayerInArena(getPlayersArena(p))) {
			playersInMap.hidePlayer(p);
		}
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player && getPlayerInGame((Player) e.getEntity())) {
			Player p = (Player) e.getEntity();
			// No fall-damage, suicide or drowning while in-arena but not in-game!
			if ((e.getCause().equals(EntityDamageEvent.DamageCause.FALL) || e.getCause().equals(EntityDamageEvent.DamageCause.SUICIDE) || e.getCause().equals(EntityDamageEvent.DamageCause.DROWNING) || e.getCause().equals(EntityDamageEvent.DamageCause.FIRE) || e.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK)) && (countdown.get(getPlayersArena(p)) != -1 || getPlayerInArena(getPlayersArena(p)).size() == 1) || !playerIsAlive(p)) {
				e.setCancelled(true);
			} else if ((e.getCause().equals(EntityDamageEvent.DamageCause.FALL) || e.getCause().equals(EntityDamageEvent.DamageCause.SUICIDE) || e.getCause().equals(EntityDamageEvent.DamageCause.DROWNING) || e.getCause().equals(EntityDamageEvent.DamageCause.FIRE) || e.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK)) && playerIsAlive(p)) {
				if ((p.getHealth() - e.getDamage()) <= 0) {
					if (playerIsMurderer(p)) {
						for (Player players : getPlayerInArena(getPlayersArena(p))) {
							players.sendMessage(Texts.PRE_TEXT + Texts.GAME_KILLED_MURDERER.replace("%killer", e.getCause().toString()));
							players.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 3, 1);
						}
						stopGame(getPlayersArena(p), true);
					} else {
						if (p.getInventory().contains(Material.WOOD_HOE)) {
							List<String> lsWeapon = new ArrayList<>();
							lsWeapon.add(Texts.GAME_HOWTO_WEAPON);
							lsWeapon.add(Texts.GAME_RELOAD_WEAPON + "5s");
							p.getWorld().dropItemNaturally(p.getLocation(), setName(new ItemStack(Material.WOOD_HOE, 1), "§2Gun", lsWeapon, 1));
							p.setItemInHand(new ItemStack(Material.AIR));
							reloadTime.put(p.getName(), System.currentTimeMillis());
						}
						p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 3, 1);
						p.sendMessage(Texts.PRE_TEXT + Texts.GAME_KILLED_BY_UNKNOWN);
						playerDeath(p);

						// Last one alive?
						int playersAlive = 0;
						for (Player players : getPlayerInArena(getPlayersArena(p))) {
							if (playerIsAlive(players)) playersAlive++;
						}
						if (playersAlive < 2) {
							for (Player players : getPlayerInArena(getPlayersArena(p))) {
								players.sendMessage(Texts.PRE_TEXT + Texts.GAME_MURDERER_WINS);
								players.playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 3, 1);
							}
							stopGame(getPlayersArena(p), true);
						}
					}
					p.setHealth(20);
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		playerLeave(p);
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent e) {
		// No block-breaking in-game!
		if (getPlayerInGame(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler
	public void playerDropItem(PlayerDropItemEvent e) {
		// Why would you want to drop your weapons?!
		if (getPlayerInGame(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler
	public void playerPickupItem(PlayerPickupItemEvent e) {
		// The Murderer is not allowed to pick up the gun + he is the only one who is allowed to pick up the knife
		if (getPlayerInGame(e.getPlayer())) {
			if (e.getItem().getItemStack().getType() == Material.STONE_AXE && playerIsAlive(e.getPlayer()) && playerIsMurderer(e.getPlayer())) {
				e.setCancelled(false);
			} else if (e.getItem().getItemStack().getType() == Material.WOOD_HOE && playerIsAlive(e.getPlayer()) && (playerIsBystander(e.getPlayer()) || playerIsBystanderWithWeapon(e.getPlayer())) && System.currentTimeMillis() - (reloadTime.get(e.getPlayer().getName())) >= 20000) {
				e.setCancelled(false);
			} else {
				e.setCancelled(true);
			}
		}
	}

	/**
	 * Manages the countdowns in every arena
	 */
	private void countdown() {
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			for (int i = 1; i < 100; i++) {
				if (countdown.get(i) != -1) {
					if (countdown.get(i) != 0) {
						if (getPlayerInArena(i).size() < 2) {
							for (Player players : getPlayerInArena(i)) {
								players.sendMessage(Texts.PRE_TEXT + Texts.GAME_START_STOPPED);
								players.setLevel(0);
							}
							countdown.put(i, -1);
							return;
						}

						if (countdown.get(i) < 6) {
							for (Player players : getPlayerInArena(i)) {
								players.playSound(players.getLocation(), Sound.UI_BUTTON_CLICK, 3, 1);
							}
						}

						for (Player players : getPlayerInArena(i)) {
							players.setLevel(countdown.get(i));
						}
						countdown.put(i, countdown.get(i) - 1);
					} else {
						countdown.put(i, countdown.get(i) - 1);
						if (getPlayerInArena(i).size() < 2) {
							for (Player players : getPlayerInArena(i)) {
								players.sendMessage(Texts.PRE_TEXT + Texts.GAME_START_STOPPED);
								players.setLevel(countdown.get(0));
							}
							countdown.put(i, -1);
							return;
						}
						startGame(i);
					}
				}
			}
		}, 0, 20L);
	}

	/**
	 * Shows the time until player is able to shoot again in the exp-bar
	 */
	private void reloadTime() {
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			for (Player p : playersList) {
				long time = System.currentTimeMillis() - reloadTime.get(p.getName());
				if (time < 500) {
					p.setExp((float) 1);
				} else if (time < 1000) {
					p.setExp((float) 0.9);
				} else if (time < 1500) {
					p.setExp((float) 0.8);
				} else if (time < 2000) {
					p.setExp((float) 0.7);
				} else if (time < 2500) {
					p.setExp((float) 0.6);
				} else if (time < 3000) {
					p.setExp((float) 0.5);
				} else if (time < 3500) {
					p.setExp((float) 0.4);
				} else if (time < 4000) {
					p.setExp((float) 0.3);
				} else if (time < 4500) {
					p.setExp((float) 0.2);
				} else if (time < 5000) {
					p.setExp((float) 0.1);
				} else {
					p.setExp((float) 0.0);
				}
			}
		}, 0, 1);
	}

	/**
	 * Updates the signs every half second and shows game-status and player-count
	 */
	private void updateSigns() {
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			for (Map.Entry<Integer, Block> b : updateSigns.entrySet()) {
				if (isSign(b.getValue())) {
					// Load chunk, prevent NullPointerExceptions
					b.getValue().getChunk().load();

					Sign mySign;
					try {
						mySign = (Sign) b.getValue().getState();
					} catch (NullPointerException e) {
						Bukkit.getLogger().warning("Signs' chunk not loaded! Will update later...");
						continue;
					}

					if (!arenaConfig.get(b.getKey())) {
						mySign.setLine(1, ChatColor.RED + "Disabled");
						mySign.setLine(2, ChatColor.RED + "0 / " + maxPlayers);
						mySign.update();
					} else {
						mySign.setLine(1, getArenaStatus(b.getKey()));
						mySign.setLine(2, ChatColor.RED + "" + getPlayerInArena(b.getKey()).size() + " / " + maxPlayers);
						mySign.update();
					}
				}
			}
		}, 0, 10L);
	}

	@EventHandler
	public void onSignChange(SignChangeEvent event) {
		// A Murder-sign? Change and save it!
		if (event.getLine(0).trim().equalsIgnoreCase("Murder")) {
			for (int i = 1; i < 100; i++) {
				if (event.getLine(2).equalsIgnoreCase("mcm join " + i) && event.getPlayer().isOp()) {
					event.setLine(0, ChatColor.GREEN + "[Murder]");
					event.setLine(1, "");
					event.setLine(2, ChatColor.RED + "0  / " + maxPlayers);
					event.setLine(3, ChatColor.BLUE + "A" + i + ": " + event.getLine(3));
					List<Double> listPosition = Arrays.asList(event.getBlock().getLocation().getX(), event.getBlock().getLocation().getY(), event.getBlock().getLocation().getZ());
					getConfig().set("maps." + i + ".signWorld", event.getPlayer().getWorld().getName());
					getConfig().set("maps." + i + ".sign", listPosition);
					saveConfig();
					loadConfig();
				}
			}
		}

		// A Murder-lobby-sign? Change and save it!
		if (event.getLine(0).equalsIgnoreCase("Murder") && event.getLine(2).equalsIgnoreCase("mcm lobby") && event.getPlayer().isOp()) {
			event.setLine(0, ChatColor.GREEN + "[Murder]");
			event.setLine(2, ChatColor.DARK_GREEN + "--> Lobby <--");
		}
	}

	/**
	 * Create an ItemStack with more information
	 *
	 * @param is     New ItemStack
	 * @param name   Name of item
	 * @param lore   Sub-titles of item
	 * @param amount Amount of items
	 * @return Custom ItemStack
	 */
	private ItemStack setName(ItemStack is, String name, List<String> lore, int amount) {
		ItemMeta im = is.getItemMeta();
		if (name != null)
			im.setDisplayName(name);
		if (lore != null)
			im.setLore(lore);
		is.setItemMeta(im);
		is.setAmount(amount);
		return is;
	}

	/**
	 * Is this block a sign?
	 *
	 * @param theBlock Block to check
	 * @return Is this block a sign
	 */
	private boolean isSign(Block theBlock) {
		return theBlock.getType() == Material.SIGN || theBlock.getType() == Material.SIGN_POST || theBlock.getType() == Material.WALL_SIGN;
	}

	/**
	 * Get a random spawn of an arena
	 *
	 * @param mapId Which map / arena
	 * @return A random spawn
	 */
	private Location randomSpawn(int mapId) {
		Random r = new Random();
		int random = r.nextInt(8) + 1;
		List<Double> spawns = this.getConfig().getDoubleList("maps." + mapId + "." + random);
		return new Location(getServer().getWorld(gameWorldPrefix + mapId), spawns.get(0), spawns.get(1), spawns.get(2));
	}

	/**
	 * Get the lobby-location
	 *
	 * @return Lobby-location
	 */
	public Location mcmSpawn() {
		List<Double> spawn = getConfig().getDoubleList("lobbySpawn");
		return new Location(Bukkit.getWorld(getConfig().getString("lobbyWorld")), spawn.get(0), spawn.get(1), spawn.get(2));
	}

	/**
	 * Did the game start?
	 *
	 * @param mapId Which map / arena will be checked
	 * @return If the game already started or not
	 */
	public boolean getGameStarted(int mapId) {
		return gameStarted.get(mapId);
	}

	/**
	 * Get a list of every player in an arena
	 *
	 * @param map Which map / arena will be checked
	 * @return A list of every player in the arena
	 */
	public List<Player> getPlayerInArena(int map) {
		List<Player> myPlayers = new ArrayList<>();
		for (Player p : playersList) {
			if (playersMap.get(p.getName()).equals(map)) {
				myPlayers.add(p);
			}
		}
		return myPlayers;
	}

	/**
	 * Get the arena of a specific player
	 *
	 * @param p Which player will be checked
	 * @return Arena of the player or 0 if there is none
	 */
	public int getPlayersArena(Player p) {
		if (playersMap.containsKey(p.getName())) {
			return playersMap.get(p.getName());
		}
		return 0;
	}

	/**
	 * Is the player currently in-game?
	 *
	 * @param p Which player will be checked
	 * @return If the player is currently in-game
	 */
	public boolean getPlayerInGame(Player p) {
		return playersList.contains(p);
	}

	/**
	 * Is the player the murderer?
	 *
	 * @param p Which player will be checked
	 * @return If the player is the murderer
	 */
	private boolean playerIsMurderer(Player p) {
		return playersTeam.containsKey(p.getName()) && playersTeam.get(p.getName()).equals("murderer");
	}

	/**
	 * Is the player a bystander?
	 *
	 * @param p Which player will be checked
	 * @return If the player is a bystander
	 */
	private boolean playerIsBystander(Player p) {
		return playersTeam.containsKey(p.getName()) && playersTeam.get(p.getName()).equals("bystander");
	}

	/**
	 * Is the player the bystander with a secret weapon?
	 *
	 * @param p Which player will be checked
	 * @return If the player is the bystander with a secret weapon
	 */
	private boolean playerIsBystanderWithWeapon(Player p) {
		return playersTeam.containsKey(p.getName()) && playersTeam.get(p.getName()).equals("bystanderWeapon");
	}

	/**
	 * Is the player alive?
	 *
	 * @param p Which player will be checked
	 * @return If the player is alive
	 */
	private boolean playerIsAlive(Player p) {
		return playersAlive.containsKey(p.getName()) && playersAlive.get(p.getName());
	}

	/**
	 * Get the current game-status of an arena
	 *
	 * @param mapId Which map / arena will be checked
	 * @return State of game
	 */
	private String getArenaStatus(int mapId) {
		if (getGameStarted(mapId)) {
			return ChatColor.RED + "In-Game";
		}
		if (countdown.get(mapId) == -1) {
			return ChatColor.GREEN + "Waiting";
		}
		if (countdown.get(mapId) != -1) {
			return ChatColor.DARK_GREEN + "Countdown";
		}
		return "unknown";
	}
}