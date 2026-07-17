package net.vulkanic.backends.vulkan;

import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.vulkanic.PipelineDescriptor;
import org.lwjgl.vulkan.VK10;

final class VulkanPipelineFormatClassifier {
    private VulkanPipelineFormatClassifier() {
    }

    static int toVkVertexElementFormat(VertexFormatElement element) {
        return toVkVertexElementFormat(element, null);
    }

    static int toVkVertexElementFormat(VertexFormatElement element, String attributeName) {
        VertexFormatElement.Type type = element.type();
        int count = element.count();
        boolean useIntegerFormat = (element.usage() == VertexFormatElement.Usage.GENERIC
            || element.usage() == VertexFormatElement.Usage.UV)
            && !isSodiumIrisFloatAttribute(element);

        return switch (type) {
            case FLOAT -> switch (count) {
                case 1 -> VK10.VK_FORMAT_R32_SFLOAT;
                case 2 -> VK10.VK_FORMAT_R32G32_SFLOAT;
                case 3 -> VK10.VK_FORMAT_R32G32B32_SFLOAT;
                case 4 -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
                default -> throw new IllegalArgumentException("Unsupported FLOAT vertex component count: " + count);
            };
            case UBYTE -> switch (count) {
                case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R8_UINT : VK10.VK_FORMAT_R8_UNORM;
                case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8_UINT : VK10.VK_FORMAT_R8G8_UNORM;
                case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8_UINT : VK10.VK_FORMAT_R8G8B8_UNORM;
                case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8A8_UINT : VK10.VK_FORMAT_R8G8B8A8_UNORM;
                default -> throw new IllegalArgumentException("Unsupported UBYTE vertex component count: " + count);
            };
            case BYTE -> switch (count) {
                case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R8_SINT : VK10.VK_FORMAT_R8_SNORM;
                case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8_SINT : VK10.VK_FORMAT_R8G8_SNORM;
                case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8_SINT : VK10.VK_FORMAT_R8G8B8_SNORM;
                case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R8G8B8A8_SINT : VK10.VK_FORMAT_R8G8B8A8_SNORM;
                default -> throw new IllegalArgumentException("Unsupported BYTE vertex component count: " + count);
            };
            case SHORT -> switch (count) {
                case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R16_SINT : VK10.VK_FORMAT_R16_SSCALED;
                case 2 -> "mc_Entity".equals(attributeName)
                    ? VK10.VK_FORMAT_R16G16_SSCALED
                    : useIntegerFormat ? VK10.VK_FORMAT_R16G16_SINT : VK10.VK_FORMAT_R16G16_SSCALED;
                case 3 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16_SINT : VK10.VK_FORMAT_R16G16B16_SSCALED;
                case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16A16_SINT : VK10.VK_FORMAT_R16G16B16A16_SSCALED;
                default -> throw new IllegalArgumentException("Unsupported SHORT vertex component count: " + count);
            };
            case USHORT -> switch (count) {
                case 1 -> useIntegerFormat ? VK10.VK_FORMAT_R16_UINT : VK10.VK_FORMAT_R16_USCALED;
                case 2 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16_UINT : VK10.VK_FORMAT_R16G16_USCALED;
                case 3 -> "iris_Entity".equals(attributeName)
                    ? VK10.VK_FORMAT_R16G16B16_SINT
                    : useIntegerFormat ? VK10.VK_FORMAT_R16G16B16_UINT : VK10.VK_FORMAT_R16G16B16_USCALED;
                case 4 -> useIntegerFormat ? VK10.VK_FORMAT_R16G16B16A16_UINT : VK10.VK_FORMAT_R16G16B16A16_USCALED;
                default -> throw new IllegalArgumentException("Unsupported USHORT vertex component count: " + count);
            };
            case INT -> switch (count) {
                case 1 -> VK10.VK_FORMAT_R32_SINT;
                case 2 -> VK10.VK_FORMAT_R32G32_SINT;
                case 3 -> VK10.VK_FORMAT_R32G32B32_SINT;
                case 4 -> VK10.VK_FORMAT_R32G32B32A32_SINT;
                default -> throw new IllegalArgumentException("Unsupported INT vertex component count: " + count);
            };
            case UINT -> switch (count) {
                case 1 -> VK10.VK_FORMAT_R32_UINT;
                case 2 -> VK10.VK_FORMAT_R32G32_UINT;
                case 3 -> VK10.VK_FORMAT_R32G32B32_UINT;
                case 4 -> VK10.VK_FORMAT_R32G32B32A32_UINT;
                default -> throw new IllegalArgumentException("Unsupported UINT vertex component count: " + count);
            };
        };
    }

