package carpet.mixins;

import carpet.fakes.ServerWorldInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Supplier;

import static carpet.script.CarpetEventServer.Event.EXPLOSION;
import static carpet.script.CarpetEventServer.Event.LIGHTNING;
import static carpet.script.CarpetEventServer.Event.CHUNK_UNLOADED;

@Mixin(ServerLevel.class)
public abstract class ServerLevel_scarpetMixin extends Level implements ServerWorldInterface
{

    @Shadow public abstract GameRules getGameRules();

    protected ServerLevel_scarpetMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, boolean bl, boolean bl2, long l, int i)
    {
        super(writableLevelData, resourceKey, registryAccess, holder, bl, bl2, l, i);
    }

    @Inject(method = "tickThunder", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            shift = At.Shift.BEFORE,
            ordinal = 1
    ))
    private void onNaturalLightinig(LevelChunk chunk, CallbackInfo ci,
                                    //ChunkPos chunkPos, boolean bl, int i, int j, Profiler profiler, BlockPos blockPos, boolean bl2)
                                    ChunkPos chunkPos, boolean bl, int i, int j, ProfilerFiller profiler, BlockPos blockPos, DifficultyInstance localDifficulty, boolean bl2, LightningBolt lightningEntity)
    {
        if (LIGHTNING.isNeeded()) LIGHTNING.onWorldEventFlag((ServerLevel) (Object)this, blockPos, bl2?1:0);
    }

    private Explosion.BlockInteraction getCMDestroyType(final GameRules.Key<GameRules.BooleanValue> rule) {
        return getGameRules().getBoolean(rule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void handleExplosion(final Entity entity, final DamageSource damageSource, final ExplosionDamageCalculator explosionDamageCalculator, final double x, final double y, final double z, final float g, final boolean bl, final ExplosionInteraction explosionInteraction, final ParticleOptions particleOptions, final ParticleOptions particleOptions2, final Holder<SoundEvent> holder, final CallbackInfo ci)
    {
        if (EXPLOSION.isNeeded()) {
            Explosion.BlockInteraction var10000 = switch (explosionInteraction) {
                case NONE -> Explosion.BlockInteraction.KEEP;
                case BLOCK -> this.getCMDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
                case MOB -> this.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? this.getCMDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY) : Explosion.BlockInteraction.KEEP;
                case TNT -> this.getCMDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
                case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
            };

            boolean cancelled = EXPLOSION.onExplosion((ServerLevel) (Object) this, entity, null, new Vec3(x, y, z), g, bl, null, null, var10000);
            if (cancelled) ci.cancel();
        }
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void handleChunkUnload(LevelChunk levelChunk, CallbackInfo ci)
    {
        if (CHUNK_UNLOADED.isNeeded())
        {
            ServerLevel level = (ServerLevel)((Object)this);
            CHUNK_UNLOADED.onChunkEvent(level, levelChunk.getPos(), false);
        }
    }

    @Final
    @Shadow
    private ServerLevelData serverLevelData;
    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

    @Unique
    @Override
    public ServerLevelData getWorldPropertiesCM(){
        return serverLevelData;
    }

    @Unique
    @Override
    public LevelEntityGetter<Entity> getEntityLookupCMPublic() {
        return entityManager.getEntityGetter();
    }
}
