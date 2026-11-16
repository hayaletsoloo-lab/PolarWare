package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.events.impl.EventSync;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {
    
    public final Setting<Boolean> autofw = new Setting<>("AutoFW", true);
    public final Setting<FWMode> fwMode = new Setting<>("FWMode", FWMode.Swap);
    public final Setting<Boolean> snapOnHit = new Setting<>("SnapOnHit", true); // [YENİ]
    public final Setting<Integer> delay = new Setting<>("Delay", 4, 1, 20);
    public final Setting<Float> predict = new Setting<>("Predict", 1.5f, 0.5f, 4.0f);
    public final Setting<Float> aimRange = new Setting<>("AimRange", 50f, 20f, 64f);
    public final Setting<Rotations> rotations = new Setting<>("Rotations", Rotations.Smooth);
    public final Setting<Float> smoothFactor = new Setting<>("SmoothFactor", 0.4f, 0.1f, 1.0f, v -> rotations.is(Rotations.Smooth));

    public enum FWMode { Silent, Swap }
    private enum Rotations { Sharp, Smooth }

    private int fireworkTicks = 0;
    private int currentDelay = 4;
    private float smoothYaw, smoothPitch;
    private final Vec3d[] velHistory = new Vec3d[5];
    private int velIndex = 0;

    public ElytraTarget() {
        super("ElytraTarget", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        fireworkTicks = 0;
        java.util.Arrays.fill(velHistory, Vec3d.ZERO);
        if (!fullNullCheck()) {
            smoothYaw = mc.player.getYaw();
            smoothPitch = mc.player.getPitch();
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (fullNullCheck() || !mc.player.isFallFlying() || mc.currentScreen != null) return;
        
        fireworkTicks++;
        if (!autofw.getValue()) return;

        // --- HEDEF BULMA (Sadece uçan oyuncular) ---
        PlayerEntity target = null;
        double closestDist = Double.MAX_VALUE;
        
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity p && p != mc.player && p.isAlive() && p.isFallFlying()) {
                double dist = mc.player.distanceTo(p);
                if (dist <= aimRange.getValue() && dist < closestDist) {
                    closestDist = dist;
                    target = p;
                }
            }
        }

        if (target == null) return;

        // --- ROTASYON HESABI (PREDICT / ÖNÜNE GEÇME) ---
        velHistory[velIndex % 5] = target.getVelocity();
        velIndex++;
        double svx = 0, svy = 0, svz = 0;
        for (Vec3d v : velHistory) { svx += v.x; svy += v.y; svz += v.z; }
        Vec3d smoothVel = new Vec3d(svx / 5, svy / 5, svz / 5);

        Vec3d targetCenter = target.getBoundingBox().getCenter();
        Vec3d eyePos = mc.player.getEyePos();
        
        double dist = eyePos.distanceTo(targetCenter);
        double travelTime = dist / 1.6;
        
        // Önüne geçme noktası (LeadPos)
        Vec3d leadPos = targetCenter.add(
            smoothVel.x * travelTime * predict.getValue(),
            smoothVel.y * travelTime * predict.getValue(),
            smoothVel.z * travelTime * predict.getValue()
        );

        float targetYaw = (float) Math.toDegrees(Math.atan2(leadPos.z - eyePos.z, leadPos.x - eyePos.x)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(leadPos.y - eyePos.y, Math.sqrt(Math.pow(leadPos.x - eyePos.x, 2) + Math.pow(leadPos.z - eyePos.z, 2))));
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);

        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        double gcd = Math.pow(f, 3.0) * (8.0 / 3.0);

        if (rotations.is(Rotations.Smooth)) {
            smoothYaw = smoothYaw + (targetYaw - smoothYaw) * smoothFactor.getValue();
            smoothPitch = smoothPitch + (targetPitch - smoothPitch) * smoothFactor.getValue();
            targetYaw = smoothYaw;
            targetPitch = smoothPitch;
        }

        float safeYaw = (float) (targetYaw - (targetYaw - mc.player.getYaw()) % gcd);
        float safePitch = (float) (targetPitch - (targetPitch - mc.player.getPitch()) % gcd);

        // Rotasyonu uygula (Önüne geçme rotasyonu)
        mc.player.setYaw(safeYaw);
        mc.player.setPitch(safePitch);

        // --- SWAP MODU: VURUŞ VE ANLIK KİLİT (SNAP) ---
        if (fwMode.is(FWMode.Swap) && closestDist <= 3.5) {
            if (mc.player.getAttackCooldownProgress(0.5f) >= 0.92f) {
                int swordSlot = findSwordSlot();
                if (swordSlot != -1) {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    
                    // 1. Kılıcı sessizce eline al
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, swordSlot, prevSlot, SlotActionType.SWAP, mc.player);

                    // 2. [POLAR BYPASS] Eğer SnapOnHit açıksa, kafayı anlık olarak adama kilitlet
                    if (snapOnHit.getValue()) {
                        // Gerçek hedefe bakış açısını hesapla
                        float realYaw = (float) Math.toDegrees(Math.atan2(targetCenter.z - eyePos.z, targetCenter.x - eyePos.x)) - 90f;
                        float realPitch = (float) -Math.toDegrees(Math.atan2(targetCenter.y - eyePos.y, Math.sqrt(Math.pow(targetCenter.x - eyePos.x, 2) + Math.pow(targetCenter.z - eyePos.z, 2))));
                        
                        float safeRealYaw = (float) (realYaw - (realYaw - mc.player.getYaw()) % gcd);
                        float safeRealPitch = (float) (realPitch - (realPitch - mc.player.getPitch()) % gcd);

                        // Sunucuya "adama bakıyorum" paketi gönder
                        mc.player.setYaw(safeRealYaw);
                        mc.player.setPitch(safeRealPitch);
                        sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(safeRealYaw, safeRealPitch, mc.player.isOnGround()));
                    }

                    // 3. Vur
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    // 4. [POLAR BYPASS] Vurduktan hemen sonra eski "önüne geçme" rotasyonuna geri dön
                    if (snapOnHit.getValue()) {
                        mc.player.setYaw(safeYaw);
                        mc.player.setPitch(safePitch);
                        sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(safeYaw, safePitch, mc.player.isOnGround()));
                    }

                    // 5. Fişeğe geri dön
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, swordSlot, prevSlot, SlotActionType.SWAP, mc.player);
                }
            }
        }

        // --- FİŞEK BASMA ---
        if (fireworkTicks < currentDelay) return;

        int fireworkSlot = findFireworkSlot();
        if (fireworkSlot == -1) return;

        int prevSlot = mc.player.getInventory().selectedSlot;
        
        if (fireworkSlot < 9) {
            sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        } else {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, prevSlot, SlotActionType.SWAP, mc.player);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, prevSlot, SlotActionType.SWAP, mc.player);
        }

        currentDelay = delay.getValue() + (int) (Math.random() * 3) - 1;
        if (currentDelay < 1) currentDelay = 1;
        fireworkTicks = 0; 
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

    private int findSwordSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem)) return i;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem)) return i;
        }
        return -1;
    }
}
