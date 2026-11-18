package polar.ware.utility.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PlayerManager {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * Returns [yaw, pitch] angles from player eyes to the given target position.
     */
    public static float[] calcAngle(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

        yaw   = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

        return new float[]{yaw, pitch};
    }
}
