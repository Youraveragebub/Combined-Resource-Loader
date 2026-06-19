package com.youraveragebub.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombinedResourceLoaderClient implements ClientModInitializer {
	public static final String MOD_ID = "combinedresourceloader";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final BlockSelectionController BLOCK_SELECTION = new BlockSelectionController();

	@Override
	public void onInitializeClient() {
		OverrideManager.load();
		ClientTickEvents.START_CLIENT_TICK.register(BLOCK_SELECTION::tick);
	}
}
