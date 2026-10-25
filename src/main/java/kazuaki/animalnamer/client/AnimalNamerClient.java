package kazuaki.animalnamer.client;

import kazuaki.animalnamer.network.OpenGuidePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class AnimalNamerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenGuidePayload.ID, (payload, context) ->
            context.client().execute(() -> {
                // すでに開いていれば（名前の追加・削除の直後）、見ているページのまま中身だけ更新する
                if (context.client().currentScreen instanceof AnimalGuideScreen screen) {
                    screen.update(payload);
                } else {
                    context.client().setScreen(new AnimalGuideScreen(payload));
                }
            }));
    }
}
