package dev.emi.emi.platform;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import dev.emi.emi.runtime.EmiSyncedRecipes;

public class EmiClient {
	private static final Logger LOGGER = System.getLogger("emi");
	/** Whether the connected server has EMI (set by the ping packet on join). */
	public static boolean onServer = false;

	public static void init() {
		LOGGER.log(Level.INFO, "EMI 26.2 port skeleton: client init");
	}

	/** Called from the loaders' client disconnect events. */
	public static void onDisconnect() {
		onServer = false;
		EmiSyncedRecipes.clear();
	}
}
