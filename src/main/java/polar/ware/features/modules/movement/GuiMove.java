package polar.ware.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import polar.ware.events.impl.PacketEvent;
import polar.ware.features.modules.Module;
import polar.ware.setting.Setting;

import java.util.LinkedList;
import java.util.Queue;

public class GuiMove extends Module {
    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
    }

    private final Setting<Boolean> rotateOnArrows = new Setting<>("RotateOnArrows", true);
    private final Setting<Boolean> sneak = new Setting<>("sneak", false);
    private final Setting<Boolean> disableRightClick = new Setting<>("NoRightClick", false);

    // FunTime Bypass Değişkenleri
    private final Queue<ClickSlotC2SPacket> funTimeQueue = new LinkedList<>();
    private CloseHandledScreenC2SPacket queuedClose = null;
    private int funTimeDelay = 0;

    @Override
    public void onUpdate() {
        // [FUNTIME BYPASS] - Envanter kapandıktan sonra paketleri gecikmeli gönder
        if (funTimeDelay > 0) {
            funTimeDelay--;
            
            // Sunucu paketleri işlerken karakterin hareket etmesini engelle
            for (KeyBinding k : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey}) {
                k.setPressed(false);
            }
            
            if (funTimeDelay == 0) {
                // Tıklamaları sırayla gönder
                while (!funTimeQueue.isEmpty()) {
                    sendPacket(funTimeQueue.poll());
                }
                // En son envanteri kapat
                if (queuedClose != null) {
                    sendPacket(queuedClose);
                    queuedClose = null;
                }
            }
            return;
        }

        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) {
            for (KeyBinding k : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey})
                k.setPressed(isKeyPressed(InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));

            float deltaX = 0;
            float deltaY = 0;

            if (rotateOnArrows.getValue()) {
                if (isKeyPressed(264)) deltaY += 30f;
                if (isKeyPressed(265)) deltaY -= 30f;
                if (isKeyPressed(262)) deltaX += 30f;
                if (isKeyPressed(263)) deltaX -= 30f;
                if (deltaX != 0 || deltaY != 0) mc.player.changeLookDirection(deltaX, deltaY);
            }

            if (sneak.getValue())
                mc.options.sneakKey.setPressed(isKeyPressed(InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()));
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (e.getPacket() instanceof ClickSlotC2SPacket click) {
            
            // [NO RIGHT CLICK] - Sağ tık engeli
            if (disableRightClick.getValue() && click.getButton() == 1) {
                e.cancel();
                return;
            }

            // [FUNTIME BYPASS] - Tıklamaları iptal et, kuyruğa ekle
            funTimeQueue.add(click);
            e.cancel();
        }

        if (e.getPacket() instanceof CloseHandledScreenC2SPacket close) {
            // [FUNTIME BYPASS] - Envanteri kapatma isteğini iptal et, 3 tick (150ms) geciktir
            queuedClose = close;
            e.cancel();
            funTimeDelay = 3; 
        }
    }
}
