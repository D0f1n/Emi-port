package dev.emi.emi.runtime;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * Client-side persistent state, written to {@code emi.json} in the game directory like the original.
 * The {@code favorites} and sidebar history sections are ported; the original's BoM recipe defaults
 * and hidden stacks sections load/save with their own rounds. TODO(bom)
 */
public class EmiPersistentData {
	public static final File FILE = new File("emi.json");
	public static final Gson GSON = new Gson().newBuilder().setPrettyPrinting().create();

	public static void save() {
		try {
			JsonObject json = new JsonObject();
			json.add("favorites", EmiFavorites.save());
			EmiSidebars.save(json);
			FileWriter writer = new FileWriter(FILE);
			GSON.toJson(json, writer);
			writer.close();
		} catch (Exception e) {
			EmiLog.error("Failed to write persistent data", e);
		}
	}

	public static void load() {
		if (!FILE.exists()) {
			return;
		}
		try {
			JsonObject json = GSON.fromJson(new FileReader(FILE), JsonObject.class);
			if (GsonHelper.isArrayNode(json, "favorites")) {
				EmiFavorites.load(GsonHelper.getAsJsonArray(json, "favorites"));
			}
			EmiSidebars.load(json);
		} catch (Exception e) {
			EmiLog.error("Failed to parse persistent data", e);
		}
	}
}
