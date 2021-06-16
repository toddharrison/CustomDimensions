package buttondevteam.customdimensions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.serialization.Lifecycle;
//import org.bstats.bukkit.Metrics;
//import org.bstats.charts.SimplePie;

import net.minecraft.core.IRegistryCustom;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.Main;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.PlayerChunkMap;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.progress.WorldLoadListener;
import net.minecraft.server.level.progress.WorldLoadListenerFactory;
import net.minecraft.server.packs.resources.IResourceManager;
//import net.minecraft.server.v1_16_R3.*;
import net.minecraft.core.IRegistry;
import net.minecraft.core.RegistryMaterials;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.resources.RegistryReadOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.datafix.DataConverterRegistry;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.MobSpawnerCat;
import net.minecraft.world.entity.npc.MobSpawnerTrader;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.MobSpawner;
import net.minecraft.world.level.WorldSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionManager;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.levelgen.GeneratorSettings;
import net.minecraft.world.level.levelgen.MobSpawnerPatrol;
import net.minecraft.world.level.levelgen.MobSpawnerPhantom;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.SaveData;

import net.minecraft.world.level.storage.WorldDataServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;

//import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;

import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

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
        CraftServer craftServer = ((CraftServer) Bukkit.getServer());
        DedicatedServer console = craftServer.getServer();
//        Field field = console.getClass().getSuperclass().getDeclaredField("saveData");
        Field field = console.getClass().getSuperclass().getDeclaredField("o");
        field.setAccessible(true);
        SaveData saveData = (SaveData) field.get(console);
        GeneratorSettings mainGenSettings = saveData.getGeneratorSettings();
        RegistryMaterials<WorldDimension> dimensionRegistry = mainGenSettings.d();

        List<World> worlds = Bukkit.getWorlds();
        World mainWorld = worlds.get(0);

        File worldContainer = Bukkit.getWorldContainer();
        Convertable convertable = Convertable.a(worldContainer.toPath());

        if (!getConfig().contains("ignored")) {
            getConfig().set("ignored", Lists.newArrayList("single_biome"));
            saveConfig();
        }
        List<String> ignored = getConfig().getStringList("ignored");
        int allCount = -3, loadedCount = 0, ignoredCount = 0; //-3: overworld, nether, end
        for (Map.Entry<ResourceKey<WorldDimension>, WorldDimension> dimEntry : dimensionRegistry.d()) {
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

    private boolean loadDimension(ResourceKey<WorldDimension> dimKey,
                                  WorldDimension dimension,
                                  Convertable convertable,
                                  DedicatedServer console,
                                  org.bukkit.World mainWorld
    ) throws IOException {
//		if (dimKey == WorldDimension.OVERWORLD //The default dimensions are already loaded
//				|| dimKey == WorldDimension.THE_NETHER
//				|| dimKey == WorldDimension.THE_END)
//			return false;
        if (dimKey == WorldDimension.b //The default dimensions are already loaded
                || dimKey == WorldDimension.c
                || dimKey == WorldDimension.d)
            return false;
//		ResourceKey<net.minecraft.world.level.World> worldKey = ResourceKey.a(IRegistry.L, dimKey.a());
        ResourceKey<net.minecraft.world.level.World> worldKey = ResourceKey.a(IRegistry.Q, dimKey.a());
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
        Convertable.ConversionSession session = convertable.new ConversionSession(name, dimKey) { //The original session isn't prepared for custom dimensions
            @Override
            public File a(ResourceKey<net.minecraft.world.level.World> resourcekey) {
//				return new File(this.folder.toFile(), "custom");
                return new File(this.c.toFile(), "custom");
            }
        };
        MinecraftServer.convertWorld(session);

        //Load world settings or create default values
//		RegistryReadOps<NBTBase> registryreadops = RegistryReadOps.a(DynamicOpsNBT.a, console.dataPackResources.h(), console.customRegistry);
        DynamicOpsNBT don = DynamicOpsNBT.a;
//		IResourceManager rm = console.dataPackResources.h();
        IResourceManager rm = console.aC.i();
//		IRegistryCustom.Dimension cr = console.customRegistry;
        IRegistryCustom.Dimension cr = console.l;
        RegistryReadOps<NBTBase> registryreadops = RegistryReadOps.a(don, rm, cr);
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
//					EnumDifficulty.EASY, false, new GameRules(), console.datapackconfiguration);
                    EnumDifficulty.b, false, new GameRules(), console.datapackconfiguration);
            worlddata = new WorldDataServer(worldSettings, dimGenSettings, Lifecycle.stable());
        }

        worlddata.checkName(name);
        worlddata.a(console.getServerModName(), console.getModded().isPresent());
        if (console.options.has("forceUpgrade")) {
//			net.minecraft.server.v1_16_R3.Main.convertWorld(session, DataConverterRegistry.a(),

            GeneratorSettings gs = worlddata.getGeneratorSettings();
            RegistryMaterials<WorldDimension> rm2 = gs.d();
            Set<Map.Entry<ResourceKey<WorldDimension>, WorldDimension>> set = rm2.d();
            Stream<Map.Entry<ResourceKey<WorldDimension>, WorldDimension>> stream = set.stream();

            Main.convertWorld(session, DataConverterRegistry.a(),
                    console.options.has("eraseCache"), () -> true,
//					worlddata.getGeneratorSettings().d().d().stream()
//							.map((entry2) -> ResourceKey.a(IRegistry.K, entry2.getKey().a()))
//							.collect(ImmutableSet.toImmutableSet()));
                    stream
                            .map((entry2) -> {
//								ResourceKey<IRegistry<DimensionManager>> k = IRegistry.K;
                                ResourceKey<IRegistry<DimensionManager>> k = IRegistry.P;
                                ResourceKey<WorldDimension> mk1 = entry2.getKey();
                                MinecraftKey mk2 = mk1.a();
                                return ResourceKey.a(k, mk2);
                            })
                            .collect(ImmutableSet.toImmutableSet()));
        }

        List<MobSpawner> spawners = ImmutableList.of(new MobSpawnerPhantom(), new MobSpawnerPatrol(), new MobSpawnerCat(), new VillageSiege(), new MobSpawnerTrader(worlddata));

