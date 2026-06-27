package dev.emi.emi.platform;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public class EmiMain {
	private static final Logger LOGGER = System.getLogger("emi");

	public static void init() {
		// Touching EmiAgnos triggers the platform delegate bootstrap, so this also
		// confirms the loader abstraction resolves at startup.
		LOGGER.log(Level.INFO, "EMI 26.2 port skeleton: common init (loader=" + EmiAgnos.getLoaderName() + ")");
	}
}
