package mca;

import mca.advancement.criterion.CriterionMCA;
import mca.block.BlocksMCA;
import mca.block.TombstoneBlockData;
import mca.cobalt.network.NetworkHandlerImpl;
import mca.cobalt.registration.RegistrationImpl;
import mca.entity.EntitiesMCA;
import mca.item.ItemsMCA;
import mca.network.MessagesMCA;
import mca.resources.ApiIdentifiableReloadListener;
import mca.resources.FabricClothingList;
import mca.server.ServerInteractionManager;
import mca.server.command.AdminCommand;
import mca.server.command.MCACommand;
import mca.server.world.data.VillageManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.world.ServerWorld;

public final class MCAFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        new RegistrationImpl();
        new NetworkHandlerImpl();
        TombstoneBlockData.bootstrap();

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        SoundsMCA.bootstrap();
        ParticleTypesMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ApiIdentifiableReloadListener());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FabricClothingList());

        ServerTickEvents.END_WORLD_TICK.register(w -> {
            VillageManager.get(w).tick();
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            ServerInteractionManager.getInstance().tick();
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((old, neu, alive) -> {
            if (!alive) {
                VillageManager.get((ServerWorld)old.world).getBabies().pop(neu);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            AdminCommand.register(dispatcher);
            MCACommand.register(dispatcher);
        });
    }
}

