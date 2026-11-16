package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {

    // ── Ayarlar ──
    public final Setting<Boolean> autofw = new Setting<>("AutoFW", true);
    public final Setting<Integer> delay = new Setting<>("Delay", 5, 1, 20);
    public final Setting<Float> predict = new Setting<>("Predict", 1.0f, 0.5f, 4.0f);
    public final Setting<Boolean> snap = new Setting<>("Snap", true);

    private int fireworkTicks = 0;
    private int currentDelay = 5;
    private final Vec3d[] velHistory = new Vec3d[5];
    private int velIndex = 0;

    public ElytraTarget() {
        super("ElytraTarget", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        fireworkTicks = 0;
        java.util.Arrays.fill(velHistory, Vec3d.ZERO);
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (!mc.player.isFallFlying() || mc.currentScreen != null) return;

        fireworkTicks++;
        Entity target = ModuleManager.aura.target;
        if (!(target instanceof PlayerEntity p) || !p.isAlive()) return;

        // ── HIZ TAHMİNİ (PREDICT) ──
        velHistory[velIndex % 5] = p.getVelocity();
        velIndex++;
        double svx = 0, svy = 0, svz = 0;
        for (Vec3d v : velHistory) { svx += v.x; svy += v.y; svz += v.z; }
        Vec3d smoothVel = new Vec3d(svx / 5, svy / 5, svz / 5);

        Vec3d targetCenter = p.getBoundingBox().getCenter();
        Vec3d eyePos = mc.player.getEyePos();
        double dist = eyePos.distanceTo(targetCenter);
        
        Vec3d leadPos = targetCenter.add(
            smoothVel.x * (dist / 1.6) * predict.getValue(),
            smoothVel.y * (dist / 1.6) * predict.getValue(),
            smoothVel.z * (dist / 1.6) * predict.getValue()
        );

        // ── SNAP (Ghost Hit Engeli) ──
        Vec3d finalLookPos = (snap.getValue() && dist <= 3.5) ? targetCenter : leadPos;
        double dx = finalLookPos.x - eyePos.x;
        double dy = finalLookPos.y - eyePos.y;
        double dz = finalLookPos.z - eyePos.z;

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        pitch = MathHelper.clamp(pitch, -90f, 90f);

        // ── GCD FIX (Polar Bypass) ──
        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        double gcd = Math.pow(f, 3.0) * (8.0 / 3.0);
        float safeYaw = (float) (yaw - (yaw - mc.player.getYaw()) % gcd);
        float safePitch = (float) (pitch - (pitch - mc.player.getPitch()) % gcd);

        // ── ROTASYONU UYGULA VE PAKET GÖNDER (Polar Bypass) ──
        mc.player.setYaw(safeYaw);
        mc.player.setPitch(safePitch);
        sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(safeYaw, safePitch, mc.player.isOnGround()));

        // ── FİŞEK BASMA (SILENT) ──
        if (autofw.getValue() && fireworkTicks >= currentDelay) {
            int fireworkSlot = findFireworkSlot();
            if (fireworkSlot != -1) {
                if (fireworkSlot < 9) {
                    sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
                    sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                } else {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, safeYaw, safePitch));
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
                }
                
                // İnsanileştirilmiş gecikme
                currentDelay = delay.getValue() + (int) (Math.random() * 3) - 1;
                if (currentDelay < 1) currentDelay = 1;
                fireworkTicks = 0;
            }
        }
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