    static PipelineDescriptor.VertexAttributeFormat toPipelineVertexElementFormat(VertexFormatElement element, String attributeName) {
        return toPipelineVertexAttributeFormat(toVkVertexElementFormat(element, attributeName));
    }

    private static PipelineDescriptor.VertexAttributeFormat toPipelineVertexAttributeFormat(int vkFormat) {
        return switch (vkFormat) {
            case VK10.VK_FORMAT_R8_UNORM -> PipelineDescriptor.VertexAttributeFormat.R8_UNORM;
            case VK10.VK_FORMAT_R8G8_UNORM -> PipelineDescriptor.VertexAttributeFormat.R8G8_UNORM;
            case VK10.VK_FORMAT_R8G8B8_UNORM -> PipelineDescriptor.VertexAttributeFormat.R8G8B8_UNORM;
            case VK10.VK_FORMAT_R8G8B8A8_UNORM -> PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UNORM;
            case VK10.VK_FORMAT_R8_UINT -> PipelineDescriptor.VertexAttributeFormat.R8_UINT;
            case VK10.VK_FORMAT_R8G8_UINT -> PipelineDescriptor.VertexAttributeFormat.R8G8_UINT;
            case VK10.VK_FORMAT_R8G8B8_UINT -> PipelineDescriptor.VertexAttributeFormat.R8G8B8_UINT;
            case VK10.VK_FORMAT_R8G8B8A8_UINT -> PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UINT;
            case VK10.VK_FORMAT_R8_SNORM -> PipelineDescriptor.VertexAttributeFormat.R8_SNORM;
            case VK10.VK_FORMAT_R8G8_SNORM -> PipelineDescriptor.VertexAttributeFormat.R8G8_SNORM;
            case VK10.VK_FORMAT_R8G8B8_SNORM -> PipelineDescriptor.VertexAttributeFormat.R8G8B8_SNORM;
            case VK10.VK_FORMAT_R8G8B8A8_SNORM -> PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SNORM;
            case VK10.VK_FORMAT_R8_SINT -> PipelineDescriptor.VertexAttributeFormat.R8_SINT;
            case VK10.VK_FORMAT_R8G8_SINT -> PipelineDescriptor.VertexAttributeFormat.R8G8_SINT;
            case VK10.VK_FORMAT_R8G8B8_SINT -> PipelineDescriptor.VertexAttributeFormat.R8G8B8_SINT;
            case VK10.VK_FORMAT_R8G8B8A8_SINT -> PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SINT;
            case VK10.VK_FORMAT_R16_USCALED -> PipelineDescriptor.VertexAttributeFormat.R16_USCALED;
            case VK10.VK_FORMAT_R16G16_USCALED -> PipelineDescriptor.VertexAttributeFormat.R16G16_USCALED;
            case VK10.VK_FORMAT_R16G16B16_USCALED -> PipelineDescriptor.VertexAttributeFormat.R16G16B16_USCALED;
            case VK10.VK_FORMAT_R16G16B16A16_USCALED -> PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_USCALED;
            case VK10.VK_FORMAT_R16_UINT -> PipelineDescriptor.VertexAttributeFormat.R16_UINT;
            case VK10.VK_FORMAT_R16G16_UINT -> PipelineDescriptor.VertexAttributeFormat.R16G16_UINT;
            case VK10.VK_FORMAT_R16G16B16_UINT -> PipelineDescriptor.VertexAttributeFormat.R16G16B16_UINT;
            case VK10.VK_FORMAT_R16G16B16A16_UINT -> PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_UINT;
            case VK10.VK_FORMAT_R16_SSCALED -> PipelineDescriptor.VertexAttributeFormat.R16_SSCALED;
            case VK10.VK_FORMAT_R16G16_SSCALED -> PipelineDescriptor.VertexAttributeFormat.R16G16_SSCALED;
            case VK10.VK_FORMAT_R16G16B16_SSCALED -> PipelineDescriptor.VertexAttributeFormat.R16G16B16_SSCALED;
            case VK10.VK_FORMAT_R16G16B16A16_SSCALED -> PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_SSCALED;
            case VK10.VK_FORMAT_R16_SINT -> PipelineDescriptor.VertexAttributeFormat.R16_SINT;
            case VK10.VK_FORMAT_R16G16_SINT -> PipelineDescriptor.VertexAttributeFormat.R16G16_SINT;
            case VK10.VK_FORMAT_R16G16B16_SINT -> PipelineDescriptor.VertexAttributeFormat.R16G16B16_SINT;
            case VK10.VK_FORMAT_R16G16B16A16_SINT -> PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_SINT;
            case VK10.VK_FORMAT_R32_SFLOAT -> PipelineDescriptor.VertexAttributeFormat.R32_SFLOAT;
            case VK10.VK_FORMAT_R32G32_SFLOAT -> PipelineDescriptor.VertexAttributeFormat.R32G32_SFLOAT;
            case VK10.VK_FORMAT_R32G32B32_SFLOAT -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT;
            case VK10.VK_FORMAT_R32G32B32A32_SFLOAT -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT;
            case VK10.VK_FORMAT_R32_SINT -> PipelineDescriptor.VertexAttributeFormat.R32_SINT;
            case VK10.VK_FORMAT_R32G32_SINT -> PipelineDescriptor.VertexAttributeFormat.R32G32_SINT;
            case VK10.VK_FORMAT_R32G32B32_SINT -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT;
            case VK10.VK_FORMAT_R32G32B32A32_SINT -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SINT;
            case VK10.VK_FORMAT_R32_UINT -> PipelineDescriptor.VertexAttributeFormat.R32_UINT;
            case VK10.VK_FORMAT_R32G32_UINT -> PipelineDescriptor.VertexAttributeFormat.R32G32_UINT;
            case VK10.VK_FORMAT_R32G32B32_UINT -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_UINT;
            case VK10.VK_FORMAT_R32G32B32A32_UINT -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_UINT;
            default -> throw new IllegalArgumentException("Unsupported Vulkan vertex attribute format: " + vkFormat);
        };
    }

