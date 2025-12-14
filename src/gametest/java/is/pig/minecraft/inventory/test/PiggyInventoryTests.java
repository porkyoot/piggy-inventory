package is.pig.minecraft.inventory.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest suite for Piggy Inventory mod - Limited to server-compatible logic
 * Tests can be run with: ./gradlew :piggy-inventory:runGametest
 * 
 * Note: Most inventory logic is client-only (sorting, UI, handlers, config)
 * Only testing basic initialization that works server-side
 */
public class PiggyInventoryTests {

    /**
     * Simple dummy test to verify GameTest framework is working
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dummyPassTest(GameTestHelper context) {
        System.out.println("[PIGGY-INVENTORY TEST] Dummy test executed successfully!");
        System.out.println("[TEST] Note: Most inventory logic is client-only");
        System.out.println("[TEST] Sorters, UI handlers, and preferences require client environment");
        context.succeed();
    }

    /**
     * Test MOD_ID constant
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testModId(GameTestHelper context) {
        String modId = is.pig.minecraft.inventory.PiggyInventory.MOD_ID;

        context.assertTrue(modId != null, "MOD_ID should not be null");
        context.assertTrue(modId.equals("piggy-inventory"), "MOD_ID should be 'piggy-inventory'");

        System.out.println("[TEST] MOD_ID verified: " + modId);
        context.succeed();
    }

    /**
     * Test that logger is initialized
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testLoggerInit(GameTestHelper context) {
        org.slf4j.Logger logger = is.pig.minecraft.inventory.PiggyInventory.LOGGER;

        context.assertTrue(logger != null, "Logger should be initialized");

        System.out.println("[TEST] Logger initialized successfully");
        context.succeed();
    }
}
