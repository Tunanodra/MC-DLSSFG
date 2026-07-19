#version 430 core

layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

layout(binding = 0) uniform sampler2D inputDepth;
layout(binding = 1) uniform sampler2D previousDepth;
layout(binding = 2, rg16f) uniform writeonly image2D outputMotionVectors;
layout(binding = 3, r32f) uniform writeonly image2D outputDepth;

const float INVALID_MV = -32768.0;
const float NEAR_PLANE = 0.05;

layout(std140, binding = 4) uniform ReprojectData {
    mat4 invCurrentViewProjection;
    mat4 previousViewProjection;
    vec2 renderSize;
    float reset;
    float hasPreviousDepth;
};

float linearizeDepth(float depth) {
    return NEAR_PLANE / max(1.0 - depth, 1e-6);
}

void main() {
    ivec2 texelCoord = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(outputMotionVectors);
    if (texelCoord.x >= size.x || texelCoord.y >= size.y) {
        return;
    }
    vec2 uv = (vec2(texelCoord) + 0.5) / vec2(size);
    float depth = texture(inputDepth, uv).r;
    imageStore(outputDepth, texelCoord, vec4(depth, 0.0, 0.0, 0.0));
    if (reset > 0.5) {
        imageStore(outputMotionVectors, texelCoord, vec4(INVALID_MV, INVALID_MV, 0.0, 0.0));
        return;
    }
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = invCurrentViewProjection * ndc;
    world /= world.w;
    vec4 previousClip = previousViewProjection * world;
    vec2 previousUV = previousClip.xy / previousClip.w * 0.5 + 0.5;
    vec2 motion = (previousUV - uv) * renderSize;
    if (hasPreviousDepth > 0.5 && dot(motion, motion) < 0.0025) {
        motion = vec2(0.0);
    }
    imageStore(outputMotionVectors, texelCoord, vec4(motion, 0.0, 0.0));
}
