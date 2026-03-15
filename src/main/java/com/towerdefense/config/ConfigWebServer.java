package com.towerdefense.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.towerdefense.TowerDefenseMod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class ConfigWebServer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static HttpServer server;

    public static void start() {
        if (server != null) return;
        try {
            int port = ConfigManager.getInstance().getWebPort();
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newSingleThreadExecutor());

            server.createContext("/api/config", ConfigWebServer::handleConfig);
            server.createContext("/", ConfigWebServer::handleRoot);

            server.start();
            TowerDefenseMod.LOGGER.info("Config web UI: http://localhost:{}/", port);
        } catch (IOException e) {
            TowerDefenseMod.LOGGER.warn("Failed to start config web server: {}", e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            TowerDefenseMod.LOGGER.info("Config web server stopped.");
        }
    }

    private static void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        String html = getIndexHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void handleConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

        if ("GET".equals(method)) {
            TDConfig config = ConfigManager.getInstance().getConfig();
            String json = GSON.toJson(config);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        if ("PUT".equals(method) || "PATCH".equals(method)) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                String sanitized = sanitizeMapFields(body);
                TDConfig patch = GSON.fromJson(sanitized, TDConfig.class);
                if (patch == null) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Invalid JSON\"}");
                    return;
                }
                if ("PUT".equals(method)) {
                    ConfigManager.getInstance().updateConfig(patch);
                } else {
                    ConfigManager.getInstance().patchConfig(patch);
                }
                TowerDefenseMod mod = TowerDefenseMod.getInstance();
                if (mod != null && mod.getTowerRegistry() != null) {
                    mod.getTowerRegistry().reloadFromConfig();
                }
                sendJsonResponse(exchange, 200, "{\"ok\":true}");
            } catch (Exception e) {
                TowerDefenseMod.LOGGER.warn("Config update failed: {}", e.getMessage());
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
            return;
        }

        if ("OPTIONS".equals(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, PATCH, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        sendResponse(exchange, 405, "Method Not Allowed");
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Ensures weapons and walls are flat Map<String,Integer> (client may send nested objects). */
    private static String sanitizeMapFields(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (String key : new String[]{"weapons", "walls"}) {
                if (!root.has(key) || !root.get(key).isJsonObject()) continue;
                JsonObject map = root.getAsJsonObject(key);
                JsonObject flat = new JsonObject();
                for (String k : map.keySet()) {
                    var v = map.get(k);
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                        flat.add(k, v);
                    } else if (v.isJsonObject()) {
                        Integer n = extractInt(v.getAsJsonObject());
                        if (n != null) flat.addProperty(k, n);
                    }
                }
                root.add(key, flat);
            }
            return GSON.toJson(root);
        } catch (Exception e) {
            return json;
        }
    }

    private static Integer extractInt(JsonObject obj) {
        for (String k : new String[]{"price", "value", "amount"}) {
            if (obj.has(k) && obj.get(k).isJsonPrimitive()) {
                try { return obj.get(k).getAsInt(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String getIndexHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tower Defense Config</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:system-ui,sans-serif;background:#1a1a2e;color:#e0e0e0;padding:1.5rem;line-height:1.5}
h1{color:#e94560;margin-bottom:1rem;font-size:1.5rem}
h2{color:#0f3460;margin:1.5rem 0 0.75rem;font-size:1.1rem;padding-bottom:0.3rem;border-bottom:1px solid #333}
.actions{margin-bottom:1rem;display:flex;gap:0.5rem;flex-wrap:wrap}
button{background:#e94560;color:#fff;border:none;padding:0.5rem 1rem;border-radius:6px;cursor:pointer;font-weight:600;font-size:0.95rem}
button:hover{background:#ff6b6b}
button.save{background:#0f3460}
button.save:hover{background:#1a4a7a}
#status{margin-bottom:1rem;padding:0.5rem 0.75rem;border-radius:6px;min-height:1.5em}
#status.ok{background:#0f3460;color:#7f7}
#status.err{background:#600;color:#f88}
.form-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:0.75rem 1.5rem}
.field{display:flex;flex-direction:column;gap:0.25rem}
.field label{font-size:0.85rem;color:#aaa}
.field input{background:#0f3460;border:1px solid #333;color:#fff;padding:0.4rem 0.6rem;border-radius:4px;font-size:0.95rem}
.field input:focus{outline:none;border-color:#e94560}
.field-row{display:flex;align-items:center;gap:0.25rem}
.field-row input{flex:1}
.btn-sm{width:28px;height:28px;padding:0;font-size:1rem;line-height:1;background:#333;color:#fff;border-radius:4px;cursor:pointer}
.btn-sm:hover{background:#444}
.subsection{margin-left:1rem;padding-left:1rem;border-left:2px solid #333}
.subsection h3{color:#888;font-size:0.95rem;margin:0.75rem 0 0.4rem}
.empty-msg{color:#888;padding:1rem}
</style>
</head>
<body>
<h1>Tower Defense - Configuration</h1>
<div class="actions">
  <button onclick="load()">Recharger</button>
  <button class="save" onclick="save()">Enregistrer</button>
</div>
<div id="status"></div>
<div id="form"></div>
<script>
let configData = {};
const LABELS = {
  startingMoney:'Argent de départ',nexusMaxHp:'PV max Nexus',nexusExplosionRadius:'Rayon explosion Nexus',
  prepPhaseTicks:'Ticks phase prep',basePassiveIncome:'Revenu passif',basePassiveInterval:'Intervalle passif',
  defeatDelayTicks:'Délai défaite',chainExplosionDelay:'Délai explosion chaîne',
  size:'Taille arène',wallHeight:'Hauteur mur',arenaY:'Y arène',standDepth:'Profondeur stand',standHeight:'Hauteur stand',
  baseHp:'PV base',speed:'Vitesse',nexusDamage:'Dégâts nexus',moneyReward:'Récompense or',
  price:'Prix',spawnIntervalTicks:'Intervalle spawn (ticks)',
  power:'Puissance',range:'Portée',fireRateInTicks:'Cadence (ticks)',incomeAmount:'Revenu',incomeIntervalTicks:'Intervalle revenu',
  hpBaseCost:'Coût base HP',speedBaseCost:'Coût base Vitesse',damageBaseCost:'Coût base Dégâts',
  spawnerExtraCostPerUpgrade:'Coût extra upgrade spawner',hpMultiplierPerLevel:'Multiplicateur HP/niveau',speedMultiplierPerLevel:'Multiplicateur Vitesse/niveau',
  towerUpgradeBaseCost:'Coût base upgrade tour',towerPowerMultiplierPerLevel:'Multiplicateur dégâts/niveau tour',towerFireRateMultiplierPerLevel:'Multiplicateur cadence/niveau tour',towerEffectDurationMultiplierPerLevel:'Multiplicateur durée effet/niveau tour',
  fireballLifetime:'Durée Fireball',fireballExplosionRadius:'Rayon explosion Fireball',freezeDurationTicks:'Durée Freeze',
  healNexusAmount:'Soin Nexus',lightningBoxSize:'Taille zone Lightning',lightningDamage:'Dégâts Lightning',shieldDurationTicks:'Durée Shield',
  fireTicks:'Durée feu',slowDurationTicks:'Durée Slow',slowAmplifier:'Amplificateur Slow',
  poisonDurationTicks:'Durée Poison',poisonAmplifier:'Amplificateur Poison',
  chainLightningBoxSize:'Taille zone Chain Lightning',chainLightningMaxTargets:'Cibles max Chain Lightning',
  aoeBoxSize:'Taille zone AOE',aoeDamageRadius:'Rayon dégâts AOE',
  spawnSpread:'Spread spawn',endermanTeleportRange:'Portée téléport Enderman',  witchHealBoxSize:'Taille heal Witch',
  witchHealDurationTicks:'Durée heal Witch',
  witchHealIntervalTicks:'Intervalle heal Witch (ticks)',
  witchHealAmount:'HP par heal Witch',followRange:'Portée follow',specialMobTickInterval:'Intervalle mobs spéciaux',
  waveEventIntervalTicks:'Intervalle événements vague',bonusMoney:'Bonus or',doubleIncomeMultiplier:'Multiplicateur double revenu',
  speedBoostDurationTicks:'Durée speed boost',structureDestroyedBounty:'Prime structure détruite'
};
function label(path){const k=path.split('.').pop();return LABELS[k]||k.replace(/([A-Z])/g,' $1').replace(/^./,s=>s.toUpperCase());}
function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');}
function renderForm(data,path=''){
  if(!data||typeof data!=='object')return '';
  let html='';
  for(const [k,v] of Object.entries(data)){
    const p=path?path+'.'+k:k;
    if(v!==null&&typeof v==='object'&&!Array.isArray(v)){
      const firstVal=Object.values(v)[0];
      const isFlat=firstVal===undefined||typeof firstVal==='number'||typeof firstVal==='string';
      if(isFlat){
        html+='<div class="subsection"><h3>'+esc(label(p))+'</h3><div class="form-grid">';
        for(const [k2,v2] of Object.entries(v)){
          const p2=p+'.'+k2;
          const isDouble=typeof v2==='number'&&v2%1!==0;
          const step=isDouble?0.1:1;
          html+='<div class="field"><label>'+esc(label(p2))+'</label><div class="field-row">';
          html+='<input type="number" data-path="'+esc(p2)+'" value="'+esc(v2)+'" step="'+step+'" min="0">';
          if(typeof v2==='number')html+='<button type="button" class="btn-sm" data-path="'+esc(p2)+'" data-step="'+step+'" onclick="adj(this,-1)">-</button><button type="button" class="btn-sm" data-path="'+esc(p2)+'" data-step="'+step+'" onclick="adj(this,1)">+</button>';
          html+='</div></div>';
        }
        html+='</div></div>';
      }else{
        html+='<h2>'+esc(label(p))+'</h2><div class="form-grid">';
        html+=renderForm(v,p);
        html+='</div>';
      }
    }else if(typeof v==='number'){
      const isDouble=v%1!==0;
      const step=isDouble?0.1:1;
      html+='<div class="field"><label>'+esc(label(p))+'</label><div class="field-row">';
      html+='<input type="number" data-path="'+esc(p)+'" value="'+v+'" step="'+step+'" min="0">';
      html+='<button type="button" class="btn-sm" data-path="'+esc(p)+'" data-step="'+step+'" onclick="adj(this,-1)">-</button><button type="button" class="btn-sm" data-path="'+esc(p)+'" data-step="'+step+'" onclick="adj(this,1)">+</button>';
      html+='</div></div>';
    }else if(typeof v==='string'){
      html+='<div class="field"><label>'+esc(label(p))+'</label><input type="text" data-path="'+esc(p)+'" value="'+esc(v)+'"></div>';
    }
  }
  return html;
}
function collectForm(){
  const out=JSON.parse(JSON.stringify(configData));
  document.querySelectorAll('input[data-path]').forEach(inp=>{
    const path=inp.getAttribute('data-path');
    const val=inp.value;
    const parts=path.split('.');
    let o=out;
    for(let i=0;i<parts.length-1;i++){
      const k=parts[i];
      if(!(k in o)||typeof o[k]!=='object')o[k]={};
      o=o[k];
    }
    const last=parts[parts.length-1];
    const origVal=parts.reduce((a,b)=>a?.[b],configData);
    o[last]=typeof origVal==='number'?(val.includes('.')?parseFloat(val):parseInt(val,10)):val;
  });
  return out;
}
async function load(){
  const formEl=document.getElementById('form');
  try{
    const r=await fetch('/api/config');
    if(!r.ok)throw new Error('HTTP '+r.status);
    configData=await r.json();
    const rendered=renderForm(configData);
    formEl.innerHTML=rendered||'<p class="empty-msg">Aucune donnée à afficher. Vérifiez que le serveur est démarré.</p>';
    status('Config chargée','ok');
  }catch(e){
    formEl.innerHTML='<p class="empty-msg">Erreur de chargement: '+esc(e.message)+'. Cliquez Recharger.</p>';
    status('Erreur: '+e.message,'err');
  }
}
async function save(){
  try{
    const data=collectForm();
    const r=await fetch('/api/config',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
    const res=await r.json();
    if(res.ok){configData=data;status('Enregistré ! Les changements sont appliqués.','ok');}
    else status('Erreur: '+(res.error||'Inconnu'),'err');
  }catch(e){status('Erreur: '+e,'err')}
}
function adj(btn,delta){
  const step=parseFloat(btn.getAttribute('data-step'))||1;
  const inp=btn.closest('.field-row')?.querySelector('input');
  if(!inp)return;
  let v=parseFloat(inp.value)||0;
  v=Math.max(0,Math.round((v+delta*step)/step)*step);
  inp.value=v;
}
function status(msg,cls){
  const s=document.getElementById('status');
  s.textContent=msg;
  s.className=cls||'';
}
document.addEventListener('keydown',(e)=>{
  if((e.ctrlKey||e.metaKey)&&e.key==='s'){e.preventDefault();save();}
});
load();
</script>
</body>
</html>
""";
    }
}
