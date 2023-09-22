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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
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
/**
 * There are 2 tiers of FakeNPC
 * The highest tier is a seen npc. Its is a FakeNPC that represents a real NPC that walked out of view.
 * It is unclear how long NPC index stays valid, it seems like between client restarts?
 * Second tier is FakeNPCs that are loaded during map load, these are promoted to seen npcs if the player gets close enough
 */
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

	//Mapping of FakeNPC's that are directly tied to an npc that has been seen already this play session
	private Map<Integer, FakeNPC> seenNPCs = new HashMap<>();

	//Subset of FakeNPC that need to have their position updated
	private Set<FakeNPC> walking = new HashSet<>();
	private Set<FakeNPC> walkingToRemove = new HashSet<>();

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Override
	protected void startUp() throws Exception
	{
		hooks.registerRenderableDrawListener(drawListener);
		clientThread.invokeLater(this::onAreaLoaded);
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
			//Respawn all the visible fake npcs after map loading
			for (Iterator<Map.Entry<NPCSpawnDefinition, FakeNPC>> iterator = staticNPCs.entrySet().iterator(); iterator.hasNext(); )
			{
				FakeNPC fakeNPC = iterator.next().getValue();
				if (fakeNPC.isInScene())
				{
					fakeNPC.recreate();
				}
				else
				{
					fakeNPC.shutDown();
					iterator.remove();
					seenNPCs.remove(fakeNPC.getNpcIndex());
					walking.remove(fakeNPC);
					//TODO cleanup other lists
				}
			}
			for (Iterator<FakeNPC> iterator = dynamicNPCs.iterator(); iterator.hasNext(); )
			{
				FakeNPC fakeNPC = iterator.next();
				if (fakeNPC.isInScene())
				{
					fakeNPC.recreate();
				}
				else
				{
					fakeNPC.shutDown();
					iterator.remove();
					seenNPCs.remove(fakeNPC.getNpcIndex());
					walking.remove(fakeNPC);
					//TODO cleanup other lists
				}
			}
			this.onAreaLoaded();
		}
	}

	private void onAreaLoaded()
	{
		if(!isAllowedRegion())
		{
			return;
		}
		int[] loadedRegions = client.getMapRegions();
		List<NPC> realNPCs = client.getNpcs();
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
							if (fakeNpcMatch(dynamic, def))
							{
								fakeNPC = dynamic;
								break;
							}
						}
					}
					if (fakeNPC == null)
					{
						//Check already spawned real npcs for a match
						for (Iterator<NPC> iterator = realNPCs.listIterator(); iterator.hasNext();)
						{
							NPC existingNPC = iterator.next();
							if (npcMatch(def, existingNPC))
							{
								int npcIndex = existingNPC.getIndex();
								if (seenNPCs.containsKey(npcIndex))
								{
									fakeNPC = seenNPCs.get(npcIndex);
								}
								else
								{
									fakeNPC = new FakeNPC(this, client, existingNPC);
								}
								staticNPCs.put(def, fakeNPC);
								seenNPCs.put(npcIndex, fakeNPC);
								fakeNPC.lerpToAndHide(existingNPC);
								System.out.printf("Linked new hidden static npc: %s\n", def.getName());
								iterator.remove(); //Remove the NPC we just claimed
								break;
							}
						}
					}
					if (fakeNPC == null)
					{
						fakeNPC = new FakeNPC(this, client, def);
						staticNPCs.put(def, fakeNPC);
						System.out.printf("New static npc: %s\n", def.getName());
						fakeNPC.jumpToAndShow(def);
					}
				}
			}
		}
		System.out.print("\n");
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned eventNpc)
	{
		System.out.printf("Despawn: %s\n", eventNpc.getNpc().getName());
		NPC npc = eventNpc.getNpc();
		int npcIndex = eventNpc.getNpc().getIndex();

		if (npc.isDead() || npc.getComposition().getName().toLowerCase().equals("null") || npc.getComposition().isFollower() || ExtendedNPCsConstants.IGNORED_NPCS.contains(npc.getId()) || !isAllowedRegion())
		{
			return;
		}

		FakeNPC fakeNpc = null;
		if (seenNPCs.containsKey(npcIndex))
		{
			fakeNpc = seenNPCs.get(npcIndex);
		}
		else
		{
			//Find a matching static npc if possible
			for (FakeNPC staticFakeNPC : staticNPCs.values())
			{
				if (npcMatch(staticFakeNPC, eventNpc.getNpc()))
				{
					fakeNpc = staticFakeNPC;
					break;
				}
			}
			if (fakeNpc == null)
			{
				fakeNpc = new FakeNPC(this, client, npc);
				dynamicNPCs.add(fakeNpc);
				System.out.printf("New Dynamic npc: %s\n", npc.getName());
			}
			seenNPCs.put(npcIndex, fakeNpc);
		}
		fakeNpc.jumpToAndShow(npc);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned eventNpc)
	{
		System.out.printf("Spawn: %s - ", eventNpc.getNpc().getName());
		int npcIndex = eventNpc.getNpc().getIndex();
		if (seenNPCs.containsKey(npcIndex))
		{
			System.out.printf("Already seen at index: %d", npcIndex);
			FakeNPC fakeNpc = seenNPCs.get(npcIndex);
			fakeNpc.lerpToAndHide(eventNpc.getNpc());
		}
		else
		{
			for (FakeNPC staticFakeNPC : staticNPCs.values())
			{
				if (npcMatch(staticFakeNPC, eventNpc.getNpc()))
				{
					System.out.printf("Found match with static npc - %s", staticFakeNPC.getComposition().getName());
					seenNPCs.put(npcIndex, staticFakeNPC);
					staticFakeNPC.lerpToAndHide(eventNpc.getNpc());
					break;
				}
			}
		}
		System.out.print("\n");
	}

	private boolean npcMatch(FakeNPC fake, NPC real)
	{
		if (fake.getNpcIndex() == real.getIndex()
			|| (fake.getNpcIndex() == -1 &&
			(fake.getComposition() == real.getTransformedComposition()
				|| fake.getComposition() == real.getComposition()
				|| (fake.getComposition().getId() == real.getId())
				|| (fake.getComposition().getName().equals(real.getName()))))
		)
		{
			return true;
		}
		return false;
	}

	private boolean npcMatch(NPCSpawnDefinition fake, NPC real)
	{
		if (fake.getId() == real.getId())
		{
			return true;
		}
		return false;
	}

	private boolean fakeNpcMatch(FakeNPC fake, NPCSpawnDefinition spawnDef)
	{
		if (fake.getNpcIndex() == -1
			&& fake.getComposition().getId() == spawnDef.getId())
		{
			return true;
		}
		return false;
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

	private boolean isAllowedRegion()
	{
		boolean isOverWorld = WorldPoint.getMirrorPoint(client.getLocalPlayer().getWorldLocation(), true).getY() < Constants.OVERWORLD_MAX_Y;
		boolean isWhitelistedRegion = ExtendedNPCsConstants.WHITELISTED_REGIONS.contains( WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID());
		boolean isInstance = client.isInInstancedRegion();
		return  (isOverWorld || isWhitelistedRegion || !isInstance);
	}

	@Provides
	ExtendedNPCsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExtendedNPCsConfig.class);
	}
}
