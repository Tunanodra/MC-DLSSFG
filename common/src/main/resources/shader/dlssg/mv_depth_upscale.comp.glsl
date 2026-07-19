#version 430 core

layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

layout(binding = 0) uniform sampler2D inputDepth;
layout(binding = 1) uniform sampler2D inputMotionVectors;
layout(binding = 2, r32f) uniform writeonly image2D outputDepth;
layout(binding = 3, rg16f) uniform writeonly image2D outputMotionVectors;

layout(std140, binding = 4) uniform UpscaleData {
    vec2 inputSize;
    vec2 outputSize;
};

void main() {
    ivec2 texelCoord = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(outputMotionVectors);
    if (texelCoord.x >= size.x || texelCoord.y >= size.y) {
        return;
    }
    vec2 uv = (vec2(texelCoord) + 0.5) / vec2(size);
    vec4 depth = texture(inputDepth, uv);
    imageStore(outputDepth, texelCoord, vec4(depth.r, 0.0, 0.0, 0.0));
    vec2 mv = texture(inputMotionVectors, uv).rg;
    mv *= outputSize / inputSize;
    imageStore(outputMotionVectors, texelCoord, vec4(mv, 0.0, 0.0));
}
