package net.runelite.client.plugins.extendednpcloading;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Renderable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Extended NPCs"
)
public class ExtendedNPCsPlugin extends Plugin
{
	private static final Map<Integer, Collection<NPCSpawnDefinition>> SPAWNS = new HashMap<>();

	static
	{
		//Load spawn file
		try (InputStream in = ExtendedNPCsPlugin.class.getResourceAsStream("npc-spawns.json"))
		{
			// npcid, wander range, x, y, level(plane)
			// CHECKSTYLE:OFF
			final TypeToken<Collection<NPCSpawnDefinition>> typeToken = new TypeToken<>()
			{
			};
			// CHECKSTYLE:ON
			List<NPCSpawnDefinition> spawnDefinitionList = new Gson().fromJson(new InputStreamReader(in), typeToken.getType());

			//Put into buckets by region
			for (NPCSpawnDefinition def : spawnDefinitionList)
			{
				WorldPoint loc = new WorldPoint(def.getX(), def.getY(), def.getLevel());
				int regionId = loc.getRegionID();

				Collection<NPCSpawnDefinition> regionSpawns = SPAWNS.getOrDefault(regionId, new HashSet<>());
				regionSpawns.add(def);
				SPAWNS.putIfAbsent(regionId, regionSpawns);
			}
		}
		catch (IOException ex)
		{
			throw new RuntimeException(ex);
		}
	}

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private Hooks hooks;

	@Inject
	private ExtendedNPCsConfig config;

	//Combined the staticNPCs and dynamicNPCs are every FakeNPC
	//FakeNPCs that are spawned on region load
	private Map<NPCSpawnDefinition, FakeNPC> staticNPCs = new HashMap<>();
	//FakeNPCs that are spawned from an actual NPC leaving the scene.
	private Collection<FakeNPC> dynamicNPCs = new ArrayList<>();
	//Subset of FakeNPC that need to have their position updated
	private Set<FakeNPC> walking = new HashSet<>();
	//Mapping of FakeNPC's that are directly tied to an npc that has been seen already this play session
	private Map<Integer, FakeNPC> seenNPCs = new HashMap<>();
	private Set<FakeNPC> walkingToRemove = new HashSet<>();

	private static final Set<Integer> IGNORED_NPCS = ImmutableSet.of(
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
			324//teleport animation cow
	);
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Override
	protected void startUp() throws Exception
	{
		hooks.registerRenderableDrawListener(drawListener);
	}

