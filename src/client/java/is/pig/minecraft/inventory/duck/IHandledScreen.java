package is.pig.minecraft.inventory.duck;

import net.minecraft.world.inventory.Slot;

public interface IHandledScreen {
    Slot piggy_getSlotUnderMouse(double mouseX, double mouseY);
}
