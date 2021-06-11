# MC-Murder
MC-Murder is a Minecraft (Bukkit/Spigot) Mini-Game. You have to find and kill the Murderer among every Player in your game. But if you are the Murderer: kill everyone!

## How To Play
If you know how to play "Garry's Mod: Murder", you know how to play this. Otherwise here a short introduction:

* **Bystander**: Find out who the Murderer is an work together with everyone else.
* **Bystander with a Weapon**: You have to kill the Murderer before he kills you. If you are dead, anyone else (except the Murderer) is able to pickup the weapon.
* **Murderer**: Kill everyone, but don't get caught!

## Setup
Follow these steps to setup and configure "MC-Murder" on your server:

1. Download the plugin: Grab the current release from [here](https://github.com/menzerath/spigot-murder/releases) and put it in your plugins-folder.
2. Download Multiverse: Grab the current release from [here](https://dev.bukkit.org/projects/multiverse-core) and put it in your plugins-folder.
3. Upload the maps: Upload the maps you want to play on and name them `mcm-wX`, where "X" has to be a unique number. This will be the id of the arena. Example: `mcm-w1`
This can be changed in the config-file (see `gameWorldPrefix`).
4. Now start your server and import the worlds. Example: `/mvimport mcm-w1 normal`  After you did this for every arena, edit the Multiverse-world-config and change the following parameters (if you want to):
  * `allowweather: false`
  * `difficulty: PEACEFUL`
  * `animals:
      spawn: false`
  * `monsters:
      spawn: false`
  * `hunger: false`
5. Reload the Multiverse-Config: `/mvreload`
6. Set a MCM-Spawn: `/mcm setlobby` You will spawn there if you type `/mcm lobby`.
7. Configure the first arena:
  1. Teleport there: `/mvtp mcm-w1`
  2. Now set every spawn (exactly 8!): Go to the place you want the players to spawn and type in `/mcm setspawn [MAP] [SPAWN]`. Example: `/mcm setspawn 1 1`
  3. Enable the arena: `/mcm enable 1`.
  4. Place a sign to join the arena (every new line represents a line of the sign):
    1. `Murder`
    2. `__EMPTY__`
    3. `mcm join [ARENA-ID]`
    4. `[MAP-NAME]`

## Commands

### User
* Teleport to the lobby: `/mcm lobby`
* Join an arena: `/mcm join [ARENA]`
* Leave an arena: `/mcm leave`

### Admin / OP
* Set lobby-spawn: `/mcm setLobby`
* Set arena-spawn: `/mcm setSpawn [ARENA] [SPAWN]`
* Enable / Disable arena: `/mcm enable/disable [ARENA]`
* Reload config: `/mcm reload`
