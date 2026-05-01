package is.pig.minecraft.inventory.duck;
import is.pig.minecraft.api.*;

import net.minecraft.world.inventory.Slot;

public interface HandledScreen {
    Slot piggy_getSlotUnderMouse(double mouseX, double mouseY);
}
