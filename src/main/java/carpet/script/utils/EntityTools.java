package carpet.script.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EntityTools
{
    /**
     * Not a replacement for living entity jump() - this barely is to allow other entities that can't jump in vanilla to 'jump'
     *
     * @param e
     */
    public static void genericJump(final Entity e)
    {
        if (!e.isOnGround() && !e.isInWaterOrBubble() && !e.isInLava())
        {
            return;
        }
        final float m = e.level.getBlockState(e.blockPosition()).getBlock().getJumpFactor();
        final float g = e.level.getBlockState(new BlockPos(e.getX(), e.getBoundingBox().minY - 0.5000001D, e.getZ())).getBlock().getJumpFactor();
        final float jumpVelocityMultiplier = m == 1.0D ? g : m;
        final float jumpStrength = (0.42F * jumpVelocityMultiplier);
        final Vec3 vec3d = e.getDeltaMovement();
        e.setDeltaMovement(vec3d.x, jumpStrength, vec3d.z);
        if (e.isSprinting())
        {
            final float u = e.getYRot() * 0.017453292F; // yaw
            e.setDeltaMovement(e.getDeltaMovement().add((-Mth.sin(g) * 0.2F), 0.0D, (Mth.cos(u) * 0.2F)));
        }
        e.hasImpulse = true;
    }
}
