#ifndef INSTANCING
$input v_texcoord0, v_posTime
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>

  SAMPLER2D_AUTOREG(s_SkyTexture);
#endif

void main() {
  #ifndef INSTANCING
    
    vec4 diffuse = texture2D(s_SkyTexture, v_texcoord0);
    vec3 vDir = normalize(v_posTime.xyz);

    vec3 color = renderEndSky(getEndHorizonCol(), getEndZenithCol(), vDir, v_posTime.w);
    color += 2.8*diffuse.rgb; // stars
    color += 1.5*endGalaxy(vDir, v_posTime.w);
    #ifdef NL_END_VORTEX
      vec4 vortex = renderVortex(vDir, v_posTime.w);
      color *= vortex.a;
      color += vortex.rgb;
    #endif

    color = colorCorrection(color);

    gl_FragColor = vec4(color, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
