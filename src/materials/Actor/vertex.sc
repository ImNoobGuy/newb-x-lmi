$input a_position, a_color0, a_texcoord0, a_indices, a_normal
#ifdef INSTANCING
  $input i_data0, i_data1, i_data2
#endif

$output v_color0, v_fog, v_light, v_texcoord0, v_wpos, v_edgemap, v_lightmapUV

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/DynamicUtil.dragonh>
#include <MinecraftRenderer.Materials/TAAUtil.dragonh>
#include <newb/main.sh>

uniform vec4 OverlayColor;
uniform vec4 TileLightColor;
uniform vec4 FogColor;
uniform vec4 FogControl;
uniform vec4 UVAnimation;
uniform mat4 Bones[8];
uniform vec4 ViewPositionAndTime;
uniform vec4 RenderDistance;
uniform vec4 DimensionID;
uniform vec4 TimeOfDay;
uniform vec4 Day;
uniform vec4 CameraPosition;

void main() {
  mat4 World = u_model[0];

  World = mul(World, Bones[int(a_indices)]);

  vec2 texcoord0 = a_texcoord0;
  texcoord0 = applyUvAnimation(texcoord0, UVAnimation);

  vec3 wpos;
  #ifdef INSTANCING
    mat4 model = mtxFromCols(i_data0, i_data1, i_data2, vec4(0.0, 0.0, 0.0, 1.0));
    wpos = instMul(model, vec4(a_position, 1.0)).xyz;
  #else
    wpos = mul(World, vec4(a_position, 1.0)).xyz;
  #endif

  vec4 position = jitterVertexPosition(wpos);

  #if !(defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY) || defined(INSTANCING))
    nl_environment env = nlDetectEnvironment(DimensionID.x, TimeOfDay.x, Day.x, FogColor.rgb, FogControl.xyz);
    nl_skycolor skycol = nlSkyColors(env);

    float relativeDist = position.z/FogControl.z;

    wpos.y = -wpos.y;
    vec3 viewDir = normalize(wpos.xyz);

    vec4 fogColor;
    fogColor.rgb = nlRenderSky(skycol, env, viewDir, ViewPositionAndTime.w, false);
    fogColor.a = nlRenderFogFade(env, skycol, fogColor.rgb, relativeDist, FogColor.rgb, FogControl.xy, wpos.xyz, vec3_splat(0.0), ViewPositionAndTime.w);

    if (env.nether) {
      // blend fog with void color
      fogColor.rgb = colorCorrectionInv(FogColor.rgb);
    }

    vec3 light = nlEntityLighting(skycol, env, a_position, a_normal, wpos.xyz, World, TileLightColor, OverlayColor, skycol.horizonEdge, ViewPositionAndTime.w, TimeOfDay.x, RenderDistance.x, CameraPosition.xyz);

    v_texcoord0 = texcoord0;
    v_color0 = a_color0;
    v_fog = fogColor;
    v_wpos = wpos;
    #ifdef NL_ENTITY_EDGE_HIGHLIGHT
      v_edgemap = nlEntityEdgeHighlightPreprocess(texcoord0);
    #else
      v_edgemap = vec4_splat(0.0);
    #endif
    v_light = vec4(light, 1.0);
  #endif

  gl_Position = position;
}