//		ResourceKey<DimensionManager> dimManResKey = ResourceKey.a(IRegistry.K, dimKey.a());
//		ResourceKey<IRegistry<DimensionManager>> k = IRegistry.K;
        ResourceKey<IRegistry<DimensionManager>> k = IRegistry.P;
        MinecraftKey v = dimKey.a();
        ResourceKey<DimensionManager> dimManResKey = ResourceKey.a(k, v);
//		IRegistryCustom.Dimension cr2 = console.customRegistry;
        IRegistryCustom.Dimension cr2 = console.l;
//		IRegistry<DimensionManager> foo = cr2.a();
        IRegistry<DimensionManager> foo = cr2.b(IRegistry.P);
        RegistryMaterials<DimensionManager> dimRegistry = ((RegistryMaterials<DimensionManager>) foo);
        {
            MinecraftKey key = dimRegistry.getKey(dimensionmanager);
            if (key == null) { //The loaded manager is different - different dimension type
                //Replace existing dimension manager, correctly setting the ID up (which is -1 for default worlds...)
                dimRegistry.a(OptionalInt.empty(), dimManResKey, dimensionmanager, Lifecycle.stable());
            }
        }

//		WorldLoadListenerFactory wllf = console.worldLoadListenerFactory;
        WorldLoadListenerFactory wllf = console.L;
        WorldLoadListener worldloadlistener = wllf.create(11);

//		Executor e = console.executorService;
        Executor e = console.aA;
        WorldServer worldserver = new WorldServer(console, e, session,
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
//			Map<ResourceKey<net.minecraft.server.v1_16_R3.World>, WorldServer> m = console.worldServer;
            Map<ResourceKey<net.minecraft.world.level.World>, WorldServer> m = console.R;
            m.put(worldserver.getDimensionKey(), worldserver);
            Bukkit.getPluginManager().callEvent(new WorldInitEvent(worldserver.getWorld()));
//			ChunkProviderServer cps = worldserver.chunkProvider;
            ChunkProviderServer cps = worldserver.C;
//			PlayerChunkMap pcm = cps.playerChunkMap;
            PlayerChunkMap pcm = cps.a;
//			WorldLoadListener wll = pcm.worldLoadListener;
            WorldLoadListener wll = pcm.z;
            console.loadSpawn(wll, worldserver);
            Bukkit.getPluginManager().callEvent(new WorldLoadEvent(worldserver.getWorld()));
            return true;
        }
    }
}
