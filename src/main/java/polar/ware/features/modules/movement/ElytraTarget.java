package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {

    // ── Settings ────────────────────────────────────────────────────────────
    public final Setting<Float>   minRange     = new Setting<>("MinRange",     3f, 1f,  100f);
    public final Setting<Float>   maxRange     = new Setting<>("MaxRange",     60f, 1f, 100f);
    public final Setting<Float>   leadMult     = new Setting<>("LeadMult",     0.7f, 0.1f, 3.0f);
    public final Setting<Float>   delay        = new Setting<>("Delay", 5f, 1f, 20f);
    public final Setting<Boolean> snap         = new Setting<>("Snap", true);
    public final Setting<Rotations> rotations  = new Setting<>("Rotations", Rotations.Sharp);
    public final Setting<Float>   smoothFactor = new Setting<>("SmoothFactor", 0.35f, 0.05f, 1.0f, v -> rotations.is(Rotations.Smooth));

    private enum Rotations { Sharp, Smooth }

    private long lastFireworkMs = 0L;
    private float smoothYaw   = 0f;
    private float smoothPitch = 0f;

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
        // Sadece sen uçarken çalış (yerdeyken fişek basmanın anlamı yok)
        if (!mc.player.isFallFlying() || mc.currentScreen != null) return;

        // ── KENDİ HEDEFİNİ BUL (Aura'dan Bağımsız) ──
        PlayerEntity target = null;
        double closestDist = Double.MAX_VALUE;
        
        for (Entity entity : mc.world.getEntities()) {
            // Uçan veya uçmayan tüm oyuncuları hedef alır (p.isFallFlying kontrolü YOK)
            if (entity instanceof PlayerEntity p && p != mc.player && p.isAlive()) {
                double dist = mc.player.distanceTo(p);
                if (dist >= minRange.getValue() && dist <= maxRange.getValue() && dist < closestDist) {
                    closestDist = dist;
                    target = p;
                }
            }
        }

        if (target == null) return;

        // ── Delay Kontrolü ──
        long now = System.currentTimeMillis();
        if (now - lastFireworkMs < (long)(delay.getValue() * 100)) return;

        int fireworkSlot = findFireworkSlot();
        if (fireworkSlot == -1) return;

        // ── Velocity smooth ──
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

        Vec3d targetCenter = target.getBoundingBox().getCenter();
        Vec3d eyePos   = mc.player.getEyePos();
        Vec3d toTarget = targetCenter.subtract(eyePos).normalize();
        Vec3d fwVel    = mc.player.getVelocity();
        double fwSpeed = 1.6;
        Vec3d fwVelVec = new Vec3d(
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

        // ── SNAP ──
        Vec3d finalLookPos = (snap.getValue() && closestDist <= 3.5) ? targetCenter : leadPos;

        double dx = finalLookPos.x - eyePos.x;
        double dy = finalLookPos.y - eyePos.y;
        double dz = finalLookPos.z - eyePos.z;

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
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

        // Silent Fişek
        if (fireworkSlot < 9) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
            final int prevSlot = mc.player.getInventory().selectedSlot;
            mc.execute(() -> mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot)));
        } else {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                fireworkSlot,
                mc.player.getInventory().selectedSlot,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
            );
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                fireworkSlot,
                mc.player.getInventory().selectedSlot,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
            );
        }

        // Rotasyonu uygula
        mc.player.setYaw(safeYaw);
        mc.player.setPitch(safePitch);

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
