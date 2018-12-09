varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform sampler2D r;

void main()
{
  vec3 b = texture2D(r, v_texCoords).rgb;
  gl_FragColor = vec4(
    b * (0.82 - 0.3 * b.r * b.r) +
    texture2D(u_texture, v_texCoords).rgb,
    texture2D(u_texture, v_texCoords).a);
}