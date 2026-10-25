package kazuaki.animalnamer.network;

import kazuaki.animalnamer.AnimalNamerMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * サーバー → クライアント: どうぶつ名鑑を開く。名前の一覧、まだ名前のないMOB、現在の設定を一緒に送る。
 */
public record OpenGuidePayload(
        List<Entry> entries,
        List<Identifier> unnamed,
        boolean showNameAlways,
        boolean specialNamesEnabled,
        int specialNamesChance,
        boolean canEdit
) implements CustomPayload {

    /** 1ページ分: MOBのIDと、そのMOBに付く名前。 */
    public record Entry(Identifier entityId, List<String> names) {
    }

    public static final CustomPayload.Id<OpenGuidePayload> ID =
        new CustomPayload.Id<>(Identifier.of(AnimalNamerMod.MOD_ID, "open_guide"));
    public static final PacketCodec<RegistryByteBuf, OpenGuidePayload> CODEC =
        CustomPayload.codecOf(OpenGuidePayload::write, OpenGuidePayload::new);

    private OpenGuidePayload(RegistryByteBuf buf) {
        this(
            buf.readList(b -> new Entry(b.readIdentifier(), b.readList(PacketByteBuf::readString))),
            buf.readList(PacketByteBuf::readIdentifier),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readBoolean()
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeCollection(entries, (b, entry) -> {
            b.writeIdentifier(entry.entityId());
            b.writeCollection(entry.names(), PacketByteBuf::writeString);
        });
        buf.writeCollection(unnamed, PacketByteBuf::writeIdentifier);
        buf.writeBoolean(showNameAlways);
        buf.writeBoolean(specialNamesEnabled);
        buf.writeVarInt(specialNamesChance);
        buf.writeBoolean(canEdit);
    }

    @Override
    public Id<OpenGuidePayload> getId() {
        return ID;
    }
}
