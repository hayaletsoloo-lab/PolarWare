package polar.ware.gui.polargui.components;

import net.minecraft.client.util.math.MatrixStack;
import polar.ware.features.modules.client.HudEditor;
import polar.ware.gui.font.FontRenderers;
import polar.ware.gui.polargui.PolarGui;
import polar.ware.setting.Setting;
import polar.ware.setting.impl.BooleanSettingGroup;
import polar.ware.utility.render.Render2DEngine;

import java.awt.*;

import static polar.ware.utility.render.animation.AnimationUtility.fast;

public class BooleanParentComponent extends SettingElement {
    float animation = 0f;
    private final Setting<BooleanSettingGroup> parentSetting;

    public BooleanParentComponent(Setting setting) {
        super(setting);
        this.parentSetting = setting;
    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        super.render(stack, mouseX, mouseY, partialTicks);
        if ((getY() > PolarGui.getInstance().main_posY + PolarGui.getInstance().height) || getY() < PolarGui.getInstance().main_posY) {
            return;
        }
        FontRenderers.modules.drawString(stack, getSetting().getName(), (float) getX(), (float) getY() + 5, isHovered() ? -1 : new Color(0xB0FFFFFF, true).getRGB());
        animation = fast(animation, getParentSetting().getValue().isEnabled() ? 1 : 0, 15f);
        double paddingX = 7 * animation;
        Color color = HudEditor.getColor(1);
        Render2DEngine.drawRound(stack, (float) (x + width - 18), (float) (y + height / 2 - 4), 15, 8, 4, paddingX > 4 ? color : new Color(0xFFB2B1B1));
        Render2DEngine.drawRound(stack, (float) (x + width - 17 + paddingX), (float) (y + height / 2 - 3), 6, 6, 3, new Color(-1));
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if ((getY() > PolarGui.getInstance().main_posY + PolarGui.getInstance().height) || getY() < PolarGui.getInstance().main_posY) {
            return;
        }
        if (isHovered()) getParentSetting().getValue().setEnabled(!getParentSetting().getValue().isEnabled());
    }

    public Setting<BooleanSettingGroup> getParentSetting() {
        return parentSetting;
    }
}
