<p align="center"><img src="logo.png" alt="Remnants of the Past" width="640"></p>

# Remnants of the Past

*Fabric · Minecraft 1.21.1 · requires Fabric API · English and Russian*

When you die, your gear doesn't hit the floor. It rises as a **Remnant**: a hostile echo of you, wearing your armor and
wielding your weapon, fighting with an adaptive moveset. Defeat it to get everything back. Die again before you do, and
the next one is stronger.

## Features
- **Remnants** hold your whole inventory **and all your experience** safely (no despawn timer, immune to lava and
  explosions). A Remnant kneels where you fell until you come close, so you choose when the fight starts.
- **An echo as strong as you were.** A naked death with a wooden sword leaves a *Faint Echo* you can beat with a
  wooden sword; enchanted netherite leaves a *Great Echo* with three phases, mirror decoys and a full moveset. Every
  strike is wound up before it lands, big moves leave it catching its breath, and a **poise** bar rewards pressure and
  perfect shield **parries** with a stagger. Every death in a row makes the next one tougher. 10% of the time one
  becomes **Empowered**: it reforges your weapon into a Spectral weapon (+1 to every enchantment) and leaves a
  **Remnant Crown**.
- **Path of the Soul.** Laying Remnants to rest earns Soul Resonance; five ranks unlock permanent gifts (respawn
  vengeance, soul bond, keep your weapon, survive a killing blow once a day, keep your armor).
- **The Soul Codex**, an in-game guide with a modern UI: chapters, recipes, live 3D portraits (your own ghost included)
  and your Path of the Soul progress. Handed out on first join.
- **Forgotten Remnants** wander at night and in the Soul Sand Valley: echoes of long-dead adventurers in worn,
  antique gear. They are the main source of **Soul Fragments**; rare Crowned ones carry a crown.
- **Forgotten Graves** (1.2): unmarked graves hide under the earth all over the overworld. A **Soul Dowser** clicks
  faster as you close in; dig one up and a **Burial Casket** climbs out of the hole with an old adventurer's belongings,
  sometimes their **Epitaph** - and sometimes its owner, who claws its way out of the ground beside you.
- **Echoes of every land** (1.2): Forgotten Remnants take on their biome - the **Drowned** hurl a chain harpoon, the
  **Frozen** wear an ice crust and breathe frost, the **Sandy** burrow and surface behind you. They all dig themselves out of
  the ground when they spawn.
- **Bone Chakram** (1.2): a returning throwing blade that cuts grass and cobwebs, presses buttons and brings the loot back;
  inlay it with a biome trophy for frost, a dragging tide or a ricochet. Every Remnant of yours also leaves the
  **Epitaph** of its death, drawn as a stone tablet.
- **The Soul King**, a three-headed, four-armed lich boss with crystal wards and three phases. Build the summoning circle:
  a Spirit Offering Table with four Soul Pedestals around it (empty spots show as glowing ghost pedestals), then offer a
  Condensed Soul.
- **The Soul King's Arsenal**: one weapon with four forms (sword, bow, trident, mace), each with its own special
  ability, drawn as chunky 3D models in hand.
- **Soul Forge**: raise any weapon's Spectral level on purpose. **Soul Anchor**: choose where your Remnant rises.
  **Soul Ward**: weakens your next Remnant. **Phylactery**: crafted from the King's Heart, it keeps your inventory
  through three deaths.
- A **Soul Brick** building set (stairs, slabs, walls, chiseled bricks, braziers), 17 advancements, and recipe-book
  unlocks for every recipe.

## Config
`config/remnants_of_the_past.json` (created on first launch): keepInventory behaviour, echo health range, ghost armor and weapon
shares, dormancy, poise, experience keeping, Soul Resonance, health and damage scaling, Empowered chance,
Forgotten Remnant spawn weights, drops and biome variants, grave rarity, casket owner and epitaph chances, chakram damage, Soul Anchor range, boss health and damage, and more.

## Compatibility
Built on Fabric API events; a few small client mixins (screen shake, which `screenShake: 0` turns off; custom boss bars; boss music) and one server mixin (explosions spare sealed crypt vaults). Mod id: `remnants_of_the_past`. Respects `keepInventory` unless configured otherwise. Totems of Undying still
work, and Curse of Vanishing still vanishes.


## License
MIT
