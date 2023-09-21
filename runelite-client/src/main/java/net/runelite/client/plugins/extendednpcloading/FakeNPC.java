package net.runelite.client.plugins.extendednpcloading;

import net.runelite.api.Client;
import java.awt.Shape;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Max distance between a real NPC and the player is 15 tiles.
 *
 * TODO explicit modes/status. Do we know the NPC index? Do we have a realNPC?
 * TODO Are we walking to a realNPC?
 */
public class FakeNPC
{
	private Client client;
	private ExtendedNPCsPlugin plugin;
	private int idlePoseAnimation = -1;
	private int walkAnimation = 1;
	private int orientation = 0;
	@Getter
	private int npcIndex = -1;
	private boolean isAttackable;
	private boolean shouldRun;
	@Getter
	private NPCComposition composition;
	@Getter
	private RuneLiteObject rlobj;
	//Saved as we go because the scene can become null at any time
	private WorldPoint curLocationWorldPoint;
	private NPC realNPC = null;
	private LocalPoint walkingDestination;
	private String EXAMINE_TEXT = "Totally real npc";

	public FakeNPC(ExtendedNPCsPlugin plugin, Client client, NPC npc)
	{
		this.client = client;
		this.plugin = plugin;
		realNPC = npc;
		extractNPCData(npc);
		copyNPC(true);
	}

	public FakeNPC(ExtendedNPCsPlugin plugin, Client client, NPCSpawnDefinition spawnDefinition)
	{
		this.client = client;
		this.plugin = plugin;
		composition = client.getNpcDefinition(spawnDefinition.getId());
		copyNPC(true);
	}

	/**
	 * Since we sometimes start from just an NPC definition
	 * update the data from the real NPC when possible
	 * @param npc
	 */
	private void extractNPCData(NPC npc)
	{
		npcIndex = npc.getIndex();
		composition = npc.getTransformedComposition();
		idlePoseAnimation = npc.getIdlePoseAnimation();
		walkAnimation = npc.getWalkAnimation();
		orientation = npc.getOrientation();
		isAttackable = npc.getCombatLevel() > 0;
	}

	public boolean isInScene()
	{
		return curLocationWorldPoint.isInScene(client);
	}

	public void recreate()
	{
		if (curLocationWorldPoint == null)
		{
			return;
		}
		//TODO doesn't work for extended scene
		LocalPoint newLocal = LocalPoint.fromWorld(client, curLocationWorldPoint);
		if (newLocal != null)
		{
			this.realNPC = this.npcIndex >= 0 ? client.getCachedNPCs()[this.npcIndex] : null;
			copyNPC(rlobj.isActive());
			this.setLocation(newLocal);
		}
	}

	private void copyNPC(boolean active)
	{
		if (rlobj != null)
		{
			rlobj.setActive(false);
		}

		rlobj = client.createRuneLiteObject();
		int[] modelIds = composition.getModels();
		if (modelIds == null)
		{
			return;
		}
		ModelData[] mDatas = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			mDatas[i] = client.loadModelData(modelIds[i]);
		}
		ModelData mData = client.mergeModels(mDatas);

		short[] colorsToReplace = composition.getColorToReplace();
		short[] colorsToReplaceWith = composition.getColorToReplaceWith();
		if (colorsToReplace != null && colorsToReplaceWith != null)
		{
			mData.cloneColors();
			for (int i = 0; i < colorsToReplace.length; i++)
			{
				mData = mData.recolor(colorsToReplace[i], colorsToReplaceWith[i]);
			}
		}
		//desaturate attackable npcs
		if (isAttackable)
		{
			mData.cloneColors();
			for (int i = 0; i < mData.getFaceColors().length; i++)
			{
				// The game uses bitpacked HSL where bit 8-10 control the saturation(HHHHHHSSSLLLLLLL)
				// 64639 is bitmask 1111110001111111 which will completely desaturate a color
				// 65023 is bitmask 1111110111111111 which will ~half desaturate a color
				mData.recolor(mData.getFaceColors()[i], (short) (mData.getFaceColors()[i] & 65023));
			}
		}

