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
        // [FUNTIME BYPASS] - Envanter kapandıktan sonra 0.5 saniye bekle
        if (funTimeDelay > 0) {
            funTimeDelay--;
            
            // Bekleme süresince hareketi tamamen engelle (Ayağın yere bassın)
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            
            if (funTimeDelay == 0) {
                // 0.5 saniye bitti, tıklamaları ve envanter kapatma paketini gönder
                while (!funTimeQueue.isEmpty()) {
                    sendPacket(funTimeQueue.poll());
                }
                if (queuedClose != null) {
                    sendPacket(queuedClose);
                    queuedClose = null;
                }
                
                // [ÇÖZÜM BURADA] - Sunucu paketleri aldıktan sonra tuşları senin yerine tekrar bas
                long handle = mc.getWindow().getHandle();
                if (InputUtil.isKeyPressed(handle, mc.options.forwardKey.getDefaultKey().getCode())) mc.options.forwardKey.setPressed(true);
                if (InputUtil.isKeyPressed(handle, mc.options.backKey.getDefaultKey().getCode())) mc.options.backKey.setPressed(true);
                if (InputUtil.isKeyPressed(handle, mc.options.leftKey.getDefaultKey().getCode())) mc.options.leftKey.setPressed(true);
                if (InputUtil.isKeyPressed(handle, mc.options.rightKey.getDefaultKey().getCode())) mc.options.rightKey.setPressed(true);
                if (InputUtil.isKeyPressed(handle, mc.options.jumpKey.getDefaultKey().getCode())) mc.options.jumpKey.setPressed(true);
                
                // Eğer W'ye basılıyorsa direkt koşmayı (sprint) da geri aç
                if (mc.options.forwardKey.isPressed() && !mc.player.isSneaking()) {
                    mc.options.sprintKey.setPressed(true);
                    mc.player.setSprinting(true);
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
            // [FUNTIME BYPASS] - Envanteri kapatma isteğini iptal et, 10 tick (0.5 saniye) geciktir
            queuedClose = close;
            e.cancel();
            funTimeDelay = 10; 
        }
    }
}
