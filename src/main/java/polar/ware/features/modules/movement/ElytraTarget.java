package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.core.Managers;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {

    public final Setting<Float>     range        = new Setting<>("Range",        100f, 10f, 300f);
    public final Setting<Float>     leadMult     = new Setting<>("LeadMult",     0.7f, 0.1f, 3.0f);
    public final Setting<Float>     delay        = new Setting<>("Delay",        5f,   1f,   20f);
    public final Setting<FireMode>  fireMode     = new Setting<>("FireMode",     FireMode.Silent);
    public final Setting<Rotations> rotations    = new Setting<>("Rotations",    Rotations.Sharp);
    public final Setting<Float>     smoothFactor = new Setting<>("SmoothFactor", 0.35f, 0.05f, 1.0f,
                                                       v -> rotations.is(Rotations.Smooth));

    private enum Rotations { Sharp, Smooth }
    private enum FireMode  { Silent, SwordSwap }

    private long  lastFireworkMs = 0L;
    private float smoothYaw      = 0f;
    private float smoothPitch    = 0f;

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

        // Velocity smooth
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

        // Lead
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
        for (int i = 0; i < 3; i++) {
            double dx0 = leadPos.x - eyePos.x;
            double dy0 = leadPos.y - eyePos.y;
            double dz0 = leadPos.z - eyePos.z;
            double d0  = Math.sqrt(dx0*dx0 + dy0*dy0 + dz0*dz0);
            double spd = fwVelVec.length();
            if (spd < 0.01) spd = fwSpeed;
            double t = d0 / spd;
            leadPos = targetCenter.add(
                smoothVel.x * t * leadMult.getValue(),
                smoothVel.y * t * leadMult.getValue(),
                smoothVel.z * t * leadMult.getValue()
            );
        }

        // Rotation
        double dx = leadPos.x - eyePos.x;
        double dy = leadPos.y - eyePos.y;
        double dz = leadPos.z - eyePos.z;

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));
        pitch = MathHelper.clamp(pitch, -90f, 90f);

        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        double gcd  = Math.pow(f, 3.0) * (8.0 / 3.0);

        float tYaw, tPitch;
        if (rotations.is(Rotations.Smooth)) {
            smoothYaw   += (yaw   - smoothYaw)   * smoothFactor.getValue();
            smoothPitch += (pitch - smoothPitch) * smoothFactor.getValue();
            tYaw   = smoothYaw;
            tPitch = smoothPitch;
        } else {
            tYaw   = yaw;
            tPitch = pitch;
        }

        float safeYaw   = (float)(tYaw   - (tYaw   - mc.player.getYaw())   % gcd);
        float safePitch = (float)(tPitch - (tPitch - mc.player.getPitch()) % gcd);

        // Her tick rotation paketi gönder (targeting)
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.LookAndOnGround(safeYaw, safePitch, mc.player.isOnGround())
        );

        // Delay check
        long now = System.currentTimeMillis();
        if (now - lastFireworkMs < (long)(delay.getValue() * 100)) return;

        int fwSlot = findFireworkSlot();
        if (fwSlot == -1) return;

        if (fireMode.is(FireMode.Silent)) {
            // Silent: slot paketi yok
            if (fwSlot < 9) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = fwSlot;
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
                mc.player.getInventory().selectedSlot = prev;
            } else {
                int hotbar = mc.player.getInventory().selectedSlot;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fwSlot, hotbar,
                    net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fwSlot, hotbar,
                    net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
            }
        } else {
            // SwordSwap: fişek bas → kılıca geç
            int swordSlot = findSwordSlot();
            if (fwSlot < 9) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = fwSlot;
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
                mc.player.getInventory().selectedSlot = (swordSlot >= 0 && swordSlot < 9) ? swordSlot : prev;
            } else {
                int hotbar = mc.player.getInventory().selectedSlot;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fwSlot, hotbar,
                    net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fwSlot, hotbar,
                    net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                if (swordSlot >= 0 && swordSlot < 9)
                    mc.player.getInventory().selectedSlot = swordSlot;
            }
        }

        lastFireworkMs = System.currentTimeMillis();
    }

    private PlayerEntity findTarget() {
        PlayerEntity closest = null;
        double maxDist = (double) range.getValue() * range.getValue();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (Managers.FRIEND.isFriend(p)) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < maxDist) { maxDist = d; closest = p; }
        }
        return closest;
    }

    private int findFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof FireworkRocketItem) return i;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof FireworkRocketItem) return i;
        }
        return -1;
    }

    private int findSwordSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof SwordItem) return i;
        }
        return -1;
    }
}
