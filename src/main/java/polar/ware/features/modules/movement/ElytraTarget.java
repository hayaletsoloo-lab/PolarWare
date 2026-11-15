package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.setting.Setting;

import static polar.ware.utility.math.MathUtility.random;

public class ElytraTarget extends Module {

    public final Setting<Float>   range        = new Setting<>("Range",        100f, 10f, 300f);
    public final Setting<Float>   leadMult     = new Setting<>("LeadMult",     0.7f, 0.1f, 3.0f);
    public final Setting<Float>   delay        = new Setting<>("Delay",        5f,   1f,   20f);
    public final Setting<Rotations> rotations  = new Setting<>("Rotations",    Rotations.Sharp);
    public final Setting<Float>   smoothFactor = new Setting<>("SmoothFactor", 0.35f, 0.05f, 1.0f,
                                                     v -> rotations.is(Rotations.Smooth));

    private enum Rotations { Sharp, Smooth }

    private long    lastFireworkMs = 0L;
    private float   smoothYaw      = 0f;
    private float   smoothPitch    = 0f;

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
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck()) return;
        if (!mc.player.isFallFlying()) return;

        PlayerEntity target = findTarget();
        if (target == null) return;

        long now = System.currentTimeMillis();
        if (now - lastFireworkMs < (long)(delay.getValue() * 100)) return;

        int fireworkSlot = findFireworkSlot();
        if (fireworkSlot == -1) return;

        // ── Velocity smooth ──────────────────────────────────────────────────
        velHistory[velIndex % VEL_SAMPLES] = target.getVelocity();
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

        // ── Lead simülasyonu ─────────────────────────────────────────────────
        Vec3d eyePos       = mc.player.getEyePos();
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        Vec3d fwVel        = mc.player.getVelocity();
        Vec3d toTarget     = targetCenter.subtract(eyePos).normalize();
        double fwSpeed     = 1.6;
        Vec3d fwVelVec     = new Vec3d(
            fwVel.x + toTarget.x * fwSpeed,
            fwVel.y + toTarget.y * fwSpeed,
            fwVel.z + toTarget.z * fwSpeed
        );

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
                smoothVel.x * travelTicks * leadMult.getValue(),
                smoothVel.y * travelTicks * leadMult.getValue(),
                smoothVel.z * travelTicks * leadMult.getValue()
            );
        }

        // ── Rotation hesapla ─────────────────────────────────────────────────
        double dx = leadPos.x - eyePos.x;
        double dy = leadPos.y - eyePos.y;
        double dz = leadPos.z - eyePos.z;

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));
        pitch = MathHelper.clamp(pitch, -90f, 90f);

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

        // ── Silent rotation packet ────────────────────────────────────────────
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.LookAndOnGround(safeYaw, safePitch, mc.player.isOnGround())
        );

        // ── Fişek kullan ─────────────────────────────────────────────────────
        if (fireworkSlot < 9) {
            int prev = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = fireworkSlot;
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id));
            mc.player.getInventory().selectedSlot = prev;
        } else {
            int hotbarSlot = mc.player.getInventory().selectedSlot;
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                fireworkSlot, hotbarSlot,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
            );
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id));
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                fireworkSlot, hotbarSlot,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
            );
        }

        lastFireworkMs = System.currentTimeMillis();
    }

    private PlayerEntity findTarget() {
        PlayerEntity closest = null;
        double closestDist = range.getValue() * range.getValue();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (ModuleManager.friendManager.isFriend(p.getName().getString())) continue;
            double dist = mc.player.squaredDistanceTo(p);
            if (dist < closestDist) {
                closestDist = dist;
                closest = p;
            }
        }
        return closest;
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
