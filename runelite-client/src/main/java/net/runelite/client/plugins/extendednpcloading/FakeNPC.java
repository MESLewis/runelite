package net.runelite.client.plugins.extendednpcloading;

import java.awt.Shape;
import lombok.Getter;
import net.runelite.api.Animation;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Max distance between a real NPC and the player is 15 tiles.
 */
public class FakeNPC
{
	private Client client;
	private ExtendedNPCsPlugin plugin;
	private FakeNPCMode mode = FakeNPCMode.IDLE;
	private Animation idlePoseAnimation = null;
	private Animation walkAnimation = null;
	private int orientation = 0;
	@Getter
	private int npcIndex = -1;
	private boolean isAttackable;
	private boolean shouldRun;
	@Getter
	private NPCComposition composition;
	@Getter
	private RuneLiteObject rlobj;
	private WorldPoint spawnWorldPoint;
	//Saved as we go because the scene can become null at any time
	private WorldPoint curLocationWorldPoint;
	private NPC realNPC = null;
	private LocalPoint walkingDestination;
	private String EXAMINE_TEXT = "Totally real npc";
	private Model cachedModel = null;

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
		orientation = (int) (Math.random() * 2047); //2047 is maximum orientation units used by jagex
		curLocationWorldPoint = new WorldPoint(spawnDefinition.getX(), spawnDefinition.getY(), spawnDefinition.getLevel());
		idlePoseAnimation = client.loadAnimation(composition.getIdlePoseAnimation());
		walkAnimation = client.loadAnimation(composition.getWalkAnimation());
		isAttackable = composition.getCombatLevel() > 0;
		copyNPC(true);
		LocalPoint localPoint = LocalPoint.fromScene(spawnDefinition.getX() - client.getBaseX(), spawnDefinition.getY() - client.getBaseY());
		//TODO deal with other z planes
		this.setLocation(localPoint);
		this.spawnWorldPoint = new WorldPoint(spawnDefinition.getX(), spawnDefinition.getY(), spawnDefinition.getLevel());
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
		idlePoseAnimation = client.loadAnimation(npc.getIdlePoseAnimation());
		walkAnimation = client.loadAnimation(npc.getWalkAnimation());
		orientation = npc.getOrientation();
		isAttackable = npc.getCombatLevel() > 0;
	}

	public boolean isInScene()
	{
		return curLocationWorldPoint.isInScene(client);
	}

	/**
	 * Create a new RuneLiteObject to represent this FakeNPC in the new scene
	 */
	public void recreate()
	{
		LocalPoint newLocal = LocalPoint.fromScene(curLocationWorldPoint.getX() - client.getBaseX(), curLocationWorldPoint.getY() - client.getBaseY());
		//TODO new inScene check
		this.realNPC = this.npcIndex >= 0 ? client.getCachedNPCs()[this.npcIndex] : null;
		copyNPC(rlobj.isActive());
		this.setLocation(newLocal);
	}

	private void copyNPC(boolean active)
	{
		//We need to create a new runeliteobject every time but we can store the models etc
		rlobj = client.createRuneLiteObject();
		if (cachedModel == null)
		{
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
			//TODO something is a little off with lighting
			cachedModel = mData.light();
		}

		// TODO I think the cached model is going to make this go too high up?
		// If the npc is not within the base scene but still in the loaded region it's an extended region npc.
		if (curLocationWorldPoint != null && !curLocationWorldPoint.isInScene(client))
		{
			LocalPoint newLocal = LocalPoint.fromScene(curLocationWorldPoint.getX() - client.getBaseX(), curLocationWorldPoint.getY() - client.getBaseY());
			int posX = (client.getExpandedMapLoading() * 8) + newLocal.getX() / 128;
			int posY = (client.getExpandedMapLoading() * 8) + newLocal.getY() / 128;
			if (posX >= 0 && posX < 184 && posY >= 0 && posY < 184)
			{
				Tile bridge = client.getScene().getExtendedTiles()[curLocationWorldPoint.getPlane()][posX][posY].getBridge();
				if (bridge != null)
				{
					// This npc is on a bridge so we move it up 1 plane
					// TODO doesnt seem to work with an extended map loading of < 5
					cachedModel.translate(0, client.getScene().getTileHeights()[client.getPlane() + 1][posX][posY], 0);
				}
				else
				{
					cachedModel.translate(0, client.getScene().getTileHeights()[curLocationWorldPoint.getPlane()][40 + newLocal.getX() / 128][40 + newLocal.getY() / 128], 0);
				}
			}
		}

		rlobj.setModel(cachedModel);
		rlobj.setOrientation(orientation);

		FakeNPCMode oldMode = mode;
		//Set to hidden so that we can reset to what we were before and have everything work right
		setMode(FakeNPCMode.HIDDEN);
		setMode(oldMode);
	}

	/**
	 * When the real NPC spawns we want to set our movement goal to the real position.
	 * @param spawnedNPC
	 */
	public void lerpToAndHide(NPC spawnedNPC)
	{
		extractNPCData(spawnedNPC);
		this.realNPC = spawnedNPC;
		//Skip lerping if distance between is too great
		//TODO config option for this between skip lerp/run
		this.curLocationWorldPoint = WorldPoint.fromLocal(client, rlobj.getLocation());
		if (curLocationWorldPoint.distanceTo(spawnedNPC.getWorldLocation()) > 10)
		{
			setMode(FakeNPCMode.HIDDEN);
			return;
		}
		//TODO config option for this between skip lerp/run
		if (isAttackable)
		{
			this.shouldRun = true;
		}
		setMode(FakeNPCMode.LERP_TO_REAL);
	}

	/**
	 * When a real NPC despawns, we jump to their position and show ourselves
	 * @param despawnedNPC
	 */
	public void jumpToAndShow(NPC despawnedNPC)
	{
		extractNPCData(despawnedNPC);
		this.realNPC = null;
		this.setLocation(despawnedNPC.getLocalLocation());
		this.spawnWorldPoint = despawnedNPC.getWorldLocation();
		rlobj.setOrientation(despawnedNPC.getOrientation());
		setMode(FakeNPCMode.IDLE);
	}

	/**
	 * Called on plugin shut down or when this is no longer in scene
	 */
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
		if (mode == FakeNPCMode.HIDDEN)
		{
			return;
		}
		//If we have a realNPC, walk to them
		if (realNPC != null)
		{
			walkingDestination = realNPC.getLocalLocation();
			setMode(FakeNPCMode.LERP_TO_REAL);
		}
		//We don't have anywhere to walk to
		if (walkingDestination == null)
		{
			setMode(FakeNPCMode.IDLE);
			return;
		}
		LocalPoint curLocation = rlobj.getLocation();

		//TODO speed based on mode
		//Speed up the lerp by 2x if the distance is over 10 tiles
		int movementDelta = (int) (7 * (this.shouldRun ? 1.5 : 1));
		if (mode == FakeNPCMode.WANDER)
		{
			movementDelta = 5;
		}
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
		//Check that we aren't going inside a wall
		//TODO canTravelInDirection doesn't like extended scene
		LocalPoint lp = LocalPoint.fromWorld(client, curLocationWorldPoint.getX(), curLocationWorldPoint.getY());
		if (curLocationWorldPoint.isInScene(client)
			&& lp != null
			&& lp.getSceneX() + dx > 0
			&& lp.getSceneY() + dy > 0
			&& lp.getSceneX() + dx < 103
			&& lp.getSceneY() + dy < 103
			&& curLocationWorldPoint.toWorldArea().canTravelInDirection(client, dx, dy))
		{
			int newX = curLocation.getX() + dx;
			int newY = curLocation.getY() + dy;
			this.setLocation(newX, newY);
		}
		else
		{
			this.walkingDestination = null;
			if (mode == FakeNPCMode.AVOID_PLAYER || mode == FakeNPCMode.LERP_TO_REAL)
			{
				setMode(FakeNPCMode.HIDDEN);
			}
			else
			{
				setMode(FakeNPCMode.IDLE);
			}
		}


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

		//TODO detect looping around from max rotation to 0 as a faster method
		int dorient = Math.min(orientationDelta, Math.abs(rlobj.getOrientation() - orientationDestination));
		if (rlobj.getOrientation() > orientationDestination)
		{
			dorient *= -1;
		}
		int newOrientation = rlobj.getOrientation() + dorient;
		rlobj.setOrientation(newOrientation);

		if (walkingDestination != null
			&& rlobj.getLocation().distanceTo(walkingDestination) < 1
			&& rlobj.getOrientation() == orientationDestination)
		{
			if (mode == FakeNPCMode.LERP_TO_REAL)
			{
				setMode(FakeNPCMode.HIDDEN);
			}
			else
			{
				setMode(FakeNPCMode.IDLE);
			}
		}
	}

	/**
	 * Used for random wander, and to stay near the realNPC boundary line
	 */
	public void processGameTick()
	{
		//If we are walking too far away from our original location just stop showing
		if (spawnWorldPoint != null && spawnWorldPoint.distanceTo(curLocationWorldPoint) > 5 && mode == FakeNPCMode.AVOID_PLAYER)
		{
			setMode(FakeNPCMode.HIDDEN);
		}
		if (this.mode == FakeNPCMode.WANDER || this.mode == FakeNPCMode.IDLE || this.mode == FakeNPCMode.AVOID_PLAYER)
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
				walkingDestination = new LocalPoint(newX, newY);
				setMode(FakeNPCMode.AVOID_PLAYER);
			}
		}
		if (mode == FakeNPCMode.IDLE)
		{
			if (Math.random() * 6 < 1)
			{
				//TODO this will cause npcs displaced by AVOID_PLAYER to constantly try and path back to their spawn
				//TODO is that a bad thing? idk
				int dx = (int) ((Math.random() * 3) - 1.5) * 256;
				int dy = (int) ((Math.random() * 3) - 1.5) * 256;
				LocalPoint spawnLocation = LocalPoint.fromScene(spawnWorldPoint.getX() - client.getBaseX(), spawnWorldPoint.getY() - client.getBaseY());
				int newX = spawnLocation.getX() + dx;
				int newY = spawnLocation.getY() + dy;
				walkingDestination = new LocalPoint(newX, newY);
				setMode(FakeNPCMode.WANDER);
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
		String message = String.format("Name: %s Index: %d Mode: %s IdleAnim: %d WalkAnim: %d", this.composition.getName(), this.npcIndex, this.mode, this.idlePoseAnimation.getId(), this.walkAnimation.getId());
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
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

	/**
	 * Handles housekeeping of switching modes
	 * @param newMode
	 */
	private void setMode(FakeNPCMode newMode)
	{
		//No change, do nothing
		if (newMode == mode)
		{
			return;
		}

		//If the current mode is hidden, the new one must not be hidden, so unhide.
		if (mode == FakeNPCMode.HIDDEN)
		{
			rlobj.setActive(true);
		}
		if (newMode == FakeNPCMode.HIDDEN)
		{
			rlobj.setActive(false);
		}
		if (newMode == FakeNPCMode.IDLE)
		{
			if (idlePoseAnimation != null)
			{
				rlobj.setAnimation(idlePoseAnimation);
				rlobj.setShouldLoop(true);
			}
		}
		//If currently in any walking mode
		if (mode == FakeNPCMode.WANDER || mode == FakeNPCMode.AVOID_PLAYER || mode == FakeNPCMode.LERP_TO_REAL)
		{
			//We were walking, now we are idle
			if (newMode == FakeNPCMode.IDLE || newMode == FakeNPCMode.HIDDEN)
			{
				plugin.removeWalking(this);
			}
		}
		//Else we weren't walking, check if we are now walking.
		else if (newMode == FakeNPCMode.WANDER || newMode == FakeNPCMode.AVOID_PLAYER || newMode == FakeNPCMode.LERP_TO_REAL)
		{
			plugin.addWalking(this);
			if (walkAnimation != null)
			{
				rlobj.setAnimation(walkAnimation);
				rlobj.setShouldLoop(true);
			}
		}
		this.mode = newMode;
	}
}
