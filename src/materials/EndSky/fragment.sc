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
    nl_environment env;
    env.end = true;
    env.nether = false;
    env.underwater = true;
    env.rainFactor = 0.0;
    
    vec4 diffuse = texture2D(s_SkyTexture, v_texcoord0);
    vec3 viewDir = normalize(v_posTime.xyz);

    vec3 color = renderEndSky(getEndHorizonCol(), getEndZenithCol(), normalize(v_posTime.xyz), v_posTime.w);

    color += 2.8*diffuse.rgb; // stars
    color += 1.5*nlRenderGalaxy(viewDir, vec3_splat(0.0), env, v_posTime.w);
    #ifdef NL_END_VORTEX
      vec4 vt = renderVortex(viewDir, v_posTime.w);
      color *= vt.a;
      color += vt.rgb;
    #endif
    color = colorCorrection(color);

    gl_FragColor = vec4(color, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
