package net.runelite.client.plugins.extendednpcloading;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class FakeNPC
{
	private Client client;
	private int npcIndex;
	private int idlePoseAnimation;
	private int orientation;
	private NPCComposition composition;
	private RuneLiteObject rlobj;
	private WorldPoint worldPoint;

	public FakeNPC(Client client, NPC npc)
	{
		this.client = client;
		npcIndex = npc.getIndex();
		composition = npc.getTransformedComposition();
		idlePoseAnimation = npc.getIdlePoseAnimation();
		orientation = npc.getOrientation();
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

	private void copyNPC() {
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

	public void lerpToAndHide(LocalPoint localPoint)
	{
		rlobj.setLocation(localPoint, client.getPlane());
		worldPoint = WorldPoint.fromLocal(client, rlobj.getLocation());
		rlobj.setActive(false);
	}

	public void jumpToAndShow(LocalPoint localPoint)
	{
		rlobj.setLocation(localPoint, client.getPlane());
		worldPoint = WorldPoint.fromLocal(client, rlobj.getLocation());
		rlobj.setActive(true);
	}

	public void shutDown()
	{
		this.rlobj.setActive(false);
	}
}
