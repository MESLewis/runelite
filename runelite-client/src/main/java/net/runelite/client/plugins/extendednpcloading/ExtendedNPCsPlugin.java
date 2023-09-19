package net.runelite.client.plugins.extendednpcloading;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private Hooks hooks;

	@Inject
	private ExtendedNPCsConfig config;
	//Contains every FakeNPC
	private Map<Integer, FakeNPC> fakeNpcs = new HashMap<>();
	//Subset of FakeNPC that need to have their position updated
	private Set<FakeNPC> walking = new HashSet<>();
	private Set<FakeNPC> walkingToRemove = new HashSet<>();

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
			for (FakeNPC npc : fakeNpcs.values())
			{
				npc.shutDown();
			}
			fakeNpcs.clear();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			//Respawn all the visible fake npcs after map loading
			//TODO the mapping of npcIndex breaks after scene change
			for (Map.Entry<Integer, FakeNPC> entry : fakeNpcs.entrySet())
			{
				entry.getValue().recreate();
			}
		}
	}


	@Subscribe
	public void onNpcDespawned(NpcDespawned eventNpc)
	{
//		if(!eventNpc.getNpc().getName().equals("Lumbridge Guide")) {
//			return;
//		}

		NPC npc = eventNpc.getNpc();
		int npcId = eventNpc.getNpc().getIndex();
		FakeNPC fakeNpc;
		if (fakeNpcs.containsKey(npcId))
		{
			fakeNpc = fakeNpcs.get(npcId);
		}
		else
		{
			fakeNpc = new FakeNPC(this, client, npc);
			fakeNpcs.put(npcId, fakeNpc);
		}
		fakeNpc.jumpToAndShow(npc);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned eventNpc)
	{
		int npcId = eventNpc.getNpc().getIndex();
		if (fakeNpcs.containsKey(npcId))
		{
			FakeNPC fakeNPC = fakeNpcs.get(npcId);
			fakeNPC.lerpToAndHide(eventNpc.getNpc());
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		for (FakeNPC npc : fakeNpcs.values())
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
			FakeNPC fakeNPC = fakeNpcs.get(npc.getIndex());
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
