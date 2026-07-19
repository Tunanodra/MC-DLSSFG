#version 430 core

layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

layout(binding = 0) uniform sampler2D inputMotionVectors;
layout(binding = 1) uniform sampler2D inputDepth;
layout(binding = 2, rg16f) uniform writeonly image2D outputMotionVectors;

const float INVALID_MV = -32768.0;
const float NEAR_PLANE = 0.05;

float linearizeDepth(float depth) {
    return NEAR_PLANE / max(1.0 - depth, 1e-6);
}

bool isInvalid(vec2 mv) {
    return mv.x <= INVALID_MV + 1.0;
}

void main() {
    ivec2 texelCoord = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(outputMotionVectors);
    if (texelCoord.x >= size.x || texelCoord.y >= size.y) {
        return;
    }
    vec2 center = texelFetch(inputMotionVectors, texelCoord, 0).rg;
    if (!isInvalid(center)) {
        imageStore(outputMotionVectors, texelCoord, vec4(center, 0.0, 0.0));
        return;
    }
    float centerLinear = linearizeDepth(texelFetch(inputDepth, texelCoord, 0).r);
    vec2 best = vec2(0.0);
    float bestScore = 1e30;
    bool found = false;
    for (int dy = -2; dy <= 2; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
            ivec2 sampleCoord = texelCoord + ivec2(dx, dy);
            if (sampleCoord.x < 0 || sampleCoord.y < 0 || sampleCoord.x >= size.x || sampleCoord.y >= size.y) {
                continue;
            }
            vec2 candidate = texelFetch(inputMotionVectors, sampleCoord, 0).rg;
            if (isInvalid(candidate)) {
                continue;
            }
            float candidateLinear = linearizeDepth(texelFetch(inputDepth, sampleCoord, 0).r);
            float depthScore = abs(candidateLinear - centerLinear) / max(centerLinear, 1e-4);
            float distanceScore = length(vec2(dx, dy)) * 0.25;
            float score = depthScore + distanceScore;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
                found = true;
            }
        }
    }
    imageStore(outputMotionVectors, texelCoord, vec4(found ? best : vec2(0.0), 0.0, 0.0));
}