    static int toVkVertexAttributeFormat(PipelineDescriptor.VertexAttributeFormat format) {
        return switch (format) {
            case R8_UNORM -> VK10.VK_FORMAT_R8_UNORM;
            case R8G8_UNORM -> VK10.VK_FORMAT_R8G8_UNORM;
            case R8G8B8_UNORM -> VK10.VK_FORMAT_R8G8B8_UNORM;
            case R8G8B8A8_UNORM -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
            case R8_UINT -> VK10.VK_FORMAT_R8_UINT;
            case R8G8_UINT -> VK10.VK_FORMAT_R8G8_UINT;
            case R8G8B8_UINT -> VK10.VK_FORMAT_R8G8B8_UINT;
            case R8G8B8A8_UINT -> VK10.VK_FORMAT_R8G8B8A8_UINT;
            case R8_SNORM -> VK10.VK_FORMAT_R8_SNORM;
            case R8G8_SNORM -> VK10.VK_FORMAT_R8G8_SNORM;
            case R8G8B8_SNORM -> VK10.VK_FORMAT_R8G8B8_SNORM;
            case R8G8B8A8_SNORM -> VK10.VK_FORMAT_R8G8B8A8_SNORM;
            case R8_SINT -> VK10.VK_FORMAT_R8_SINT;
            case R8G8_SINT -> VK10.VK_FORMAT_R8G8_SINT;
            case R8G8B8_SINT -> VK10.VK_FORMAT_R8G8B8_SINT;
            case R8G8B8A8_SINT -> VK10.VK_FORMAT_R8G8B8A8_SINT;
            case R16_USCALED -> VK10.VK_FORMAT_R16_USCALED;
            case R16G16_USCALED -> VK10.VK_FORMAT_R16G16_USCALED;
            case R16G16B16_USCALED -> VK10.VK_FORMAT_R16G16B16_USCALED;
            case R16G16B16A16_USCALED -> VK10.VK_FORMAT_R16G16B16A16_USCALED;
            case R16_UINT -> VK10.VK_FORMAT_R16_UINT;
            case R16G16_UINT -> VK10.VK_FORMAT_R16G16_UINT;
            case R16G16B16_UINT -> VK10.VK_FORMAT_R16G16B16_UINT;
            case R16G16B16A16_UINT -> VK10.VK_FORMAT_R16G16B16A16_UINT;
            case R16_SSCALED -> VK10.VK_FORMAT_R16_SSCALED;
            case R16G16_SSCALED -> VK10.VK_FORMAT_R16G16_SSCALED;
            case R16G16B16_SSCALED -> VK10.VK_FORMAT_R16G16B16_SSCALED;
            case R16G16B16A16_SSCALED -> VK10.VK_FORMAT_R16G16B16A16_SSCALED;
            case R16_SINT -> VK10.VK_FORMAT_R16_SINT;
            case R16G16_SINT -> VK10.VK_FORMAT_R16G16_SINT;
            case R16G16B16_SINT -> VK10.VK_FORMAT_R16G16B16_SINT;
            case R16G16B16A16_SINT -> VK10.VK_FORMAT_R16G16B16A16_SINT;
            case R32_SFLOAT -> VK10.VK_FORMAT_R32_SFLOAT;
            case R32G32_SFLOAT -> VK10.VK_FORMAT_R32G32_SFLOAT;
            case R32G32B32_SFLOAT -> VK10.VK_FORMAT_R32G32B32_SFLOAT;
            case R32G32B32A32_SFLOAT -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
            case R32_SINT -> VK10.VK_FORMAT_R32_SINT;
            case R32G32_SINT -> VK10.VK_FORMAT_R32G32_SINT;
            case R32G32B32_SINT -> VK10.VK_FORMAT_R32G32B32_SINT;
            case R32G32B32A32_SINT -> VK10.VK_FORMAT_R32G32B32A32_SINT;
            case R32_UINT -> VK10.VK_FORMAT_R32_UINT;
            case R32G32_UINT -> VK10.VK_FORMAT_R32G32_UINT;
            case R32G32B32_UINT -> VK10.VK_FORMAT_R32G32B32_UINT;
            case R32G32B32A32_UINT -> VK10.VK_FORMAT_R32G32B32A32_UINT;
        };
    }

    static int toVkPrimitiveTopology(VertexFormat.Mode mode) {
        return switch (mode) {
            case LINES, DEBUG_LINES -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case LINE_STRIP, DEBUG_LINE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case TRIANGLES -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            case TRIANGLE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            case TRIANGLE_FAN -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
            case QUADS -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        };
    }

    private static boolean isSodiumIrisFloatAttribute(VertexFormatElement element) {
        return (element.index() == 10 && element.type() == VertexFormatElement.Type.BYTE && element.count() == 4)
            || (element.index() == 12 && element.type() == VertexFormatElement.Type.USHORT && element.count() == 2)
            || (element.index() == 13 && element.type() == VertexFormatElement.Type.BYTE && element.count() == 4)
            || (element.index() == 14 && element.type() == VertexFormatElement.Type.BYTE && element.count() == 4);
    }
}
