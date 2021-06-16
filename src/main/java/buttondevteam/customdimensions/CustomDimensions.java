package buttondevteam.customdimensions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;
import com.mojang.serialization.Lifecycle;
import net.minecraft.server.v1_16_R3.*;
//import org.bstats.bukkit.Metrics;
//import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CustomDimensions extends JavaPlugin implements Listener {
//	private Metrics metrics;

	@Override
	public void onEnable() {
//		metrics = new Metrics(this, 10545);
		getLogger().info("Loading custom dimensions...");
		try {
			load();
		} catch (Exception e) {
			e.printStackTrace();
		}
		getLogger().info("Finished loading custom dimensions!");
	}

	private void load() throws Exception {
		var console = ((CraftServer) Bukkit.getServer()).getServer();
		var field = console.getClass().getSuperclass().getDeclaredField("saveData");
		field.setAccessible(true);
		var saveData = (SaveData) field.get(console);
		GeneratorSettings mainGenSettings = saveData.getGeneratorSettings();
		RegistryMaterials<WorldDimension> dimensionRegistry = mainGenSettings.d();

		var mainWorld = Bukkit.getWorlds().get(0);

		var convertable = Convertable.a(Bukkit.getWorldContainer().toPath());

		if (!getConfig().contains("ignored")) {
			getConfig().set("ignored", Lists.newArrayList("single_biome"));
			saveConfig();
		}
		var ignored = getConfig().getStringList("ignored");
		int allCount = -3, loadedCount = 0, ignoredCount = 0; //-3: overworld, nether, end
		for (var dimEntry : dimensionRegistry.d()) {
			allCount++;
			if (ignored.contains(dimEntry.getKey().a().getKey())) {
				getLogger().info(dimEntry.getKey() + " is on the ignore list");
				ignoredCount++;
				continue;
			}
			try {
				if (loadDimension(dimEntry.getKey(), dimEntry.getValue(), convertable, console, mainWorld))
					loadedCount++;
			} catch (Exception e) {
				getLogger().warning("Failed to load dimension " + dimEntry.getKey());
				e.printStackTrace();
			}
		}
//		metrics.addCustomChart(new SimplePie("all_custom_dimensions", Callables.returning(allCount + "")));
//		metrics.addCustomChart(new SimplePie("loaded_custom_dimensions", Callables.returning(loadedCount + "")));
//		metrics.addCustomChart(new SimplePie("ignored_custom_dimensions", Callables.returning(ignoredCount + "")));
	}

	private boolean loadDimension(ResourceKey<WorldDimension> dimKey, WorldDimension dimension,
	                              Convertable convertable, DedicatedServer console, org.bukkit.World mainWorld) throws IOException {
		if (dimKey == WorldDimension.OVERWORLD //The default dimensions are already loaded
				|| dimKey == WorldDimension.THE_NETHER
				|| dimKey == WorldDimension.THE_END)
			return false;
		ResourceKey<World> worldKey = ResourceKey.a(IRegistry.L, dimKey.a());
		DimensionManager dimensionmanager = dimension.b();
		ChunkGenerator chunkgenerator = dimension.c();
		String name = getConfig().getString("worldNames." + dimKey.a());
		if (name == null)
			name = dimKey.a().getKey();
		if (Bukkit.getWorld(name) != null) {
			getLogger().info(name + " already loaded");
			return false;
		}
		getLogger().info("Loading " + name);
		var session = convertable.new ConversionSession(name, dimKey) { //The original session isn't prepared for custom dimensions
			@Override
			public File a(ResourceKey<World> resourcekey) {
				return new File(this.folder.toFile(), "custom");
			}
		};
		MinecraftServer.convertWorld(session);

		//Load world settings or create default values
		RegistryReadOps<NBTBase> registryreadops = RegistryReadOps.a(DynamicOpsNBT.a, console.dataPackResources.h(), console.customRegistry);
		WorldDataServer worlddata = (WorldDataServer) session.a(registryreadops, console.datapackconfiguration);
		if (worlddata == null) {
			Properties properties = new Properties();
			properties.put("level-seed", Objects.toString(mainWorld.getSeed()));
			properties.put("generate-structures", Objects.toString(true));
			properties.put("level-type", WorldType.NORMAL.getName());
			GeneratorSettings dimGenSettings = GeneratorSettings.a(console.getCustomRegistry(), properties);
			WorldSettings worldSettings = new WorldSettings(name,
					EnumGamemode.getById(Bukkit.getDefaultGameMode().getValue()),
					false, //Hardcore
					EnumDifficulty.EASY, false, new GameRules(), console.datapackconfiguration);
			worlddata = new WorldDataServer(worldSettings, dimGenSettings, Lifecycle.stable());
		}

		worlddata.checkName(name);
		worlddata.a(console.getServerModName(), console.getModded().isPresent());
		if (console.options.has("forceUpgrade")) {
			net.minecraft.server.v1_16_R3.Main.convertWorld(session, DataConverterRegistry.a(),
					console.options.has("eraseCache"), () -> true,
					worlddata.getGeneratorSettings().d().d().stream()
							.map((entry2) -> ResourceKey.a(IRegistry.K, entry2.getKey().a()))
							.collect(ImmutableSet.toImmutableSet()));
		}

		List<MobSpawner> spawners = ImmutableList.of(new MobSpawnerPhantom(), new MobSpawnerPatrol(), new MobSpawnerCat(), new VillageSiege(), new MobSpawnerTrader(worlddata));

		ResourceKey<DimensionManager> dimManResKey = ResourceKey.a(IRegistry.K, dimKey.a());
		var dimRegistry = ((RegistryMaterials<DimensionManager>) console.customRegistry.a());
		{
			var key = dimRegistry.getKey(dimensionmanager);
			if (key == null) { //The loaded manager is different - different dimension type
				//Replace existing dimension manager, correctly setting the ID up (which is -1 for default worlds...)
				dimRegistry.a(OptionalInt.empty(), dimManResKey, dimensionmanager, Lifecycle.stable());
			}
		}

		var worldloadlistener = console.worldLoadListenerFactory.create(11);

		WorldServer worldserver = new WorldServer(console, console.executorService, session,
				worlddata, worldKey, dimensionmanager, worldloadlistener, chunkgenerator,
				false, //isDebugWorld
				BiomeManager.a(worlddata.getGeneratorSettings().getSeed()), //Biome seed
				spawners,
				true, //Update world time
				org.bukkit.World.Environment.NORMAL, null);

		if (Bukkit.getWorld(name.toLowerCase(Locale.ENGLISH)) == null) {
			getLogger().warning("Failed to load custom dimension " + name);
			return false;
		} else {
			console.initWorld(worldserver, worlddata, worlddata, worlddata.getGeneratorSettings());
			worldserver.setSpawnFlags(true, true);
			console.worldServer.put(worldserver.getDimensionKey(), worldserver);
			Bukkit.getPluginManager().callEvent(new WorldInitEvent(worldserver.getWorld()));
			console.loadSpawn(worldserver.getChunkProvider().playerChunkMap.worldLoadListener, worldserver);
			Bukkit.getPluginManager().callEvent(new WorldLoadEvent(worldserver.getWorld()));
			return true;
		}
	}
}
