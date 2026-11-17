package polar.ware.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import polar.ware.events.impl.PlayerUpdateEvent;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

public class ElytraHelper extends Module {
    public final Setting<Boolean> autoFirework = new Setting<>("AutoFirework", true);
    public final Setting<Mode> fireworkMode = new Setting<>("FireworkMode", Mode.Silent);
    public final Setting<Integer> delay = new Setting<>("Delay", 400, 100, 2000);
    public final Setting<Boolean> delayBypass = new Setting<>("Delay-Bypass", true);

    public enum Mode { Silent, Legit }

    private long lastFireworkTime = 0;
    private int state = 0;
    private int stateTicks = 0;
    private int prevSlot = -1;

    public ElytraHelper() {
        super("ElytraHelper", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        lastFireworkTime = 0;
        state = 0;
        stateTicks = 0;
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (fullNullCheck() || mc.currentScreen != null) return;
        if (!mc.player.isFallFlying()) return;

        long currentDelay = delay.getValue();
        if (delayBypass.getValue()) {
            currentDelay += (long) ((Math.random() - 0.5) * 100);
        }

        long now = System.currentTimeMillis();
        if (autoFirework.getValue() && state == 0 && now - lastFireworkTime >= currentDelay) {
            
            if (mc.player.getOffHandStack().getItem() instanceof FireworkRocketItem) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
                lastFireworkTime = now;
                return;
            }

            int fireworkSlot = findFireworkSlot();
            if (fireworkSlot != -1) {
                if (fireworkMode.getValue() == Mode.Silent) {
                    int pSlot = mc.player.getInventory().selectedSlot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    sendPacket(new UpdateSelectedSlotC2SPacket(pSlot));
                    lastFireworkTime = now;
                } else {
                    prevSlot = mc.player.getInventory().selectedSlot;
                    if (fireworkSlot == prevSlot) {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                        mc.player.swingHand(Hand.MAIN_HAND);
                        lastFireworkTime = now;
                    } else {
                        sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
                        state = 1;
                        stateTicks = 2;
                    }
                }
            }
        }

        if (fireworkMode.getValue() == Mode.Legit) {
            if (state == 1 && --stateTicks <= 0) {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.swingHand(Hand.MAIN_HAND);
                state = 2;
                stateTicks = 2;
            } else if (state == 2 && --stateTicks <= 0) {
                sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                state = 0;
                lastFireworkTime = System.currentTimeMillis();
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
