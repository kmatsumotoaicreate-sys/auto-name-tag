package kazuaki.animalnamer.network;

import kazuaki.animalnamer.AnimalNamerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * クライアント → サーバー: どうぶつ名鑑のMOBページで名前を追加・削除する。
 */
public record EditNamesPayload(Action action, Identifier entityId, String name) implements CustomPayload {

    public enum Action {
        /** 名前を1つ追加する（名前のなかったMOBなら、ページができる）。 */
        ADD,
        /** 名前を1つ削除する。 */
        REMOVE,
        /** そのMOBの名前をすべて削除する（ページがなくなる）。name は使わない。 */
        CLEAR
    }

    public static final CustomPayload.Id<EditNamesPayload> ID =
        new CustomPayload.Id<>(Identifier.of(AnimalNamerMod.MOD_ID, "edit_names"));
    public static final PacketCodec<RegistryByteBuf, EditNamesPayload> CODEC =
        CustomPayload.codecOf(EditNamesPayload::write, EditNamesPayload::new);

    private EditNamesPayload(RegistryByteBuf buf) {
        this(buf.readEnumConstant(Action.class), buf.readIdentifier(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeEnumConstant(action);
        buf.writeIdentifier(entityId);
        buf.writeString(name);
    }

    @Override
    public Id<EditNamesPayload> getId() {
        return ID;
    }
}
