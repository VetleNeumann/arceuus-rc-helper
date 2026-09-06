package com.vetle.arceuusrc;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RcModeTest
{
	@Test
	public void configuredRuneWinsRegardlessOfLevel()
	{
		assertEquals(RcMode.BLOOD, RcMode.resolve(RcMode.BLOOD, 99));
		assertEquals(RcMode.SOUL, RcMode.resolve(RcMode.SOUL, 1));
	}

	@Test
	public void autoFlipsToSoulAtTheSoulAltarLevel()
	{
		assertEquals(RcMode.BLOOD, RcMode.resolve(RcMode.AUTO, RcMode.SOUL_LEVEL - 1));
		assertEquals(RcMode.SOUL, RcMode.resolve(RcMode.AUTO, RcMode.SOUL_LEVEL));
	}
}
