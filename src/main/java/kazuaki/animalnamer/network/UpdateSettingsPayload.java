package kazuaki.animalnamer.network;

import kazuaki.animalnamer.AnimalNamerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * クライアント → サーバー: どうぶつ名鑑の設定ページで変更した内容を送る。
 */
public record UpdateSettingsPayload(
        boolean showNameAlways,
        boolean specialNamesEnabled,
        int specialNamesChance
) implements CustomPayload {

    public static final CustomPayload.Id<UpdateSettingsPayload> ID =
        new CustomPayload.Id<>(Identifier.of(AnimalNamerMod.MOD_ID, "update_settings"));
    public static final PacketCodec<RegistryByteBuf, UpdateSettingsPayload> CODEC =
        CustomPayload.codecOf(UpdateSettingsPayload::write, UpdateSettingsPayload::new);

    private UpdateSettingsPayload(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(showNameAlways);
        buf.writeBoolean(specialNamesEnabled);
        buf.writeVarInt(specialNamesChance);
    }

    @Override
    public Id<UpdateSettingsPayload> getId() {
        return ID;
    }
}
