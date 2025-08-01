package carpet.mixins;

import carpet.CarpetSettings;
import carpet.fakes.BlockEntityInterface;
import carpet.fakes.PistonBlockEntityInterface;
import carpet.fakes.LevelInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntity_movableBEMixin extends BlockEntity implements PistonBlockEntityInterface
{
    @Shadow
    private boolean isSourcePiston;
    @Shadow
    private BlockState movedState;
    
    private BlockEntity carriedBlockEntity;
    private boolean renderCarriedBlockEntity = false;
    private boolean renderSet = false;

    public PistonMovingBlockEntity_movableBEMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }


    /**
     * @author 2No2Name
     */
    @Override
    public BlockEntity getCarriedBlockEntity()
    {
        return carriedBlockEntity;
    }

    @Override
    public void setLevel(Level world) {
        super.setLevel(world);
        if (carriedBlockEntity != null) carriedBlockEntity.setLevel(world);
    }

    @Override
    public void setCarriedBlockEntity(BlockEntity blockEntity)
    {
        this.carriedBlockEntity = blockEntity;
        if (this.carriedBlockEntity != null)
        {
            ((BlockEntityInterface)carriedBlockEntity).setCMPos(worldPosition);
            // this might be little dangerous since pos is final for a hashing reason?
            if (level != null) carriedBlockEntity.setLevel(level);
        }
        //    this.carriedBlockEntity.setPos(this.pos);
    }

    @Override
    public boolean isRenderModeSet()
    {
        return renderSet;
    }

    @Override
    public boolean getRenderCarriedBlockEntity()
    {
        return renderCarriedBlockEntity;
    }

    @Override
    public void setRenderCarriedBlockEntity(boolean b)
    {
        renderCarriedBlockEntity = b;
        renderSet = true;
    }
    
    /**
     * @author 2No2Name
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
              target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean movableTEsetBlockState0(
            Level world, BlockPos blockPos_1, BlockState blockAState_2, int int_1,
            Level world2, BlockPos blockPos, BlockState blockState, PistonMovingBlockEntity pistonBlockEntity)
    {
        if (!CarpetSettings.movableBlockEntities)
            return world.setBlock(blockPos_1, blockAState_2, int_1);
        else
            return ((LevelInterface) (world)).setBlockStateWithBlockEntity(blockPos_1, blockAState_2, ((PistonBlockEntityInterface)pistonBlockEntity).getCarriedBlockEntity(), int_1);
    }
    
    @Redirect(method = "finalTick", at = @At(value = "INVOKE",
              target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean movableTEsetBlockState1(Level world, BlockPos blockPos_1, BlockState blockState_2, int int_1)
    {
        if (!CarpetSettings.movableBlockEntities)
            return world.setBlock(blockPos_1, blockState_2, int_1);
        else
        {
            boolean ret = ((LevelInterface) (world)).setBlockStateWithBlockEntity(blockPos_1, blockState_2, this.carriedBlockEntity, int_1);
            this.carriedBlockEntity = null; //this will cancel the finishHandleBroken
            return ret;
        }
    }
    
    @Inject(method = "finalTick", at = @At(value = "RETURN"))
    private void finishHandleBroken(CallbackInfo cir)
    {
        //Handle TNT Explosions or other ways the moving Block is broken
        //Also /setblock will cause this to be called, and drop e.g. a moving chest's contents.
        // This is MC-40380 (BlockEntities that aren't Inventories drop stuff when setblock is called )
        if (CarpetSettings.movableBlockEntities && this.carriedBlockEntity != null && !this.level.isClientSide() && this.level.getBlockState(this.worldPosition).getBlock() == Blocks.AIR)
        {
            BlockState blockState_2;
            if (this.isSourcePiston)
                blockState_2 = Blocks.AIR.defaultBlockState();
            else
                blockState_2 = Block.updateFromNeighbourShapes(this.movedState, this.level, this.worldPosition);
            ((LevelInterface) (this.level)).setBlockStateWithBlockEntity(this.worldPosition, blockState_2, this.carriedBlockEntity, 3);
            this.level.destroyBlock(this.worldPosition, false, null);
        }
    }
    
    @Inject(method = "loadAdditional", at = @At(value = "TAIL"))
    private void onFromTag(ValueInput valueInput, CallbackInfo ci)
    {
        if (CarpetSettings.movableBlockEntities)
            valueInput.child("carriedTileEntityCM").ifPresent(tag -> {
                if (this.movedState.getBlock() instanceof EntityBlock)
                    this.carriedBlockEntity = ((EntityBlock) (this.movedState.getBlock())).newBlockEntity(worldPosition, movedState);//   this.world);
                if (carriedBlockEntity != null) //Can actually be null, as BlockPistonMoving.createNewTileEntity(...) returns null
                    this.carriedBlockEntity.loadWithComponents(tag);
                setCarriedBlockEntity(carriedBlockEntity);
            });
    }
    
    @Inject(method = "saveAdditional", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private void onToTag(ValueOutput valueOutput, CallbackInfo ci)
    {
        if (CarpetSettings.movableBlockEntities && this.carriedBlockEntity != null && valueOutput instanceof TagValueOutput output)
        {
            //Leave name "carriedTileEntityCM" instead of "carriedBlockEntityCM" for upgrade compatibility with 1.13.2 movable TE
            this.carriedBlockEntity.saveWithoutMetadata(output.child("carriedTileEntityCM"));
        }
    }
}
