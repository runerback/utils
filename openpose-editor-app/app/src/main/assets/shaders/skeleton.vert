#version 300 es
in vec3 aPosition;

uniform vec3 uColor;
uniform mat4 uMvpMatrix;

out vec3 vColor;

void main() {
    vColor = uColor;
    gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
}
