package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.setting.Setting;

public class ElytraTarget extends Module {
    public final Setting<Boolean> autofw = new Setting<>("AutoFW", true);
    public final Setting<Mode> mode = new Setting<>("Mode", Mode.Legit);
    public final Setting<Integer> delay = new Setting<>("Delay", 5, 1, 20);
    
    public enum Mode { Silent, Legit }

    private int fireworkTicks = 0;
    private int currentDelay = 5;
    
    private int state = 0;
    private int stateTicks = 0;
    private int prevSlot = -1;

    public ElytraTarget() {
        super("ElytraTarget", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        fireworkTicks = 0;
        state = 0;
        stateTicks = 0;
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (!mc.player.isFallFlying()) return;

        Entity target = ModuleManager.aura.target;
        if (target == null) return;

        fireworkTicks++;

        if (autofw.getValue() && state == 0 && fireworkTicks >= currentDelay) {
            
            if (mc.player.getOffHandStack().getItem() instanceof FireworkRocketItem) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
                currentDelay = delay.getValue() + (int) (Math.random() * 3) - 1;
                if (currentDelay < 1) currentDelay = 1;
                fireworkTicks = 0;
                return;
            }

            int fireworkSlot = findFireworkSlot();
            if (fireworkSlot != -1) {
                if (mode.getValue() == Mode.Silent) {
                    int pSlot = mc.player.getInventory().selectedSlot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    sendPacket(new UpdateSelectedSlotC2SPacket(pSlot));
                    currentDelay = delay.getValue() + (int) (Math.random() * 3) - 1;
                    if (currentDelay < 1) currentDelay = 1;
                    fireworkTicks = 0;
                } else {
                    prevSlot = mc.player.getInventory().selectedSlot;
                    if (fireworkSlot == prevSlot) {
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        currentDelay = delay.getValue() + (int) (Math.random() * 3) - 1;
                        if (currentDelay < 1) currentDelay = 1;
                        fireworkTicks = 0;
                    } else {
                        sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
                        state = 1;
                        stateTicks = 2;
                    }
                }
            }
        }

        if (mode.getValue() == Mode.Legit) {
            if (state == 1 && --stateTicks <= 0) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                state = 2;
                stateTicks = 2;
            } else if (state == 2 && --stateTicks <= 0) {
                sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                state = 0;
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
        return -1;
    }
}
