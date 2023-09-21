package net.runelite.client.plugins.extendednpcloading;

import lombok.Data;

@Data
public class NPCSpawnDefinition
{
	private int id; //NPC id
	private int level; //Plane
	private String name;
	private int x;
	private int y;
}
