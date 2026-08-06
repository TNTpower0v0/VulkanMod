package net.vulkanmod.render.chunk.build.task;

import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.chunk.build.thread.ThreadBuilderPack;
import net.vulkanmod.render.vertex.QuadSorter;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Vector3d;

public class SortTransparencyTask extends ChunkTask {

    public SortTransparencyTask(RenderSection renderSection, Vector3d cameraPos) {
        super(renderSection, cameraPos);
    }

    public String name() {
        return "rend_chk_sort";
    }

    public Result runTask(BuilderResources context) {
        ThreadBuilderPack builderPack = context.builderPack;

        if (this.cancelled.get()) {
            return Result.CANCELLED;
        }

        Vector3d cameraPos = WorldRenderer.getCameraPos();
        float x = (float) cameraPos.x;
        float y = (float) cameraPos.y;
        float z = (float) cameraPos.z;

        CompiledSection compiledSection = this.section.getCompiledSection();
        QuadSorter.SortState transparencyState = compiledSection.transparencyState;
        QuadSorter.SortState iceTransparencyState = compiledSection.iceTransparencyState;

        if (transparencyState == null && iceTransparencyState == null) {
            return Result.CANCELLED;
        }

        CompileResult compileResult = new CompileResult(this.section, false);
        if (transparencyState != null) {
            sortLayer(builderPack, compileResult, TerrainRenderType.TRANSLUCENT, transparencyState, x, y, z);
        }
        if (iceTransparencyState != null) {
            sortLayer(builderPack, compileResult, TerrainRenderType.ICE, iceTransparencyState, x, y, z);
        }

        if (this.cancelled.get()) {
            compileResult.renderedLayers.values().forEach(UploadBuffer::release);
            return Result.CANCELLED;
        }

        taskDispatcher.scheduleSectionUpdate(compileResult);
        return Result.SUCCESSFUL;
    }

    private void sortLayer(ThreadBuilderPack builderPack, CompileResult compileResult, TerrainRenderType renderType,
                           QuadSorter.SortState sortState, float x, float y, float z) {
        TerrainBuilder bufferBuilder = builderPack.builder(renderType);
        bufferBuilder.begin();
        bufferBuilder.restoreSortState(sortState);

        bufferBuilder.setupQuadSorting(x - (float) this.section.xOffset(), y - (float) this.section.yOffset(), z - (float) this.section.zOffset());
        TerrainBuilder.DrawState drawState = bufferBuilder.endDrawing();

        UploadBuffer uploadBuffer = new UploadBuffer(bufferBuilder, drawState);
        compileResult.renderedLayers.put(renderType, uploadBuffer);

        bufferBuilder.reset();
    }
}
