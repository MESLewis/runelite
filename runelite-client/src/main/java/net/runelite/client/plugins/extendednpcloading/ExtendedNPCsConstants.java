package net.runelite.client.plugins.extendednpcloading;

import com.google.common.collect.ImmutableSet;
import lombok.Data;
import net.runelite.api.NpcID;

import java.util.Set;

public class ExtendedNPCsConstants {
    public static final Set<Integer> IGNORED_NPCS = ImmutableSet.of(
            NpcID.BEE_KEEPER_6747,
            NpcID.CAPT_ARNAV,
            NpcID.DR_JEKYLL, NpcID.DR_JEKYLL_314,
            NpcID.DRUNKEN_DWARF,
            NpcID.DUNCE_6749,
            NpcID.EVIL_BOB, NpcID.EVIL_BOB_6754,
            NpcID.FLIPPA_6744,
            NpcID.FREAKY_FORESTER_6748,
            NpcID.FROG_5429, NpcID.FROG_5430, NpcID.FROG_5431, NpcID.FROG_5432, NpcID.FROG, NpcID.FROG_PRINCE, NpcID.FROG_PRINCESS,
            NpcID.GENIE, NpcID.GENIE_327,
            NpcID.GILES, NpcID.GILES_5441,
            NpcID.LEO_6746,
            NpcID.MILES, NpcID.MILES_5440,
            NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751,
            NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753,
            NpcID.NILES, NpcID.NILES_5439,
            NpcID.PILLORY_GUARD,
            NpcID.POSTIE_PETE_6738,
            NpcID.QUIZ_MASTER_6755,
            NpcID.RICK_TURPENTINE, NpcID.RICK_TURPENTINE_376,
            NpcID.SANDWICH_LADY,
            NpcID.SERGEANT_DAMIEN_6743,
            NpcID.STRANGE_PLANT,
            //Unpredictable spawns
            NpcID.IMP_5007,
            NpcID.ABYSSAL_DEMON,
            NpcID.ABYSSAL_DEMON_415,
            NpcID.ABYSSAL_DEMON_416,
            NpcID.ABYSSAL_DEMON_7241,
            NpcID.GREATER_ABYSSAL_DEMON,
            NpcID.ABYSSAL_DEMON_11239,
            NpcID.GREATER_ABYSSAL_DEMON_12451,
            NpcID.SPAWN,
            NpcID.SPAWN_5917,
            NpcID.SCION,
            NpcID.CHAOS_ELEMENTAL,
            NpcID.ABYSSAL_SIRE_5908,
            //Implings
            NpcID.BABY_IMPLING,
            NpcID.YOUNG_IMPLING,
            NpcID.GOURMET_IMPLING,
            NpcID.EARTH_IMPLING,
            NpcID.ESSENCE_IMPLING,
            NpcID.ECLECTIC_IMPLING,
            NpcID.NATURE_IMPLING,
            NpcID.MAGPIE_IMPLING,
            NpcID.NINJA_IMPLING,
            NpcID.DRAGON_IMPLING,
            NpcID.BABY_IMPLING_1645,
            NpcID.YOUNG_IMPLING_1646,
            NpcID.GOURMET_IMPLING_1647,
            NpcID.EARTH_IMPLING_1648,
            NpcID.ESSENCE_IMPLING_1649,
            NpcID.ECLECTIC_IMPLING_1650,
            NpcID.NATURE_IMPLING_1651,
            NpcID.MAGPIE_IMPLING_1652,
            NpcID.NINJA_IMPLING_1653,
            NpcID.DRAGON_IMPLING_1654,
            NpcID.WANDERING_IMPLING,
            NpcID.LUCKY_IMPLING,
            NpcID.LUCKY_IMPLING_7302,
            NpcID.CRYSTAL_IMPLING,
            NpcID.CRYSTAL_IMPLING_8742,
            NpcID.CRYSTAL_IMPLING_8743,
            NpcID.CRYSTAL_IMPLING_8744,
            NpcID.CRYSTAL_IMPLING_8745,
            NpcID.CRYSTAL_IMPLING_8746,
            NpcID.CRYSTAL_IMPLING_8747,
            NpcID.CRYSTAL_IMPLING_8748,
            NpcID.CRYSTAL_IMPLING_8749,
            NpcID.CRYSTAL_IMPLING_8750,
            NpcID.CRYSTAL_IMPLING_8751,
            NpcID.CRYSTAL_IMPLING_8752,
            NpcID.CRYSTAL_IMPLING_8753,
            NpcID.CRYSTAL_IMPLING_8754,
            NpcID.CRYSTAL_IMPLING_8755,
            NpcID.CRYSTAL_IMPLING_8756,
            NpcID.CRYSTAL_IMPLING_8757,
            //DESERT TREASURE 1+2
            NpcID.STRANGER,
            NpcID.MYSTERIOUS_FIGURE_12297,
            NpcID.MYSTERIOUS_FIGURE_12298,
            NpcID.SANDWICH_LADY_12299,
            NpcID.MYSTERIOUS_FIGURE_12300,
            NpcID.MYSTERIOUS_FIGURE_12301,
            //ELF STORY LINE
            NpcID.KINGS_MESSENGER,
            //FROZEN DOOR
            NpcID.MESSENGER,
            //TOA miniquest
            NpcID.MESSENGER_11814,
            NpcID.MESSENGER_11815,
            NpcID.MESSENGER_11816,
            NpcID.MESSENGER_11817,
            //Bob the cat
            NpcID.BOB,
            NpcID.BOB_2636,
            NpcID.BOB_4231,
            NpcID.BOB_8034,
            NpcID.NEITE_8035,
            NpcID.BOB_8055,
            NpcID.BOB_8111,
            NpcID.BOB_8112,
            NpcID.BOB_8113,
            NpcID.BOB_8114,
            NpcID.BOB_8115,
            NpcID.NOT_BOB,
            NpcID.NOT_BOB_8117,
            NpcID.BOB_8159,
            //Nieve
            NpcID.NIEVE_7108,
            NpcID.NIEVE_7109,
            NpcID.NIEVE_7110,
            //Clue NPCS
            NpcID.ARMADYLEAN_GUARD,
            NpcID.BANDOSIAN_GUARD,
            NpcID.DOUBLE_AGENT,
            NpcID.DOUBLE_AGENT_1778,
            NpcID.DOUBLE_AGENT_7312,
            NpcID.URI_7311,
            NpcID.URI_8638,
            NpcID.URI,
            NpcID.URI_1775,
            NpcID.URI_1776,
            NpcID.ZAMORAK_WIZARD,
            NpcID.SARADOMIN_WIZARD,
            NpcID.BRASSICAN_MAGE,
            NpcID.ANCIENT_WIZARD,
            NpcID.ANCIENT_WIZARD_7308,
            NpcID.ANCIENT_WIZARD_7309,
            //Slayer npcs
            NpcID.DEATH_SPAWN,
            NpcID.CRUSHING_HAND,
            NpcID.CHASM_CRAWLER,
            NpcID.SCREAMING_BANSHEE,
            NpcID.SCREAMING_TWISTED_BANSHEE,
            NpcID.GIANT_ROCKSLUG,
            NpcID.COCKATHRICE,
            NpcID.FLAMING_PYRELORD,
            NpcID.MONSTROUS_BASILISK,
            NpcID.MALEVOLENT_MAGE,
            NpcID.INSATIABLE_BLOODVELD,
            NpcID.INSATIABLE_MUTATED_BLOODVELD,
            NpcID.VITREOUS_JELLY,
            NpcID.VITREOUS_WARPED_JELLY,
            NpcID.CAVE_ABOMINATION,
            NpcID.ABHORRENT_SPECTRE,
            NpcID.REPUGNANT_SPECTRE,
            NpcID.CHOKE_DEVIL,
            NpcID.KING_KURASK,
            NpcID.NUCLEAR_SMOKE_DEVIL,
            NpcID.MARBLE_GARGOYLE,
            NpcID.MARBLE_GARGOYLE_7408,
            NpcID.NIGHT_BEAST,
            NpcID.GREATER_ABYSSAL_DEMON,
            NpcID.NECHRYARCH,
            NpcID.SPIKED_TUROTH,
            NpcID.SHADOW_WYRM,
            NpcID.SHADOW_WYRM_10399,
            NpcID.GUARDIAN_DRAKE,
            NpcID.GUARDIAN_DRAKE_10401,
            NpcID.COLOSSAL_HYDRA,
            //DMM Guards
            NpcID.GUARD_3361,
            NpcID.GNOME_GUARD_6574,
            NpcID.GUARD_6575,
            NpcID.GUARD_6576,
            NpcID.GUARD_6579,
            NpcID.GUARD_6580,
            NpcID.GUARD_6581,
            NpcID.GUARD_6582,
            NpcID.GUARD_6583,
            NpcID.GHOST_GUARD_6698,
            NpcID.GUARD_6699,
            NpcID.GUARD_6700,
            NpcID.GUARD_6701,
            NpcID.GUARD_6702,
            NpcID.PRIFDDINAS_GUARD);

