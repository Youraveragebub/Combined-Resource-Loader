package com.youraveragebub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BlockTextureLookup {
	private BlockTextureLookup() {
	}

	public static SelectionResult capture(Minecraft client) {
		if (client == null
				|| client.level == null
				|| !(client.hitResult instanceof BlockHitResult blockHitResult)) {
			return null;
		}

		BlockState blockState = client.level.getBlockState(blockHitResult.getBlockPos());
		String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
		String blockName = blockState.getBlock().getName().getString();
		Set<String> textures = texturesFor(client, blockState, blockHitResult);
		return new SelectionResult(blockId, blockName, Set.copyOf(textures));
	}

	private static Set<String> texturesFor(Minecraft client, BlockState blockState, BlockHitResult blockHitResult) {
		BlockStateModelSet modelSet = client.getModelManager().getBlockStateModelSet();
		BlockStateModel model = modelSet.get(blockState);
		if (model == null) {
			return Set.of();
		}

		LinkedHashSet<String> textureIds = new LinkedHashSet<>();
		addMaterial(textureIds, model.particleMaterial());

		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(blockHitResult.getBlockPos().asLong()), parts);
		for (BlockStateModelPart part : parts) {
			addMaterial(textureIds, part.particleMaterial());
			addQuads(textureIds, part.getQuads(null));
			for (Direction direction : Direction.values()) {
				addQuads(textureIds, part.getQuads(direction));
			}
		}
		return textureIds;
	}

	private static void addQuads(Set<String> textureIds, List<BakedQuad> quads) {
		for (BakedQuad quad : quads) {
			addSprite(textureIds, quad.materialInfo().sprite());
		}
	}

	private static void addMaterial(Set<String> textureIds, Material.Baked material) {
		if (material != null) {
			addSprite(textureIds, material.sprite());
		}
	}

	private static void addSprite(Set<String> textureIds, TextureAtlasSprite sprite) {
		if (sprite == null) {
			return;
		}
		textureIds.add(spriteToTextureResourceId(sprite.contents().name()));
	}

	static String spriteToTextureResourceId(Identifier spriteId) {
		String path = spriteId.getPath();
		if (path.endsWith(".png")) {
			return spriteId.getNamespace() + ":" + (path.startsWith("textures/") ? path : "textures/" + path);
		}
		return spriteId.getNamespace() + ":textures/" + path + ".png";
	}

	public record SelectionResult(String blockId, String blockName, Set<String> textureResourceIds) {
	}
}
