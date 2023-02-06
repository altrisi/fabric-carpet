package carpet.script.external;

import carpet.CarpetSettings;
import carpet.fakes.BiomeInterface;
import carpet.fakes.BlockStateArgumentInterface;
import carpet.fakes.ChunkTicketManagerInterface;
import carpet.fakes.IngredientInterface;
import carpet.fakes.MinecraftServerInterface;
import carpet.fakes.RandomStateVisitorAccessor;
import carpet.fakes.RecipeManagerInterface;
import carpet.fakes.ServerChunkManagerInterface;
import carpet.fakes.ServerWorldInterface;
import carpet.fakes.SpawnHelperInnerInterface;
import carpet.fakes.ThreadedAnvilChunkStorageInterface;
import carpet.mixins.Objective_scarpetMixin;
import carpet.mixins.Scoreboard_scarpetMixin;
import carpet.script.CarpetScriptServer;
import carpet.utils.SpawnReporter;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class Vanilla
{
    public static void MinecraftServer_forceTick(final MinecraftServer server, final BooleanSupplier sup)
    {
        ((MinecraftServerInterface)server).forceTick(sup);
    }

    public static void ChunkMap_relightChunk(final ChunkMap chunkMap, ChunkPos pos)
    {
        ((ThreadedAnvilChunkStorageInterface) chunkMap).relightChunk(pos);
    }

    public static Map<String, Integer> ChunkMap_regenerateChunkRegion(ChunkMap chunkMap, List<ChunkPos> requestedChunks)
    {
        return ((ThreadedAnvilChunkStorageInterface) chunkMap).regenerateChunkRegion(requestedChunks);
    }

    public static List<Collection<ItemStack>> Ingredient_getRecipeStacks(final Ingredient ingredient)
    {
        return ((IngredientInterface) (Object) ingredient).getRecipeStacks();
    }

    public static List<Recipe<?>> RecipeManager_getAllMatching(final RecipeManager recipeManager, final RecipeType<?> type, final ResourceLocation output, final RegistryAccess registryAccess)
    {
        return ((RecipeManagerInterface) recipeManager).getAllMatching(type, output, registryAccess);
    }

    public static int NaturalSpawner_MAGIC_NUMBER() {
        return SpawnReporter.MAGIC_NUMBER;
    }

    public static PotentialCalculator SpawnState_getPotentialCalculator(final NaturalSpawner.SpawnState spawnState)
    {
        return ((SpawnHelperInnerInterface) spawnState).getPotentialCalculator();
    }

    public static void Objective_setCriterion(final Objective objective, final ObjectiveCriteria criterion)
    {
        ((Objective_scarpetMixin) objective).setCriterion(criterion);
    }

    public static Map<ObjectiveCriteria, List<Objective>> Scoreboard_getObjectivesByCriterion(final Scoreboard scoreboard)
    {
        return ((Scoreboard_scarpetMixin) scoreboard).getObjectivesByCriterion();
    }

    public static ServerLevelData ServerLevel_getWorldProperties(final ServerLevel world)
    {
        return ((ServerWorldInterface) world).getWorldPropertiesCM();
    }

    public static DistanceManager ServerChunkCache_getCMTicketManager(final ServerChunkCache chunkCache)
    {
        return ((ServerChunkManagerInterface) chunkCache).getCMTicketManager();
    }

    public static Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> ChunkTicketManager_getTicketsByPosition(final DistanceManager ticketManager)
    {
        return ((ChunkTicketManagerInterface) ticketManager).getTicketsByPosition();
    }

    public static DensityFunction.Visitor RandomState_getVisitor(final RandomState randomState)
    {
        return ((RandomStateVisitorAccessor) (Object) randomState).getVisitor();
    }

    public static CompoundTag BlockInput_getTag(final BlockInput blockInput)
    {
        return ((BlockStateArgumentInterface) blockInput).getCMTag();
    }

    public static CarpetScriptServer MinecraftServer_getScriptServer(final MinecraftServer server)
    {
        return ((MinecraftServerInterface)server).getScriptServer();
    }

    public static Biome.ClimateSettings Biome_getClimateSettings(final Biome biome)
    {
        return ((BiomeInterface) (Object) biome).getClimateSettings();
    }

    public static ThreadLocal<Boolean> skipGenerationChecks(final ServerLevel level)
    { // not sure does vanilla care at all - needs checking
        return CarpetSettings.skipGenerationChecks;
    }

}
