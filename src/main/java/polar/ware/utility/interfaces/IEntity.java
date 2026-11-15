package polar.ware.utility.interfaces;

import net.minecraft.util.math.BlockPos;
import polar.ware.features.modules.render.Trails;

import java.util.List;

public interface IEntity {
    List<Trails.Trail> getTrails();

    BlockPos polarWare_Recode$getVelocityBP();
}
