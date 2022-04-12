package mca.network.c2s;

import mca.cobalt.network.Message;
import mca.cobalt.network.NetworkHandler;
import mca.entity.ai.relationship.family.FamilyTree;
import mca.entity.ai.relationship.family.FamilyTreeNode;
import mca.network.s2c.GetFamilyTreeResponse;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetFamilyTreeRequest implements Message {
    @Serial
    private static final long serialVersionUID = -6232925305386763715L;

    UUID uuid;

    public GetFamilyTreeRequest(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        FamilyTree.get(player.getWorld()).getOrEmpty(uuid).ifPresent(entry -> {
            Map<UUID, FamilyTreeNode> familyEntries = Stream.concat(
                            entry.lookup(Stream.of(entry.id(), entry.spouse())),
                            entry.lookup(entry.getRelatives(2, 1))
                    ).distinct()
                    .collect(Collectors.toMap(FamilyTreeNode::id, Function.identity()));

            NetworkHandler.sendToPlayer(new GetFamilyTreeResponse(uuid, familyEntries), player);
        });
    }
}