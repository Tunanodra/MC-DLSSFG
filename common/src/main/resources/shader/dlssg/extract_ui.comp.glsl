#version 430 core

layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

layout(binding = 0) uniform sampler2D finalColor;
layout(binding = 1) uniform sampler2D hudlessColor;
layout(binding = 2, rgba8) uniform writeonly image2D outputUI;

const float NOISE_FLOOR = 1.5 / 255.0;

void main() {
    ivec2 texelCoord = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(outputUI);
    if (texelCoord.x >= size.x || texelCoord.y >= size.y) {
        return;
    }
    vec4 finalPixel = texelFetch(finalColor, texelCoord, 0);
    vec4 hudlessPixel = texelFetch(hudlessColor, texelCoord, 0);
    vec3 diff = max(finalPixel.rgb - hudlessPixel.rgb, vec3(0.0));
    float alpha = max(diff.r, max(diff.g, diff.b));
    if (alpha < NOISE_FLOOR) {
        imageStore(outputUI, texelCoord, vec4(0.0));
        return;
    }
    imageStore(outputUI, texelCoord, vec4(diff, alpha));
}
