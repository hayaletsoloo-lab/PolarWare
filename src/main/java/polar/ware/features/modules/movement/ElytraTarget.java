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
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {

    // ── Ayarlar ──
    public final Setting<Boolean> autofw = new Setting<>("AutoFW", true);
    public final Setting<Integer> delay = new Setting<>("Delay", 5, 1, 20);
    public final Setting<Boolean> snap = new Setting<>("Snap", true);

    private int fireworkTicks = 0;
    private int currentDelay = 5;

    public ElytraTarget() {
        super("ElytraTarget", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        fireworkTicks = 0;
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        // Sadece sen uçarken çalış
        if (!mc.player.isFallFlying() || mc.currentScreen != null) return;

        fireworkTicks++;

        // 1. ADIM: HEP FİŞEK BAS (Mesafe kontrolü YOK)
        if (autofw.getValue() && fireworkTicks >= currentDelay) {
            int fireworkSlot = findFireworkSlot();
            if (fireworkSlot != -1) {
                // Sessiz Fişek (Silent)
                if (fireworkSlot < 9) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    final int prevSlot = mc.player.getInventory().selectedSlot;
                    mc.execute(() -> mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot)));
                } else {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, mc.player.getInventory().selectedSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, mc.player.getInventory().selectedSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                }
                
                // Gecikmeyi insanileştir (+- 1 tick oynama)
                currentDelay = delay.getValue() + (int) (Math.random() * 3) - 1;
                if (currentDelay < 1) currentDelay = 1;
                fireworkTicks = 0;
            }
        }

        // 2. ADIM: SADECE SNAP (Aura'nın hedefini al, 3.5 blok yakınsa anlık adama bak)
        if (snap.getValue()) {
            Entity target = ModuleManager.aura.target;
            if (target instanceof PlayerEntity p && p.isAlive()) {
                double dist = mc.player.distanceTo(p);
                if (dist <= 3.5) {
                    Vec3d targetCenter = p.getBoundingBox().getCenter();
                    Vec3d eyePos = mc.player.getEyePos();
                    
                    double dx = targetCenter.x - eyePos.x;
                    double dy = targetCenter.y - eyePos.y;
                    double dz = targetCenter.z - eyePos.z;

                    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    pitch = MathHelper.clamp(pitch, -90f, 90f);

                    // GCD Fix (Polar Rotation Bypass)
                    double sens = mc.options.getMouseSensitivity().getValue();
                    double f = sens * 0.6 + 0.2;
                    double gcd = Math.pow(f, 3.0) * (8.0 / 3.0);
                    
                    float safeYaw = (float) (yaw - (yaw - mc.player.getYaw()) % gcd);
                    float safePitch = (float) (pitch - (pitch - mc.player.getPitch()) % gcd);

                    mc.player.setYaw(safeYaw);
                    mc.player.setPitch(safePitch);
                }
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
