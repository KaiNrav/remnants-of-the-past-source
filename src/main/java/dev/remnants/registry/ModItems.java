package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.item.ArsenalItem;
import dev.remnants.item.BoneChakramItem;
import dev.remnants.item.EpitaphItem;
import dev.remnants.item.SoulDowserItem;
import dev.remnants.item.LoreBlockItem;
import dev.remnants.item.LoreItem;
import dev.remnants.item.RemnantCrownItem;
import dev.remnants.item.SoulCodexItem;
import dev.remnants.item.SpectralCompassItem;
import dev.remnants.item.MournerArmorItem;
import dev.remnants.item.RitualHammerItem;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.EnumMap;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import dev.remnants.entity.ForgottenVariant;

public final class ModItems {
	public static final Item SOUL_CODEX = register("soul_codex",
		new SoulCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final Item SPECTRAL_COMPASS = register("spectral_compass",
		new SpectralCompassItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final Item SOUL_FRAGMENT = register("soul_fragment",
		new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), 1));
	public static final Item CONDENSED_SOUL = register("condensed_soul",
		new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 1));
	public static final Item REMNANT_CROWN = register("remnant_crown",
		new RemnantCrownItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
	public static final Item SOUL_KING_HEART = register("soul_king_heart",
		new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), 1));
	public static final Item SOUL_WARD = register("soul_ward",
		new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 2));
	public static final Item PHYLACTERY = register("phylactery",
		new LoreItem(new Item.Properties().durability(3).rarity(Rarity.EPIC).fireResistant(), 2));
	public static final Item SOUL_KING_ARSENAL = register("soul_king_arsenal",
		new ArsenalItem(new Item.Properties().durability(2500).rarity(Rarity.EPIC).fireResistant()));

	// ---- 1.2 graves
	public static final Item SOUL_DOWSER = register("soul_dowser",
		new SoulDowserItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final Item BONE_CHAKRAM = register("bone_chakram",
		new BoneChakramItem(new Item.Properties().durability(384).rarity(Rarity.UNCOMMON)));
	public static final Item EPITAPH = register("epitaph",
		new EpitaphItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final Item WET_SHROUD = register("wet_shroud", new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), 1));
	public static final Item SOUL_FROST = register("soul_frost", new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), 1));
	public static final Item SCARAB_SEAL = register("scarab_seal", new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), 1));

	// ---- 1.3 craft
	public static final Item RITUAL_HAMMER = register("ritual_hammer",
		new RitualHammerItem(new Item.Properties().durability(256).rarity(Rarity.UNCOMMON)));
	/** Mourner's set: between iron (15 armour, no toughness) and diamond (20, toughness 2): 17 armour, toughness 1. */
	public static final Holder<ArmorMaterial> MOURNER_MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
		RemnantsMod.id("mourner"), new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
			m.put(ArmorItem.Type.BOOTS, 2);
			m.put(ArmorItem.Type.LEGGINGS, 6);
			m.put(ArmorItem.Type.CHESTPLATE, 7);
			m.put(ArmorItem.Type.HELMET, 2);
			m.put(ArmorItem.Type.BODY, 7);
		}), 15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(WET_SHROUD),
			List.of(new ArmorMaterial.Layer(RemnantsMod.id("mourner"))), 1.0F, 0.0F));
	public static final Item MOURNER_HOOD = register("mourner_hood", new MournerArmorItem(MOURNER_MATERIAL, ArmorItem.Type.HELMET,
		new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(25)).rarity(Rarity.RARE)));
	public static final Item MOURNER_ROBE = register("mourner_robe", new MournerArmorItem(MOURNER_MATERIAL, ArmorItem.Type.CHESTPLATE,
		new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(25)).rarity(Rarity.RARE)));
	public static final Item MOURNER_LEGGINGS = register("mourner_leggings", new MournerArmorItem(MOURNER_MATERIAL, ArmorItem.Type.LEGGINGS,
		new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(25)).rarity(Rarity.RARE)));
	public static final Item MOURNER_BOOTS = register("mourner_boots", new MournerArmorItem(MOURNER_MATERIAL, ArmorItem.Type.BOOTS,
		new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(25)).rarity(Rarity.RARE)));

	// ---- 1.4 crypts
	public static final Item HAND_BELL = register("hand_bell",
		new dev.remnants.item.HandBellItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final Item SOUL_TETHER = register("soul_tether",
		new dev.remnants.item.SoulTetherItem(new Item.Properties().durability(256).rarity(Rarity.UNCOMMON)));
	public static final Item OSSUARY_KEY = register("ossuary_key", new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), 2));
	public static final Item WARDENS_GAUNTLET = register("wardens_gauntlet",
		new dev.remnants.item.WardensGauntletItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
	public static final Item MIMIC_TONGUE = register("mimic_tongue", new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), 1));

	// ---- 1.5 archive
	public static final Item ARCHIVIST_QUILL = register("archivist_quill",
		new dev.remnants.item.ArchivistQuillItem(new Item.Properties().durability(4).rarity(Rarity.EPIC).fireResistant()));
	public static final Item SEAL_OF_MEMORY = register("seal_of_memory", new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
		.fireResistant(), 2));
	public static final Item SHROUD_GLIDER = register("shroud_glider",
		new dev.remnants.item.ShroudGliderItem(new Item.Properties().durability(320).rarity(Rarity.UNCOMMON)));
	public static final Item SOUL_LANTERN_TRAP = register("soul_lantern_trap",
		new dev.remnants.item.SoulLanternItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	public static final Item DECOY_ECHO = register("decoy_echo",
		new dev.remnants.item.DecoyEchoItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

	public static final Item SOUL_BRICKS = block(ModBlocks.SOUL_BRICKS, new Item.Properties());
	public static final Item SOUL_BRICK_STAIRS = block(ModBlocks.SOUL_BRICK_STAIRS, new Item.Properties());
	public static final Item SOUL_BRICK_SLAB = block(ModBlocks.SOUL_BRICK_SLAB, new Item.Properties());
	public static final Item SOUL_BRICK_WALL = block(ModBlocks.SOUL_BRICK_WALL, new Item.Properties());
	public static final Item CHISELED_SOUL_BRICKS = block(ModBlocks.CHISELED_SOUL_BRICKS, new Item.Properties());
	public static final Item SOUL_BRAZIER = block(ModBlocks.SOUL_BRAZIER, new Item.Properties());
	public static final Item SOUL_PEDESTAL = loreBlock(ModBlocks.SOUL_PEDESTAL, 1, new Item.Properties());
	public static final Item SPIRIT_ALTAR = loreBlock(ModBlocks.SPIRIT_ALTAR, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item SOUL_FORGE = loreBlock(ModBlocks.SOUL_FORGE, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item SOUL_ANCHOR = loreBlock(ModBlocks.SOUL_ANCHOR, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item BURIAL_CASKET = loreBlock(ModBlocks.BURIAL_CASKET, 1, new Item.Properties());
	public static final Item WEATHERED_GRAVESTONE = loreBlock(ModBlocks.WEATHERED_GRAVESTONE, 1, new Item.Properties());
	public static final Item BURIED_GRAVE = loreBlock(ModBlocks.BURIED_GRAVE, 1, new Item.Properties());
	public static final Item REFLECTING_BASIN = loreBlock(ModBlocks.REFLECTING_BASIN, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item SOUL_CRUCIBLE = loreBlock(ModBlocks.SOUL_CRUCIBLE, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item SOUL_HEARTH = loreBlock(ModBlocks.SOUL_HEARTH, 1, new Item.Properties());
	public static final Item SOUL_BELLOWS = loreBlock(ModBlocks.SOUL_BELLOWS, 1, new Item.Properties());
	public static final Item SOUL_TROUGH = loreBlock(ModBlocks.SOUL_TROUGH, 1, new Item.Properties());
	public static final Item SOUL_CHIMNEY = loreBlock(ModBlocks.SOUL_CHIMNEY, 1, new Item.Properties());
	public static final Item OSSUARY_URN = loreBlock(ModBlocks.OSSUARY_URN, 1, new Item.Properties());
	public static final Item OSSUARY_GATE = loreBlock(ModBlocks.OSSUARY_GATE, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item CRYPT_EYE = loreBlock(ModBlocks.CRYPT_EYE, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item REST_STONE = loreBlock(ModBlocks.REST_STONE, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item BELL_OF_REST = loreBlock(ModBlocks.BELL_OF_REST, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item CRACKED_BELL_OF_REST = loreBlock(ModBlocks.CRACKED_BELL_OF_REST, 1, new Item.Properties());

	public static final Item MEMORIAL_PROJECTOR = loreBlock(ModBlocks.MEMORIAL_PROJECTOR, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item EPITAPH_NICHE = loreBlock(ModBlocks.EPITAPH_NICHE, 2, new Item.Properties());
	public static final Item SOUL_ASTROLABE = loreBlock(ModBlocks.SOUL_ASTROLABE, 2, new Item.Properties().rarity(Rarity.RARE));
	public static final Item FOCUS_CRYSTAL = loreBlock(ModBlocks.FOCUS_CRYSTAL, 1, new Item.Properties());

	public static final Item REMNANT_SPAWN_EGG = register("remnant_spawn_egg",
		new SpawnEggItem(ModEntities.REMNANT, 0x1C1A36, 0x96EDF9, new Item.Properties()));
	public static final Item FORGOTTEN_REMNANT_SPAWN_EGG = register("forgotten_remnant_spawn_egg",
		new SpawnEggItem(ModEntities.FORGOTTEN_REMNANT, 0x302F52, 0xA39895, new Item.Properties()));
	public static final Item CRYPT_WARDEN_SPAWN_EGG = register("crypt_warden_spawn_egg",
		new SpawnEggItem(ModEntities.CRYPT_WARDEN, 0x4A4656, 0xC9A45C, new Item.Properties()));
	public static final Item GRAVE_MIMIC_SPAWN_EGG = register("grave_mimic_spawn_egg",
		new SpawnEggItem(ModEntities.GRAVE_MIMIC, 0x3B2A48, 0xD8CFBE, new Item.Properties()));
	public static final Item KEENER_SPAWN_EGG = register("keener_spawn_egg",
		new SpawnEggItem(ModEntities.KEENER, 0x5D4F7A, 0x9FE7EC, new Item.Properties()));
	public static final Item ARCHIVIST_SPAWN_EGG = register("archivist_spawn_egg",
		new SpawnEggItem(ModEntities.ARCHIVIST, 0x2A2240, 0xD8B46A, new Item.Properties()));
	public static final Item ARCHIVE_PAGE_SPAWN_EGG = register("archive_page_spawn_egg",
		new SpawnEggItem(ModEntities.ARCHIVE_PAGE, 0xD9CFB4, 0x5FC7CF, new Item.Properties()));
	public static final Item CARRION_CROW_SPAWN_EGG = register("carrion_crow_spawn_egg",
		new SpawnEggItem(ModEntities.CARRION_CROW, 0x16121C, 0xD8CFBE, new Item.Properties()));
	public static final Item SOUL_KING_SPAWN_EGG = register("soul_king_spawn_egg",
		new SpawnEggItem(ModEntities.SOUL_KING, 0x2D1F4C, 0xDCA44C, new Item.Properties()));

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, RemnantsMod.id("main"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(REMNANT_CROWN))
			.title(Component.translatable("itemGroup.remnants"))
			.displayItems((params, out) -> {
				out.accept(SOUL_CODEX);
				out.accept(SOUL_FRAGMENT);
				out.accept(CONDENSED_SOUL);
				out.accept(REMNANT_CROWN);
				out.accept(SOUL_KING_HEART);
				out.accept(SPECTRAL_COMPASS);
				out.accept(SOUL_WARD);
				out.accept(PHYLACTERY);
				out.accept(SOUL_DOWSER);
				out.accept(BONE_CHAKRAM);
				out.accept(EPITAPH);
				out.accept(WET_SHROUD);
				out.accept(SOUL_FROST);
				out.accept(SCARAB_SEAL);
				out.accept(SOUL_KING_ARSENAL);
				out.accept(RITUAL_HAMMER);
				out.accept(MOURNER_HOOD);
				out.accept(MOURNER_ROBE);
				out.accept(MOURNER_LEGGINGS);
				out.accept(MOURNER_BOOTS);
				out.accept(SOUL_BRICKS);
				out.accept(SOUL_BRICK_STAIRS);
				out.accept(SOUL_BRICK_SLAB);
				out.accept(SOUL_BRICK_WALL);
				out.accept(CHISELED_SOUL_BRICKS);
				out.accept(SOUL_BRAZIER);
				out.accept(SOUL_PEDESTAL);
				out.accept(SPIRIT_ALTAR);
				out.accept(SOUL_FORGE);
				out.accept(SOUL_ANCHOR);
				out.accept(WEATHERED_GRAVESTONE);
				out.accept(BURIAL_CASKET);
				out.accept(BURIED_GRAVE);
				out.accept(REFLECTING_BASIN);
				out.accept(SOUL_CRUCIBLE);
				out.accept(SOUL_HEARTH);
				out.accept(SOUL_BELLOWS);
				out.accept(SOUL_TROUGH);
				out.accept(SOUL_CHIMNEY);
				out.accept(HAND_BELL);
				out.accept(SOUL_TETHER);
				out.accept(OSSUARY_KEY);
				out.accept(WARDENS_GAUNTLET);
				out.accept(MIMIC_TONGUE);
				out.accept(OSSUARY_URN);
				out.accept(OSSUARY_GATE);
				out.accept(CRYPT_EYE);
				out.accept(REST_STONE);
				out.accept(BELL_OF_REST);
				out.accept(CRACKED_BELL_OF_REST);
				out.accept(ARCHIVIST_QUILL);
				out.accept(SEAL_OF_MEMORY);
				out.accept(SHROUD_GLIDER);
				out.accept(SOUL_LANTERN_TRAP);
				out.accept(DECOY_ECHO);
				out.accept(MEMORIAL_PROJECTOR);
				out.accept(EPITAPH_NICHE);
				out.accept(SOUL_ASTROLABE);
				out.accept(FOCUS_CRYSTAL);
				out.accept(REMNANT_SPAWN_EGG);
				out.accept(FORGOTTEN_REMNANT_SPAWN_EGG);
				for (ForgottenVariant v : new ForgottenVariant[]{ForgottenVariant.DROWNED, ForgottenVariant.FROZEN, ForgottenVariant.SANDY}) {
					out.accept(variantEgg(v));
				}
				out.accept(CRYPT_WARDEN_SPAWN_EGG);
				out.accept(GRAVE_MIMIC_SPAWN_EGG);
				out.accept(KEENER_SPAWN_EGG);
				out.accept(CARRION_CROW_SPAWN_EGG);
				out.accept(ARCHIVIST_SPAWN_EGG);
				out.accept(ARCHIVE_PAGE_SPAWN_EGG);
				out.accept(SOUL_KING_SPAWN_EGG);
			})
			.build());

	/** A Forgotten Remnant spawn egg for one biome kind (creative tab only): the variant rides in the entity data. */
	public static ItemStack variantEgg(ForgottenVariant variant) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "remnants_of_the_past:forgotten_remnant");
		tag.putString("Variant", variant.key());
		tag.putBoolean("VariantKit", false);
		ItemStack egg = new ItemStack(FORGOTTEN_REMNANT_SPAWN_EGG);
		egg.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
		egg.set(DataComponents.ITEM_NAME, Component.translatable("item.remnants_of_the_past.forgotten_remnant_spawn_egg." + variant.key()));
		return egg;
	}

	private static Item loreBlock(net.minecraft.world.level.block.Block block, int lines, Item.Properties props) {
		return register(BuiltInRegistries.BLOCK.getKey(block).getPath(), new LoreBlockItem(block, props, lines));
	}

	private static Item block(net.minecraft.world.level.block.Block block, Item.Properties props) {
		return register(BuiltInRegistries.BLOCK.getKey(block).getPath(), new BlockItem(block, props));
	}

	private static Item register(String name, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, RemnantsMod.id(name), item);
	}

	public static void init() {
	}

	private ModItems() {
	}
}
