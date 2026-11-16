package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.events.impl.EventSync;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {

    // ── Settings ─────────────────────────────────────────────────────────────
    public final Setting<Float>   minRange     = new Setting<>("MinRange",     5f,   1f,   50f);
    public final Setting<Float>   maxRange     = new Setting<>("MaxRange",     64f,  10f,  128f);
    
    // [POLAR BYPASS] - İsim Predict oldu, aralık 0.5 - 4.0 yapıldı.
    public final Setting<Float>   predict      = new Setting<>("Predict",      1.0f, 0.5f, 4.0f);
    
    public final Setting<Float>   delay        = new Setting<>("Delay",        5f,   1f,   20f);
    public final Setting<Rotations> rotations  = new Setting<>("Rotations",    Rotations.Sharp);
    public final Setting<Float>   smoothFactor = new Setting<>("SmoothFactor", 0.35f, 0.05f, 1.0f, v -> rotations.is(Rotations.Smooth));

    private enum Rotations { Sharp, Smooth }

    // ── State ─────────────────────────────────────────────────────────────────
    private long   lastFireworkMs = 0L;
    private float  smoothYaw      = 0f;
    private float  smoothPitch    = 0f;

    private static final int VEL_SAMPLES = 5;
    private final Vec3d[] velHistory = new Vec3d[VEL_SAMPLES];
    private int velIndex = 0;

    public ElytraTarget() {
        super("ElytraTarget", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        lastFireworkMs = 0L;
        java.util.Arrays.fill(velHistory, Vec3d.ZERO);
        if (!fullNullCheck()) {
            smoothYaw   = mc.player.getYaw();
            smoothPitch = mc.player.getPitch();
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (fullNullCheck()) return;
        if (!mc.player.isFallFlying()) return;

        var target = ModuleManager.aura.target;
        if (!(target instanceof PlayerEntity playerTarget)) return;
        if (!ModuleManager.aura.isEnabled()) return;

        double dist = mc.player.distanceTo(playerTarget);
        if (dist < minRange.getValue() || dist > maxRange.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - lastFireworkMs < (long)(delay.getValue() * 100)) return;

        int fireworkSlot = findFireworkSlot();
        if (fireworkSlot == -1) return;

        // ── Velocity smooth ───────────────────────────────────────────────────
        velHistory[velIndex % VEL_SAMPLES] = playerTarget.getVelocity();
        velIndex++;
        double svx = 0, svy = 0, svz = 0;
        int count = 0;
        for (Vec3d v : velHistory) {
            if (v == null) continue;
            svx += v.x; svy += v.y; svz += v.z;
            count++;
        }
        if (count == 0) return;
        Vec3d smoothVel = new Vec3d(svx / count, svy / count, svz / count);

        Vec3d targetCenter = playerTarget.getBoundingBox().getCenter();
        Vec3d eyePos       = mc.player.getEyePos();
        Vec3d toTarget     = targetCenter.subtract(eyePos).normalize();
        Vec3d fwVel        = mc.player.getVelocity();
        double fwSpeed     = 1.6;
        Vec3d fwVelVec     = new Vec3d(
            fwVel.x + toTarget.x * fwSpeed,
            fwVel.y + toTarget.y * fwSpeed,
            fwVel.z + toTarget.z * fwSpeed
        );

        // ── İteratif lead (Predict) ───────────────────────────────────────────
        Vec3d leadPos = targetCenter;
        for (int iter = 0; iter < 3; iter++) {
            double dx0 = leadPos.x - eyePos.x;
            double dy0 = leadPos.y - eyePos.y;
            double dz0 = leadPos.z - eyePos.z;
            double d0  = Math.sqrt(dx0*dx0 + dy0*dy0 + dz0*dz0);
            double speed = fwVelVec.length();
            if (speed < 0.01) speed = fwSpeed;
            double travelTicks = d0 / speed;
            leadPos = targetCenter.add(
                smoothVel.x * travelTicks * predict.getValue(),
                smoothVel.y * travelTicks * predict.getValue(),
                smoothVel.z * travelTicks * predict.getValue()
            );
        }

        // ── Rotation ──────────────────────────────────────────────────────────
        double dx = leadPos.x - eyePos.x;
        double dy = leadPos.y - eyePos.y;
        double dz = leadPos.z - eyePos.z;

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = MathHelper.clamp(
            (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))),
            -90f, 90f
        );

        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        double gcd  = Math.pow(f, 3.0) * (8.0 / 3.0);

        float targetYaw, targetPitch;
        if (rotations.is(Rotations.Smooth)) {
            smoothYaw   = smoothYaw   + (yaw   - smoothYaw)   * smoothFactor.getValue();
            smoothPitch = smoothPitch + (pitch - smoothPitch) * smoothFactor.getValue();
            targetYaw   = smoothYaw;
            targetPitch = smoothPitch;
        } else {
            targetYaw   = yaw;
            targetPitch = pitch;
        }

        float safeYaw   = (float)(targetYaw   - (targetYaw   - mc.player.getYaw())   % gcd);
        float safePitch = (float)(targetPitch - (targetPitch - mc.player.getPitch()) % gcd);

        int currentSlot = mc.player.getInventory().selectedSlot;
        
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            fireworkSlot,
            currentSlot,
            SlotActionType.SWAP,
            mc.player
        );
        
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            fireworkSlot,
            currentSlot,
            SlotActionType.SWAP,
            mc.player
        );

        lastFireworkMs = System.currentTimeMillis();
    }

    private int findFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FireworkRocketItem) return i;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FireworkRocketItem) return i;
        }
        return -1;
    }
}