	@Override
	protected void shutDown() throws Exception
	{
		hooks.unregisterRenderableDrawListener(drawListener);
		clientThread.invokeLater(() ->
		{
			for (FakeNPC npc : dynamicNPCs)
			{
				npc.shutDown();
			}
			for (FakeNPC npc : staticNPCs.values())
			{
				npc.shutDown();
			}
			staticNPCs.clear();
			dynamicNPCs.clear();
			seenNPCs.clear();
			walking.clear();
			walkingToRemove.clear();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			//Clear seenNPCs since the id mappings are not valid after loading
			this.seenNPCs.clear();
			//Respawn all the visible fake npcs after map loading
			for (FakeNPC fakeNPC : staticNPCs.values())
			{
				fakeNPC.recreate();
			}
			for (FakeNPC fakeNPC : dynamicNPCs)
			{
				fakeNPC.recreate();
			}

			//TODO clear out staticNPCs or it grows infinitely
			int[] loadedRegions = client.getMapRegions();
			for (int regionId : loadedRegions)
			{
				Collection<NPCSpawnDefinition> regionSpawns = SPAWNS.get(regionId);
				for (NPCSpawnDefinition def : regionSpawns)
				{
					if (WorldPoint.isInScene(client, def.getX(), def.getY()) && def.getLevel() == client.getPlane())
					{
						FakeNPC fakeNPC = null;
						if (staticNPCs.containsKey(def))
						{
							fakeNPC = staticNPCs.get(def);
						}
						else
						{
							for (FakeNPC dynamic : dynamicNPCs)
							{
								if (dynamic.getComposition() == client.getNpcDefinition(def.getId()))
								{
									fakeNPC = dynamic;
									break;
								}
							}
						}
						if (fakeNPC == null)
						{
							fakeNPC = new FakeNPC(this, client, def);
							staticNPCs.put(def, fakeNPC);
						}
						fakeNPC.jumpToAndShow(def);
					}
				}
			}
		}
	}


	@Subscribe
	public void onNpcDespawned(NpcDespawned eventNpc)
	{
		NPC npc = eventNpc.getNpc();
		int npcId = eventNpc.getNpc().getIndex();

		if (npc.isDead() || npc.getComposition().isFollower() || IGNORED_NPCS.contains(npcId))
		{
			return;
		}

		FakeNPC fakeNpc = null;
		if (seenNPCs.containsKey(npcId))
		{
			fakeNpc = seenNPCs.get(npcId);
		}
		else
		{
			//Find a matching static npc if possible
			for (FakeNPC staticFakeNPC : staticNPCs.values())
			{
				if (staticFakeNPC.getComposition() == eventNpc.getNpc().getTransformedComposition())
				{
					fakeNpc = staticFakeNPC;
					break;
				}
			}
			if (fakeNpc == null)
			{
				fakeNpc = new FakeNPC(this, client, npc);
				dynamicNPCs.add(fakeNpc);
			}
			seenNPCs.put(npcId, fakeNpc);
		}
		fakeNpc.jumpToAndShow(npc);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned eventNpc)
	{
		int npcId = eventNpc.getNpc().getIndex();
		FakeNPC fakeNpc = null;
		if (seenNPCs.containsKey(npcId))
		{
			fakeNpc = seenNPCs.get(npcId);
		}
		else
		{
			for (FakeNPC staticFakeNPC : staticNPCs.values())
			{
				if (staticFakeNPC.getComposition() == eventNpc.getNpc().getTransformedComposition())
				{
					fakeNpc = staticFakeNPC;
					seenNPCs.put(npcId, fakeNpc);
					break;
				}
			}
		}
		if (fakeNpc != null)
		{
			fakeNpc.lerpToAndHide(eventNpc.getNpc());
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		for (FakeNPC npc : dynamicNPCs)
		{
			npc.processGameTick();
		}
		for (FakeNPC npc : staticNPCs.values())
		{
			npc.processGameTick();
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick tick)
	{
		for (FakeNPC npc : walking)
		{
			npc.processClientTick();
		}
		walking.removeAll(walkingToRemove);
		walkingToRemove.clear();
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		for (FakeNPC npc : staticNPCs.values())
		{
			if (npc.getRlobj().isActive() && npc.isMouseOverObject())
			{
				client.createMenuEntry(0)
						.setOption("Examine")
						.setTarget("<col=FFFFFF>Fake npc</col>")
						.onClick(npc::examine);
			}
		}
		for (FakeNPC npc : dynamicNPCs)
		{
			if (npc.getRlobj().isActive() && npc.isMouseOverObject())
			{
				client.createMenuEntry(0)
					.setOption("Examine")
					.setTarget("<col=FFFFFF>Fake npc</col>")
					.onClick(npc::examine);
			}
		}
	}

	void addWalking(FakeNPC npc)
	{
		this.walking.add(npc);
	}

	void removeWalking(FakeNPC npc)
	{
		this.walkingToRemove.add(npc);
	}

	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;
			FakeNPC fakeNPC = seenNPCs.get(npc.getIndex());
			if (fakeNPC != null)
			{
				//Don't draw npc's that have a fakeNPC walking to them
				return !walking.contains(fakeNPC);
			}
		}
		return true;
	}

	@Provides
	ExtendedNPCsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExtendedNPCsConfig.class);
	}
}
