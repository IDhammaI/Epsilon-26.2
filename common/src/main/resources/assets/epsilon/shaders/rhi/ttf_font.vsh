#version 410 core

layout(std140) uniform LuminTransform {
    mat4 u_Projection;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

out vec4 v_Color;
out vec2 v_TexCoord;

void main() {
    gl_Position = u_Projection * vec4(Position, 1.0);

    v_Color = Color;
    v_TexCoord = UV0;
}
