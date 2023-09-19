package net.runelite.client.plugins.extendednpcloading;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
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
	private ExtendedNPCsConfig config;
	private HashMap<Integer, FakeNPC> fakeNpcs = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
	}

	@Override
	protected void shutDown() throws Exception
	{
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
		NPC npc = eventNpc.getNpc();
		int npcId = eventNpc.getNpc().getIndex();
		FakeNPC fakeNpc;
		if (fakeNpcs.containsKey(npcId))
		{
			fakeNpc = fakeNpcs.get(npcId);
		}
		else
		{
			fakeNpc = new FakeNPC(client, npc);
			fakeNpcs.put(npcId, fakeNpc);
		}
		fakeNpc.jumpToAndShow(npc.getLocalLocation());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned eventNpc)
	{
		int npcId = eventNpc.getNpc().getIndex();
		if (fakeNpcs.containsKey(npcId))
		{
			FakeNPC fakeNPC = fakeNpcs.get(npcId);
			fakeNPC.lerpToAndHide(eventNpc.getNpc().getLocalLocation());
		}
	}

	@Provides
	ExtendedNPCsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExtendedNPCsConfig.class);
	}
}