    public static final Set<Integer> WHITELISTED_REGIONS = ImmutableSet.of(
            9285, 9541, 9797, 9540, 9796, // Zanaris
            12640, 12896, 13152, 13408, 12639, 12895, 13151, 13407, 12638, 12894, 13150, 13406, 12637, 12893, 13149, 13405, // Prif
            10835, 10834, // Dorgesh-kaan
            9808, 10064, 9807, 10063, // Mor Ul Rek
            12108, 11851, 11850, 12106, 12107, 12363, 12362, // Abyssal areas
            8036, 8292, 7291, // Leviathan area
            12132, // Duke area
            14484, // Guardians of the Rift
            15008, 15264, // Underwater
            13618, 13718, 11079, 11078, 11077, 10823, 10822, 10821, // Abandoned Mine
            11666, // Ah Za Rhoon
            6483, 6995, // Ancient Cavern
            11150, 10894, // Ape Atoll Dungeon
            10895, // Ape Atoll Banana Plantation
            10135, // West Ardougne Basement
            10134, 10136, 10391, 10647, // Ardougne Sewers
            11925, 12181, // Asgarnian Ice Caves
            11154, // Tomb of Bervirius
            10901, 10900, 10899, 10645, 10644, 10643, // Brimhaven Dungeon
            10910, // Brine Rat Cavern
            6557, 6556, 6813, 6812, // Catacombs of Kourend
            12696, // Champions' Challenge
            10392, // Chaos Druid Tower
            5789, // Chasm of Fire
            12948, // Chasm of Tears
            10129, // Chinchompa Hunting Ground
            10390, // Clock Tower Basement
            8076, 8332, // Corsair Cove Dungeon
            6553, 6809, // Crabclaw Caves
            11414, // Crandor Dungeon
            8280, 8536, // Crash Site Cavern
            7827, // Crumbling Tower
            14744, // Daeyalt Essence Mine
            13464, 13465, // Digsite Dungeon
            10833, // Dorgesh-Kaan South Dungeon
            12950, 13206, // Dorgeshuun Mines
            12439, 12438, // Draynor Sewers
            12185, 12184, 12183, // Dwarven Mines
            8013, // Eagles' Peak Dungeon
            14746, // Ectofuntus
            12441, 12442, 12443, 12698, // Edgeville Dungeon
            10906, 7760, // Elemental Workshop
            13252, // Elven rabbit cave
            12423, // Enakhra's Temple
            9796, // Evil Chicken's Lair
            14235, 13979, // Experiment Cave
            12700, // Ferox Enclave Dungeon
            7323, // Forthos Dungeon
            10907, 10908, 11164, // Fremennik Slayer Dungeon
            10137, // Glarial's Tomb
            10393, // Goblin Cave
            9882, // Grand Tree Tunnels
            12694, // H.A.M. Hideout
            10321, // H.A.M. Store room
            11674, // Heroes' Guild Mine
            12737, 12738, 12993, 12994, // Iorwerth Dungeon
            8593, // Isle of Souls Dungeon
            9631, // Jatizso Mines
            9875, 9874, // Jiggig Burial Tomb
            11412, // Jogre Dungeon
            11413, // Karamja Dungeon
            5280, 5279, 5023, 5535, 5022, 4766, 4510, 4511, 4767, 4768, 4512, // Karuulm Slayer Dungeon
            10658, // KGP Headquarters
            9358, 9359, 9360, 9615, 9616, 9871, 10125, 10126, 10127, 10128, 10381, 10382, 10383, 10384, 10637, 10638, 10639, 10640, // Kruk's Dungeon
            10904, // Legends' Guild Dungeon
            10140, // Lighthouse
            5275, // Lizardman Caves
            5277, // Lizardman Temple
            12693, 12949, // Lumbridge Swamp Caves
            9377, // Lunar Isle Mine
            11662, // Maniacal Monkey Hunter Area
            9544, // Meiyerditch Mine
            10144, 10400, // Miscellania Dungeon
            11924, // Mogre Camp
            14994, 14995, 15251, // Mos Le'Harmless Caves
            14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193, // Motherlode Mine
            7752, 8008, // Mourner Tunnels
            9046, // Mouse Hole
            14232, 14233, 14487, 14488, // Myreditch Laboratories
            13721, 13974, 13977, 13978, // Myreque Hideout
            7564, 7820, 7821, // Myths' Guild Dungeon
            9362, // Observatory Dungeon
            10387, // Ogre Enclave
            12119, // Ourania Cave
            4763, // Quidamortem Cave
            11668, // Rashiliyta's Tomb
            11609, 11610, 11611, 11865, 11866, 11867, 12121, 12122, 12123, // Ruins of Camdozaal
            11425, // Salt Mine
            13722, // Saradomin Shrine (Paterdomus)
            13975, // Shade Catacombs
            10575, 10831, // Shadow Dungeon
            6043, // Shayzien Crypts
            14999, 15000, 15001, 15255, 15256, 15257, 15511, 15512, 15513, // Sisterhood Sanctuary
            12946, 13202, // Smoke Dungeon
            13200, // Sophanem Dungeon
            12695, // Sourhog Cave
            7505, 8017, 8530, 9297, // Stronghold of Security
            9624, 9625, 9880, 9881, // Stronghold Slayer Cave
            12616, 12615, // Tarn's Lair
            11416, 11417, 11671, 11672, 11673, 11928, 11929, // Taverley Dungeon
            10649, 10905, 10650, // Temple of Ikov
            7496, // Temple of Light
            11151, // Temple of Marimbo
            7070, 7326, // The Warrens
            13209, // Dungeon of Tolna
            12100, // Tower of Life Basement
            13250, // Trahaearn Mine
            12625, // Tunnel of Chaos
            9369, 9370, // Underground Pass
            12954, 13210, // Varrock Sewers
            9545, 11153, // Viyeldi Caves
            11675, // Warriors' Guild Basement
            13461, // Water Ravine
            9886, 10142, 7492, 7748, // Waterbirth Dungeon
            10394, // Waterfall Dungeon
            14234, // Werewolf Agility Course
            11418, 11419, // White Wolf Mountain Caves
            10903, // Witchhaven Shrine Dungeon
            12437, // Wizards' Tower Basement
            6298, // Woodcutting Guild Dungeon
            14495, 14496, // Wyvern Cave
            10388 // Yanille Agility Dungeon
    );
}