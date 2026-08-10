#version 300 es
precision mediump float;

in vec3 vColor;
out vec4 fragColor;

uniform float uAlpha;

void main() {
    fragColor = vec4(vColor, uAlpha);
}
