$input a_color0, a_position
#ifdef INSTANCING
  $input i_data0, i_data1, i_data2, i_data3
#endif
$output v_color0
#include <newb/config.h>
#if NL_CLOUD_TYPE >= 2
  $output v_color1, v_color2, v_fogColor
#endif

#include <bgfx_shader.sh>
#include <newb/main.sh>

// uniform vec4 CloudColor;
uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec3 CameraPosition;
uniform vec4 TimeOfDay;
uniform vec4 Day;

float fog_fade(vec3 wPos) {
  return clamp(2.0-length(wPos*vec3(0.005, 0.002, 0.005)), 0.0, 1.0);
}

void main() {
  #ifdef INSTANCING
    mat4 model = mtxFromCols(i_data0, i_data1, i_data2, i_data3);
  #else
    mat4 model = u_model[0];
  #endif

  float t = ViewPositionAndTime.w;
  float rain = detectRain(FogAndDistanceControl.xyz);

  nl_environment env;
  env.end = false;
  env.nether = false;
  env.underwater = false;
  env.rainFactor = rain;
  env.fogCol = FogColor.rgb;
  env = calculateSunParams(env, TimeOfDay.x, Day.x);

  nl_skycolor skycol = nlOverworldSkyColors(env);
  vec3 pos = a_position;
  vec3 worldPos;

  #if NL_CLOUD_TYPE <= 2

    vec4 color;

    #if NL_CLOUD_TYPE == 0
      float thickness = NL_CLOUD0_THICKNESS + rain*(NL_CLOUD0_RAIN_THICKNESS - NL_CLOUD0_THICKNESS);
      // clouds.png has two non-overlaping layers:
      // r=unused, g=layers, b=reference, a=unused
      // g=0 (layer 0), g=1 (layer 1)
      bool isL2 = a_color0.g > 0.5 * a_color0.b;
      
      if (!isL2) {
        // NORMAL CLOUDS LAYER
        pos.y *= thickness;
        worldPos = mul(model, vec4(pos, 1.0)).xyz;

        // FIX: Use more balanced cloud colors (closer to original white clouds)
        // Reduce the zenith color influence and make clouds whiter
        vec3 cloudColor = mix(skycol.zenith, skycol.horizonEdge, 0.7);
        cloudColor = mix(cloudColor, vec3(1.0, 1.0, 1.0), 0.3); // Blend towards white
        color.rgb = cloudColor;
        
        // Keep some vertical variation but reduce intensity
        color.rgb += dot(color.rgb, vec3(0.2,0.3,0.2)) * a_position.y;
        color.rgb *= 1.0 - 0.8*rain;
        color.rgb = colorCorrection(color.rgb);
        color.a = NL_CLOUD0_OPACITY * fog_fade(worldPos.xyz);
      
        float local_y = pos.y;
        float norm_y = local_y / thickness;
        float cloud_base_y = worldPos.y - local_y;
        float cloud_mid_y = cloud_base_y + thickness * 0.5;
        float is_above = step(cloud_mid_y, CameraPosition.y);
        float fade = (is_above > 0.5) ? smoothstep(0.0, 0.2, norm_y) : (1.0 - smoothstep(0.8, 1.0, norm_y));
        color.a *= fade;
        
        vec3 viewDir = normalize(CameraPosition.xyz - worldPos);
        float facing = abs(dot(viewDir, vec3(0.0, 1.0, 0.0)));
        color.a *= smoothstep(0.1, 0.5, facing);
        
      } else {
        // AURORA LAYER
        #ifdef NL_AURORA
          // First calculate the base world position like normal clouds
          pos.y *= thickness;
          worldPos = mul(model, vec4(pos, 1.0)).xyz;
          
          // Then apply aurora animations and positioning
          worldPos.xyz += 4.0*sin(0.1*worldPos.zxx + vec3(0.2,0.5,0.3)*t);
          worldPos.y += 24.0*a_position.y*(1.0 + sin(0.1*(worldPos.z+worldPos.x) + 0.5*t));
          worldPos.y += NL_CLOUD0_THICKNESS + 20.0;
          
          vec4 aurora = renderAurora(worldPos, t, rain, FogColor.rgb);
          
          // FIX: Increase aurora intensity by multiplying and use brighter colors
          float auroraIntensity = 2.0; // Adjust this value (1.5-3.0) for desired intensity
          color.rgb = skycol.zenith + 0.8*skycol.horizonEdge;
          color.rgb += aurora.rgb * auroraIntensity;
          
          // Make aurora colors more vibrant
          color.rgb = mix(color.rgb, aurora.rgb * 1.5, 0.3);
          
          color.a = (a_position.y < 0.5 && a_color0.b > 0.9) ? aurora.a : 0.0;
          color.a *= fog_fade(worldPos.xyz);
          
          // Optional: Boost aurora alpha for more visibility
          color.a *= 1.5;
        #else
          worldPos = vec3(0.0,0.0,0.0);
          color.a = 0.0;
        #endif
      }
      
    #else
      pos.xz = pos.xz - 32.0;
      pos.y *= 0.01;
      worldPos.x = pos.x*model[0][0];
      worldPos.z = pos.z*model[2][2];
      #if BGFX_SHADER_LANGUAGE_GLSL
        worldPos.y = pos.y+model[3][1];
      #else
        worldPos.y = pos.y+model[1][3];
      #endif

      float fade = fog_fade(worldPos.xyz);
      #if NL_CLOUD_TYPE == 1
        // make cloud plane spherical
        float len = length(worldPos.xz)*0.01;
        worldPos.y -= len*len*clamp(0.2*worldPos.y, -1.0, 1.0);

        vec3 cloudPos = worldPos;
        cloudPos.xz += CameraPosition.xz;

        color = renderCloudsSimple(skycol, cloudPos, t, rain);

        // cloud depth
        worldPos.y -= NL_CLOUD1_DEPTH*color.a*3.3;

        color.a *= NL_CLOUD1_OPACITY;

        #ifdef NL_AURORA
          color += renderAurora(cloudPos, t, rain, FogColor.rgb)*(1.0-color.a);
        #endif

        color.a *= fade;
        color.rgb = colorCorrection(color.rgb);
      #else // NL_CLOUD_TYPE 2
        v_fogColor = FogColor.rgb;
        v_color1 = vec4(skycol.zenith, rain);
        v_color2 = vec4(skycol.horizonEdge, ViewPositionAndTime.w);
        color = vec4(worldPos, fade);
      #endif 
    #endif

    v_color0 = color;
    gl_Position = mul(u_viewProj, vec4(worldPos, 1.0));
  #else
    vec4 apos = vec4(pos.xz - 32.0, 1.0, 1.0);
    apos.x *= pos.y - 0.5;
    apos.xy = clamp(apos.xy, -1.0, 1.0);

    #if BGFX_SHADER_LANGUAGE_GLSL
      float h = model[3][1];
    #else
      float h = model[1][3];
    #endif
    h = clamp(0.002*h, 0.0, 1.0);

    worldPos = mul(u_invViewProj, apos).xyz;

    v_fogColor = FogColor.rgb;
    v_color0 = vec4(worldPos, h*h);
    v_color1 = vec4(skycol.zenith, rain);
    v_color2 = vec4(skycol.horizonEdge, ViewPositionAndTime.w);
    gl_Position = apos;
  #endif
}