		if (composition.getWidthScale() != 128 || composition.getHeightScale() != 128)
		{
			mData.cloneVertices();
			mData.scale(composition.getWidthScale(), composition.getHeightScale(), composition.getWidthScale());
		}

		Model model = mData.light();
		rlobj.setModel(model);

		int animation = idlePoseAnimation;
		if (animation >= 0)
		{
			rlobj.setAnimation(client.loadAnimation(animation));
			rlobj.setShouldLoop(true);
		}
		//TODO I think we need to use the rotate before lighting sometimes
		rlobj.setOrientation(orientation);
		rlobj.setActive(active);
	}

	public void lerpToAndHide(NPC spawnedNPC)
	{
		extractNPCData(spawnedNPC);
		this.realNPC = spawnedNPC;
		//Skip lerping if distance between is too great
		//TODO config option for this between skip lerp/run
		if (curLocationWorldPoint.distanceTo(spawnedNPC.getWorldLocation()) > 10)
		{
			this.setLocation(spawnedNPC.getLocalLocation());
			this.rlobj.setOrientation(spawnedNPC.getOrientation());
			this.rlobj.setActive(false);
			return;
		}
		//TODO config option for this between skip lerp/run
		if (isAttackable)
		{
			this.shouldRun = true;
		}
		if (walkAnimation >= 0)
		{
			rlobj.setAnimation(client.loadAnimation(walkAnimation));
			rlobj.setShouldLoop(true);
		}
		plugin.addWalking(this);
	}

	public void jumpToAndShow(NPC despawnedNPC)
	{
		extractNPCData(despawnedNPC);
		this.realNPC = null;
		this.setLocation(despawnedNPC.getLocalLocation());
		if (idlePoseAnimation >= 0)
		{
			rlobj.setAnimation(client.loadAnimation(idlePoseAnimation));
			rlobj.setShouldLoop(true);
		}
		rlobj.setOrientation(despawnedNPC.getOrientation());
		rlobj.setActive(true);
	}

	//TODO clean up logic here, do we just always want to call this from constructor?
	public void jumpToAndShow(NPCSpawnDefinition spawnDef)
	{
		this.realNPC = null;
		this.composition = client.getNpcDefinition(spawnDef.getId());
		LocalPoint localPoint = LocalPoint.fromWorld(client, spawnDef.getX(), spawnDef.getY());
		if (localPoint == null)
		{
			return;
		}
		//TODO deal with other z planes
		this.setLocation(localPoint);
		if (idlePoseAnimation >= 0)
		{
			rlobj.setAnimation(client.loadAnimation(idlePoseAnimation));
			rlobj.setShouldLoop(true);
		}
		rlobj.setActive(true);
	}

	public void shutDown()
	{
		this.rlobj.setActive(false);
	}

	/**
	 * Called ever 20ms
	 * We use it to update position when walking
	 */
	public void processClientTick()
	{
		if (realNPC != null)
		{
			walkingDestination = realNPC.getLocalLocation();
		}
		LocalPoint curLocation = rlobj.getLocation();

		//Speed up the lerp by 2x if the distance is over 10 tiles
		final int movementDelta = (int) (7 * (this.shouldRun ? 1.5 : 1));
		int dx = Math.min(movementDelta, Math.abs(curLocation.getX() - walkingDestination.getX()));
		int dy = Math.min(movementDelta, Math.abs(curLocation.getY() - walkingDestination.getY()));
		if (curLocation.getX() > walkingDestination.getX())
		{
			dx *= -1;
		}
		if (curLocation.getY() > walkingDestination.getY())
		{
			dy *= -1;
		}

		int newX = curLocation.getX() + dx;
		int newY = curLocation.getY() + dy;
		this.setLocation(newX, newY);

		final int orientationDelta = 50;
		int orientationDestination = rlobj.getOrientation();
		if (realNPC != null)
		{
			orientationDestination = realNPC.getOrientation();
		}
		if (dx != 0 || dy != 0)
		{
			//Math to face walking direction
			double angleDegrees = Math.toDegrees(Math.atan2(-dx, -dy));
			angleDegrees = (360 + (angleDegrees % 360)) % 360;
			orientationDestination = (int) (angleDegrees * 2047d / 360d);
		}

		int dorient = Math.min(orientationDelta, Math.abs(rlobj.getOrientation() - orientationDestination));
		if (rlobj.getOrientation() > orientationDestination)
		{
			dorient *= -1;
		}
		int newOrientation = rlobj.getOrientation() + dorient;
		rlobj.setOrientation(newOrientation);


		if (rlobj.getLocation().distanceTo(walkingDestination) < 1
			&& rlobj.getOrientation() == orientationDestination)
		{
			if (realNPC != null)
			{
				rlobj.setActive(false);
			}
			plugin.removeWalking(this);
		}
	}

	/**
	 * Used for random wander, and to stay near the realNPC boundary line
	 */
	public void processGameTick()
	{
		//Check every tick because the npc spawned events are weird on map load
		if (this.npcIndex >= 0)
		{
			this.realNPC = client.getCachedNPCs()[this.npcIndex];
		}
		if (rlobj.isActive() && this.realNPC == null)
		{
			LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
			LocalPoint curLocation = rlobj.getLocation();

			//If the fake npc is within the real npc visible range
			int distanceX = Math.abs(curLocation.getX() - playerLocation.getX());
			int distanceY = Math.abs(curLocation.getY() - playerLocation.getY());
			if (distanceX < 10 * 128 && distanceY < 10 * 128)
			{
				int dx = 0;
				int dy = 0;
				//Move to the closest edge
				if (distanceX > distanceY)
				{
					if (curLocation.getX() > playerLocation.getX())
					{
						dx = 256;
					}
					else
					{
						dx = -256;
					}
				}
				else
				{
					if (curLocation.getY() > playerLocation.getY())
					{
						dy = 256;
					}
					else
					{
						dy = -256;
					}
				}
				int newX = curLocation.getX() + dx;
				int newY = curLocation.getY() + dy;
				//TODO if walking a total of X away from original location, just despawn
				walkingDestination = new LocalPoint(newX, newY);
				plugin.addWalking(this);
			}
		}
		if (rlobj.isActive() && this.realNPC != null)
		{
			if (rlobj.getLocation().distanceTo(realNPC.getLocalLocation()) < 1)
			{
				rlobj.setActive(false);
			}
			else
			{
				walkingDestination = realNPC.getLocalLocation();
				plugin.addWalking(this);
			}
		}
	}

	public boolean isMouseOverObject()
	{
		if (rlobj.getModel() == null || LocalPoint.fromWorld(client, curLocationWorldPoint) == null)
		{
			return false;
		}
		Point p = client.getMouseCanvasPosition();
		Shape clickbox = Perspective.getClickbox(client, rlobj.getModel(), rlobj.getOrientation(), LocalPoint.fromWorld(client, curLocationWorldPoint).getX(), LocalPoint.fromWorld(client, curLocationWorldPoint).getY(),
					Perspective.getTileHeight(client, LocalPoint.fromWorld(client, curLocationWorldPoint), curLocationWorldPoint.getPlane()));
		if (clickbox != null)
		{
			return clickbox.contains(p.getX(), p.getY());
		}
		return false;
	}

	public void examine(MenuEntry menuEntry)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Name: " + this.composition.getName() + " Index: " + this.npcIndex, null);
		if (this.realNPC != null)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "RealNPC: " + this.realNPC.getName(), null);
		}
	}

	private void setLocation(int x, int y)
	{
		this.setLocation(new LocalPoint(x, y));
	}

	private void setLocation(LocalPoint localPoint)
	{
		rlobj.setLocation(localPoint, client.getPlane());
		curLocationWorldPoint = WorldPoint.fromLocal(client, localPoint);
	}
}
