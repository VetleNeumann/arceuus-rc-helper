package com.vetle.arceuusrc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ArceuusRcHelperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ArceuusRcHelperPlugin.class);
		RuneLite.main(args);
	}
}
