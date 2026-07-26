package net.lusik21556.rankedsmp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.lusik21556.rankedsmp.RankedSMP;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global, server-wide settings. Equivalent of the original plugin's config.yml
 * "hooks" / "gameplay" sections (the TAB / PlaceholderAPI / AltarSMP hooks from
 * the Paper plugin have no Fabric equivalent shipped here, so only the gameplay
 * toggle survives the port).
 */
public class RankedConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("rankedsmp.json");

	/** If true, ranks cannot be stolen through PvP kills. */
	public boolean keepRanks = false;

	public static RankedConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				RankedConfig loaded = GSON.fromJson(reader, RankedConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				RankedSMP.LOGGER.warn("Failed to read rankedsmp.json, using defaults", e);
			}
		}
		RankedConfig fresh = new RankedConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			RankedSMP.LOGGER.warn("Failed to save rankedsmp.json", e);
		}
	}

	public boolean isRankStealingEnabled() {
		return !keepRanks;
	}

	/** Re-reads the config file in place so already-injected references stay valid. */
	public void reload() {
		RankedConfig fresh = load();
		this.keepRanks = fresh.keepRanks;
	}
}
