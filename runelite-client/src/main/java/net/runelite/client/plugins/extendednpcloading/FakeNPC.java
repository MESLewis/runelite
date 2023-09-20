package net.runelite.client.plugins.extendednpcloading;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Max distance between a real NPC and the player is 15 tiles.
 */
public class FakeNPC
{
	private Client client;
	private ExtendedNPCsPlugin plugin;
	private int npcIndex;
	private int idlePoseAnimation;
	private int walkAnimation;
	private int orientation;
	private boolean isAttackable;
	private boolean shouldRun;
	private NPCComposition composition;
	private RuneLiteObject rlobj;
	private WorldPoint worldPoint;
	private NPC realNPC;
	private LocalPoint walkingDestination;

	public FakeNPC(ExtendedNPCsPlugin plugin, Client client, NPC npc)
	{
		this.client = client;
		this.plugin = plugin;
		npcIndex = npc.getIndex();
		composition = npc.getTransformedComposition();
		idlePoseAnimation = npc.getIdlePoseAnimation();
		walkAnimation = npc.getWalkAnimation();
		orientation = npc.getOrientation();
		isAttackable = npc.getCombatLevel() > 0;
		realNPC = npc;
		copyNPC();
	}

	public void recreate()
	{
		//TODO doesn't work for extended scene
		LocalPoint newLocal = LocalPoint.fromWorld(client, worldPoint);
		if (newLocal != null)
		{
			copyNPC();
			rlobj.setLocation(newLocal, client.getPlane());
		}
	}

	private void copyNPC()
	{
		rlobj = client.createRuneLiteObject();

		int[] modelIds = composition.getModels();
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
		if(isAttackable)
		{
			for (int i = 0; i < mData.getFaceColors().length; i++)
			{
				// The game uses bitpacked HSL where bit 8-10 control the saturation(HHHHHHSSSLLLLLLL)
				// 64639 is bitmask 1111110001111111 which will completely desaturate a color
				mData.recolor(mData.getFaceColors()[i], (short) (mData.getFaceColors()[i] & 64639));
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
		rlobj.setAnimation(client.loadAnimation(animation));
		rlobj.setShouldLoop(true);
		//TODO I think we need to use the rotate before lighting sometimes
		rlobj.setOrientation(orientation);
		rlobj.setActive(true);
	}

	public void lerpToAndHide(NPC spawnedNPC)
	{
		if(worldPoint.distanceTo(spawnedNPC.getWorldLocation()) > 10)
		{
			this.shouldRun = true;
		}
		//skip lerping if the target can be attacked
		if(isAttackable)
		{
			this.worldPoint = spawnedNPC.getWorldLocation();
			this.rlobj.setLocation(spawnedNPC.getLocalLocation(), client.getPlane());
			this.rlobj.setOrientation(spawnedNPC.getOrientation());
		}
		this.realNPC = spawnedNPC;
		rlobj.setAnimation(client.loadAnimation(walkAnimation));
		rlobj.setShouldLoop(true);
		plugin.addWalking(this);
	}

	public void jumpToAndShow(NPC despawnedNPC)
	{
		this.realNPC = null;
		rlobj.setLocation(despawnedNPC.getLocalLocation(), client.getPlane());
		worldPoint = WorldPoint.fromLocal(client, rlobj.getLocation());
		rlobj.setAnimation(client.loadAnimation(idlePoseAnimation));
		rlobj.setOrientation(despawnedNPC.getOrientation());
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
		final int movementDelta = 7;
		//Speed up the lerp by 2x if the distance is over 10 tiles
		final int movementSpeed = this.shouldRun ? -2 : -1;
		int dx = Math.min(movementDelta, Math.abs(curLocation.getX() - walkingDestination.getX()));
		int dy = Math.min(movementDelta, Math.abs(curLocation.getY() - walkingDestination.getY()));
		if (curLocation.getX() > walkingDestination.getX())
		{
			dx *= movementSpeed;
		}
		if (curLocation.getY() > walkingDestination.getY())
		{
			dy *= movementSpeed;
		}

		int newX = curLocation.getX() + dx;
		int newY = curLocation.getY() + dy;
		rlobj.setLocation(new LocalPoint(newX, newY), client.getPlane());

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
				walkingDestination = new LocalPoint(newX, newY);
				plugin.addWalking(this);
			}

		}
	}
}
