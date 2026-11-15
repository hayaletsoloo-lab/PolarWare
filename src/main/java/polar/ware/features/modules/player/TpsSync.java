package polar.ware.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import polar.ware.PolarWare;
import polar.ware.core.Managers;
import polar.ware.core.manager.client.ModuleManager;
import polar.ware.events.impl.EventTick;
import polar.ware.features.modules.Module;

public class TpsSync extends Module {
    public TpsSync() {
        super("TpsSync", Module.Category.PLAYER);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(EventTick e) {
        if (ModuleManager.timer.isEnabled()) return;
        if (Managers.SERVER.getTPS() > 1)
            PolarWare.TICK_TIMER = Managers.SERVER.getTPS() / 20f;
        else PolarWare.TICK_TIMER = 1f;
    }

    @Override
    public void onDisable() {
        PolarWare.TICK_TIMER = 1f;
    }
}
