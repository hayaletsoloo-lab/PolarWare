package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.events.impl.EventSync;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {
    
    // --- AYARLAR ---
    public final Setting<Boolean> autofw = new Setting<>("AutoFW", true);
    public final Setting<FWMode> fwMode = new Setting<>("FWMode", FWMode.Silent);
    public final Setting<Integer> delay = new Setting<>("Delay", 5, 1, 20);
    public final Setting<Float> predict = new Setting<>("Predict", 1.0f, 0.5f, 4.0f);
    public final Setting<Float> aimRange = new Setting<>("AimRange", 50f, 20f, 64f);
    public final Setting<Rotations> rotations = new Setting<>("Rotations", Rotations.Sharp);
    public final Setting<Float> smoothFactor = new Setting<>("SmoothFactor", 0.4f, 0.1f, 1.0f, v -> rotations.is(Rotations.Smooth));

    public enum FWMode { Silent, Swap }
    private enum Rotations { Sharp, Smooth }

    private int fireworkTicks = 0;
    private int currentDelay = 5;
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
        // Envanter açıksa veya oyuncu havada değilse hiçbir şey yapma (Donma/Titreme engeli)
        if (fullNullCheck() || !mc.player.isFallFlying() || mc.currentScreen != null) return;
        
        fireworkTicks++;
        if (!autofw.getValue()) return;

        Entity target = ModuleManager.aura.target;
        if (!(target instanceof PlayerEntity playerTarget)) return;
        if (!playerTarget.isFallFlying()) return;
        if (mc.player.distanceTo(playerTarget) > aimRange.getValue()) return;

        // --- SWAP MODU: Kılıç Değişimi ve Vuruş ---
        if (fwMode.is(FWMode.Swap) && mc.player.distanceTo(playerTarget) <= 3.5) {
            if (mc.player.getAttackCooldownProgress(0.5f) >= 0.92f) {
                int swordSlot = findSwordSlot();
                if (swordSlot != -1) {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    // Kılıcı sessizce eline al
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, swordSlot, prevSlot, SlotActionType.SWAP, mc.player);
                    // Vur
                    mc.interactionManager.attackEntity(mc.player, playerTarget);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    // Tekrar fişeğe dön
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, swordSlot, prevSlot, SlotActionType.SWAP, mc.player);
                }
            }
        }

        // --- FİŞEK BASMA (SILENT / SWAP ORTAK) ---
        if (fireworkTicks < currentDelay) return;

        int fireworkSlot = findFireworkSlot();
        if (fireworkSlot == -1) return;

        // --- HIZ TAHMİNİ (PREDICT) ---
        velHistory[velIndex % 5] = playerTarget.getVelocity();
        velIndex++;
        double svx = 0, svy = 0, svz = 0;
        for (Vec3d v : velHistory) { svx += v.x; svy += v.y; svz += v.z; }
        Vec3d smoothVel = new Vec3d(svx / 5, svy / 5, svz / 5);

        Vec3d targetCenter = playerTarget.getBoundingBox().getCenter();
        Vec3d eyePos = mc.player.getEyePos();
        
        double dist = eyePos.distanceTo(targetCenter);
        double travelTime = dist / 1.6;
        Vec3d leadPos = targetCenter.add(
            smoothVel.x * travelTime * predict.getValue(),
            smoothVel.y * travelTime * predict.getValue(),
            smoothVel.z * travelTime * predict.getValue()
        );

        // --- ROTASYON HESABI ---
        double dx = leadPos.x - eyePos.x;
        double dy = leadPos.y - eyePos.y;
        double dz = leadPos.z - eyePos.z;
        
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);

        // GCD Fix (Polar Rotation Bypass)
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

        // [POLAR BYPASS] Sadece rotasyonu ayarla, manuel paket YOLLAMA (Titremeyi çözen yer burası)
        mc.player.setYaw(safeYaw);
        mc.player.setPitch(safePitch);

        // [POLAR BYPASS] Silent Fişek
        int prevSlot = mc.player.getInventory().selectedSlot;
        
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, prevSlot, SlotActionType.SWAP, mc.player);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireworkSlot, prevSlot, SlotActionType.SWAP, mc.player);

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
