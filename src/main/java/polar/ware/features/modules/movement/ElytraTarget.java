package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {
    public final Setting<Boolean> predict = new Setting<>("Predict", true);
    public final Setting<Integer> predictTicks = new Setting<>("PredictTicks", 10, 1, 20, v -> predict.getValue());
    public final Setting<Float> predictLength = new Setting<>("PredictLength", 2f, 0.5f, 5f, v -> predict.getValue());
    public final Setting<Float> overshoot = new Setting<>("Overshoot", 0f, 0f, 20f);
    public final Setting<Boolean> swing = new Setting<>("Swing", true);

    private int state = 0;
    private int stateTicks = 0;
    private int prevSlot = -1;
    private float smoothYaw = 0f;
    private float smoothPitch = 0f;

    public ElytraTarget() {
        super("ElytraTarget", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        state = 0;
        stateTicks = 0;
        if (!fullNullCheck()) {
            smoothYaw = mc.player.getYaw();
            smoothPitch = mc.player.getPitch();
        }
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (!mc.player.isFallFlying()) return;

        Entity target = ModuleManager.aura.target;
        if (target == null) return;

        double dist = mc.player.distanceTo(target);

        Vec3d targetCenter = target.getBoundingBox().getCenter();
        Vec3d eyePos = mc.player.getEyePos();
        
        Vec3d finalLookPos = targetCenter;
        
        if (predict.getValue()) {
            Vec3d targetVel = target.getVelocity();
            double ticksToTravel = predictTicks.getValue();
            double length = predictLength.getValue();
            
            finalLookPos = targetCenter.add(
                targetVel.x * ticksToTravel * length, 
                targetVel.y * ticksToTravel * length, 
                targetVel.z * ticksToTravel * length
            );
        }

        double dx = finalLookPos.x - eyePos.x;
        double dy = finalLookPos.y - eyePos.y;
        double dz = finalLookPos.z - eyePos.z;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        
        targetPitch += overshoot.getValue();
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);

        if (dist > 3.0) {
            float jitterYaw = (float) ((Math.random() - 0.5) * 3.0);
            float jitterPitch = (float) ((Math.random() - 0.5) * 3.0);
            targetYaw += jitterYaw;
            targetPitch += jitterPitch;
        }

        smoothYaw = smoothYaw + (targetYaw - smoothYaw) * 0.35f;
        smoothPitch = smoothPitch + (targetPitch - smoothPitch) * 0.35f;

        float finalYaw = (dist <= 3.0) ? targetYaw : smoothYaw;
        float finalPitch = (dist <= 3.0) ? targetPitch : smoothPitch;

        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        double gcd = Math.pow(f, 3.0) * (8.0 / 3.0);
        float safeYaw = (float) (finalYaw - (finalYaw - mc.player.getYaw()) % gcd);
        float safePitch = (float) (finalPitch - (finalPitch - mc.player.getPitch()) % gcd);

        if (state == 0 && dist <= 3.0) {
            int swordSlot = findSwordSlot();
            if (swordSlot != -1) {
                prevSlot = mc.player.getInventory().selectedSlot;
                if (swordSlot != prevSlot) sendPacket(new UpdateSelectedSlotC2SPacket(swordSlot));
                
                if (mc.player.getAttackCooldownProgress(0.5f) >= 0.9f) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
                }
                state = 1;
                stateTicks = 2;
                return;
            }
        }

        if (state == 1) {
            if (--stateTicks <= 0) {
                if (prevSlot != -1) sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                state = 0;
            }
        }
    }

    private int findSwordSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem)) return i;
        }
        return -1;
    }
}
