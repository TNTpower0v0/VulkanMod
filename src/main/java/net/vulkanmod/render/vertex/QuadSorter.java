package net.vulkanmod.render.vertex;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.util.SortUtil;
import org.lwjgl.system.MemoryUtil;

public class QuadSorter {

    private QuadBounds[] sortingBounds;
    private float sortX = Float.NaN;
    private float sortY = Float.NaN;
    private float sortZ = Float.NaN;
    private boolean indexOnly;

    private VertexFormat format;
    private int vertexCount;
    private int indexCount;

    public void setQuadSortOrigin(float x, float y, float z) {
        this.sortX = x;
        this.sortY = y;
        this.sortZ = z;
    }

    public SortState getSortState() {
        return new SortState(this.vertexCount, this.sortingBounds);
    }

    public void restoreSortState(QuadSorter.SortState sortState) {
        this.vertexCount = sortState.vertexCount;
        this.sortingBounds = sortState.sortingBounds;

        this.indexOnly = true;
    }

    public void setupQuadSortingPoints(long bufferPtr, int vertexCount, VertexFormat format) {
        this.vertexCount = vertexCount;
        int pointCount = vertexCount / 4;
        QuadBounds[] sortingBounds = new QuadBounds[pointCount];

        int vertexSize = format.getVertexSize();
        int quadStride = vertexSize * 4;

        if (format == CustomVertexFormat.COMPRESSED_TERRAIN) {
            final float invConv = 1.0f / VertexBuilder.CompressedVertexBuilder.POS_CONV_MUL;
            for (int m = 0; m < pointCount; ++m) {
                long ptr = bufferPtr + (long) m * quadStride;
                sortingBounds[m] = readCompressedBounds(ptr, vertexSize, invConv);
            }
        } else {
            for (int m = 0; m < pointCount; ++m) {
                long ptr = bufferPtr + (long) m * quadStride;
                sortingBounds[m] = readBounds(ptr, vertexSize);
            }
        }

        this.sortingBounds = sortingBounds;
    }

    private static QuadBounds readCompressedBounds(long ptr, int vertexSize, float invConv) {
        final float convOffset = -VertexBuilder.CompressedVertexBuilder.POS_OFFSET;
        QuadBounds bounds = new QuadBounds();

        for (int vertex = 0; vertex < 4; vertex++, ptr += vertexSize) {
            float x = MemoryUtil.memGetShort(ptr) * invConv + convOffset;
            float y = MemoryUtil.memGetShort(ptr + 2) * invConv + convOffset;
            float z = MemoryUtil.memGetShort(ptr + 4) * invConv + convOffset;
            bounds.include(x, y, z);
        }

        return bounds;
    }

    private static QuadBounds readBounds(long ptr, int vertexSize) {
        QuadBounds bounds = new QuadBounds();

        for (int vertex = 0; vertex < 4; vertex++, ptr += vertexSize) {
            bounds.include(MemoryUtil.memGetFloat(ptr), MemoryUtil.memGetFloat(ptr + 4), MemoryUtil.memGetFloat(ptr + 8));
        }

        return bounds;
    }

    public void putSortedQuadIndices(TerrainBufferBuilder bufferBuilder, VertexFormat.IndexType indexType) {
        int[] sortingPointsIndices = this.sortQuads();

        long ptr = bufferBuilder.getPtr();

        final int size = indexType.bytes;
        final int stride = 4; // 4 vertices in a quad
        for (int i = 0; i < sortingPointsIndices.length; ++i) {
            final int quadIndex = sortingPointsIndices[i];
            final int baseVertex = quadIndex * stride;

            putQuadIndices(ptr, indexType, baseVertex);

            ptr += size * 6L;
        }
    }

    public void putSortedQuadIndices(TerrainBuilder bufferBuilder, VertexFormat.IndexType indexType) {
        int[] sortingPoints = this.sortQuads();

        long ptr = bufferBuilder.indexBufferPtr;

        final int size = indexType.bytes;
        final int stride = 4; // 4 vertices in a quad
        for (int i = 0; i < sortingPoints.length; ++i) {
            final int quadIndex = sortingPoints[i];
            final int baseVertex = quadIndex * stride;

            putQuadIndices(ptr, indexType, baseVertex);

            ptr += size * 6L;
        }
    }

    private int[] sortQuads() {
        int[] indices = new int[this.sortingBounds.length];
        float[] distances = new float[this.sortingBounds.length];

        for (int i = 0; i < this.sortingBounds.length; indices[i] = i++) {
            distances[i] = this.sortingBounds[i].distanceToClosestPointSquared(this.sortX, this.sortY, this.sortZ);
        }

        SortUtil.mergeSort(indices, distances);
        return indices;
    }

    private static void putQuadIndices(long ptr, VertexFormat.IndexType indexType, int baseVertex) {
        if (indexType == VertexFormat.IndexType.SHORT) {
            MemoryUtil.memPutShort(ptr, (short) baseVertex);
            MemoryUtil.memPutShort(ptr + 2, (short) (baseVertex + 1));
            MemoryUtil.memPutShort(ptr + 4, (short) (baseVertex + 2));
            MemoryUtil.memPutShort(ptr + 6, (short) (baseVertex + 2));
            MemoryUtil.memPutShort(ptr + 8, (short) (baseVertex + 3));
            MemoryUtil.memPutShort(ptr + 10, (short) baseVertex);
        } else {
            MemoryUtil.memPutInt(ptr, baseVertex);
            MemoryUtil.memPutInt(ptr + 4, baseVertex + 1);
            MemoryUtil.memPutInt(ptr + 8, baseVertex + 2);
            MemoryUtil.memPutInt(ptr + 12, baseVertex + 2);
            MemoryUtil.memPutInt(ptr + 16, baseVertex + 3);
            MemoryUtil.memPutInt(ptr + 20, baseVertex);
        }
    }

    public void reset() {
        this.vertexCount = 0;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getIndexCount() {
        return indexCount;
    }

    public static class SortState {
        final int vertexCount;
        final QuadBounds[] sortingBounds;

        SortState(int vertexCount, QuadBounds[] sortingBounds) {
            this.vertexCount = vertexCount;
            this.sortingBounds = sortingBounds;
        }
    }

    private static class QuadBounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        private void include(float x, float y, float z) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.maxZ = Math.max(this.maxZ, z);
        }

        private float distanceToClosestPointSquared(float x, float y, float z) {
            float dx = Math.max(Math.max(this.minX - x, 0.0f), x - this.maxX);
            float dy = Math.max(Math.max(this.minY - y, 0.0f), y - this.maxY);
            float dz = Math.max(Math.max(this.minZ - z, 0.0f), z - this.maxZ);
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
