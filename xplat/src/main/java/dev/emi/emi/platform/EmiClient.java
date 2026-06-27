package dev.emi.emi.platform;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public class EmiClient {
	private static final Logger LOGGER = System.getLogger("emi");

	public static void init() {
		LOGGER.log(Level.INFO, "EMI 26.2 port skeleton: client init");
	}
}
