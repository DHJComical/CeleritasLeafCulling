package toni.sodiumleafculling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OpaqueLeafFill {
    private static final String[] VANILLA_LEAF_TEXTURES = {
            "blocks/leaves_oak", "blocks/leaves_spruce", "blocks/leaves_birch",
            "blocks/leaves_jungle", "blocks/leaves_acacia", "blocks/leaves_big_oak"
    };

    private static final String BLOCK_ATLAS_BASE_PATH = "textures";

    private static volatile Map<String, SpritePair> spritePairs = Collections.emptyMap();

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        if (!BLOCK_ATLAS_BASE_PATH.equals(map.getBasePath())) {
            return;
        }
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        for (String path : VANILLA_LEAF_TEXTURES) {
            try {
                map.setTextureEntry(createOpaqueVariant(manager, path));
            } catch (IOException | RuntimeException e) {
                CeleritasLeafCulling.LOGGER.error("Failed to create opaque leaf texture variant for {}", path, e);
            }
        }
    }

    @SubscribeEvent
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        TextureMap map = event.getMap();
        if (!BLOCK_ATLAS_BASE_PATH.equals(map.getBasePath())) {
            return;
        }
        Map<String, SpritePair> pairs = new HashMap<>();
        for (String path : VANILLA_LEAF_TEXTURES) {
            String opaqueName = opaqueSpriteName(path);
            TextureAtlasSprite original = map.getAtlasSprite("minecraft:" + path);
            TextureAtlasSprite opaque = map.getAtlasSprite(opaqueName);
            if (original == null || opaque == null || !opaqueName.equals(opaque.getIconName())) {
                continue;
            }
            pairs.put(original.getIconName(), new SpritePair(original, opaque));
        }
        spritePairs = pairs;
    }

    public static List<BakedQuad> makeOpaqueFill(List<BakedQuad> quads) {
        Map<String, SpritePair> pairs = spritePairs;
        if (pairs.isEmpty()) {
            return quads;
        }
        List<BakedQuad> result = new ArrayList<>(quads.size());
        boolean changed = false;
        for (BakedQuad quad : quads) {
            BakedQuad mapped = remapQuad(quad, pairs);
            result.add(mapped);
            changed |= mapped != quad;
        }
        return changed ? result : quads;
    }

    private static BakedQuad remapQuad(BakedQuad quad, Map<String, SpritePair> pairs) {
        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite == null) {
            return quad;
        }
        SpritePair pair = pairs.get(sprite.getIconName());
        if (pair == null) {
            return quad;
        }
        VertexFormat format = quad.getFormat();
        if (format == null || !format.hasUvOffset(0)) {
            return quad;
        }
        int stride = format.getSize() / 4;
        int uvOffset = format.getUvOffsetById(0) / 4;
        int[] data = quad.getVertexData().clone();
        int vertexCount = data.length / stride;
        for (int i = 0; i < vertexCount; i++) {
            int base = i * stride + uvOffset;
            float u = Float.intBitsToFloat(data[base]);
            float v = Float.intBitsToFloat(data[base + 1]);
            data[base] = Float.floatToRawIntBits(u * pair.scaleU + pair.offsetU);
            data[base + 1] = Float.floatToRawIntBits(v * pair.scaleV + pair.offsetV);
        }
        return new BakedQuad(data, quad.getTintIndex(), quad.getFace(), pair.opaque,
                quad.shouldApplyDiffuseLighting(), format);
    }

    private static String opaqueSpriteName(String texturePath) {
        return "celeritasleafculling:" + texturePath + "_opaque";
    }

    private static TextureAtlasSprite createOpaqueVariant(IResourceManager manager, String texturePath) throws IOException {
        ResourceLocation source = new ResourceLocation("minecraft", "textures/" + texturePath + ".png");
        int width;
        int height;
        int[] pixels;
        try (IResource resource = manager.getResource(source)) {
            BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());
            width = image.getWidth();
            height = image.getHeight();
            if (height != width) {
                height = width;
            }
            pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
        }
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] |= 0xFF000000;
        }
        return new GeneratedSprite(opaqueSpriteName(texturePath), pixels, width, height);
    }

    private static final class GeneratedSprite extends TextureAtlasSprite {
        private final int[] pixels;
        private final int spriteWidth;
        private final int spriteHeight;

        GeneratedSprite(String name, int[] pixels, int width, int height) {
            super(name);
            this.pixels = pixels;
            this.spriteWidth = width;
            this.spriteHeight = height;
        }

        @Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
            return true;
        }

        @Override
        public boolean load(IResourceManager manager, ResourceLocation location,
                            Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
            int[][] frame = new int[Minecraft.getMinecraft().gameSettings.mipmapLevels + 1][];
            frame[0] = this.pixels;
            this.framesTextureData.add(frame);
            this.setIconWidth(this.spriteWidth);
            this.setIconHeight(this.spriteHeight);
            return false;
        }
    }

    private static final class SpritePair {
        private final TextureAtlasSprite opaque;
        private final float scaleU;
        private final float scaleV;
        private final float offsetU;
        private final float offsetV;

        SpritePair(TextureAtlasSprite original, TextureAtlasSprite opaque) {
            this.opaque = opaque;
            this.scaleU = (opaque.getMaxU() - opaque.getMinU()) / (original.getMaxU() - original.getMinU());
            this.scaleV = (opaque.getMaxV() - opaque.getMinV()) / (original.getMaxV() - original.getMinV());
            this.offsetU = opaque.getMinU() - original.getMinU() * this.scaleU;
            this.offsetV = opaque.getMinV() - original.getMinV() * this.scaleV;
        }
    }
}
