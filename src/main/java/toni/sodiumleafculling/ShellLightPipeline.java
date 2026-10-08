package toni.sodiumleafculling;

import dhj.embeddedt.embeddium.impl.model.light.LightPipeline;
import dhj.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import dhj.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import dhj.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.jetbrains.annotations.NotNull;

public class ShellLightPipeline implements LightPipeline {
    private final LightPipeline delegate;

    public ShellLightPipeline(LightPipeline delegate) {
        this.delegate = delegate;
    }

    @Override
    public void calculate(ModelQuadView quad, int x, int y, int z, QuadLightData out,
                          @NotNull ModelQuadFacing cullFace, @NotNull ModelQuadFacing lightFace,
                          boolean shade, boolean applyAoDepthBlending) {
        ModelQuadFacing step = cullFace.isDirection() ? cullFace : lightFace;
        this.delegate.calculate(quad,
                x + step.getStepX(), y + step.getStepY(), z + step.getStepZ(),
                out, cullFace, lightFace, shade, applyAoDepthBlending);
    }

    @Override
    public void reset() {
        this.delegate.reset();
    }
}
