package com.auction;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final Object LOCK = new Object();
    private static final List<Team> teams = new ArrayList<>();
    private static final List<Player> players = new ArrayList<>();
    private static final List<String> events = new ArrayList<>();
    private static int currentIndex = 0, soldCount = 0, unsoldCount = 0;

    static class Team {
        String id, name, shortName, color;
        long purse, spent;
        Team(String id, String name, String shortName, String color, long purse) {
            this.id=id; this.name=name; this.shortName=shortName; this.color=color; this.purse=purse;
        }
        long remaining(){ return purse-spent; }
    }
    static class Player {
        int id; String name, country, role, style, status="AVAILABLE", teamId="";
        long basePrice, currentBid=0; String highestBidder="";
        Player(int id,String name,String country,String role,String style,long basePrice){
            this.id=id;this.name=name;this.country=country;this.role=role;this.style=style;this.basePrice=basePrice;
        }
    }

    public static void main(String[] args) throws Exception {
        resetData();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Rohit Sharma Cricket Auction is running at http://localhost:" + PORT);
        System.out.println("Keep this terminal open. Press Ctrl+C to stop.");
    }

    private static void resetData() {
        synchronized (LOCK) {
            teams.clear(); players.clear(); events.clear(); currentIndex=0; soldCount=0; unsoldCount=0;
            teams.add(new Team("titans","Chandigarh Titans","CT","#50e3a4",10000));
            teams.add(new Team("royals","Jaipur Royals","JR","#f7bd55",10000));
            teams.add(new Team("strikers","Mumbai Strikers","MS","#78a9ff",10000));
            teams.add(new Team("falcons","Delhi Falcons","DF","#c5a0ff",10000));
            players.add(new Player(1,"Rohit Sharma","India","Batter","Right-hand bat · Opening batter",200));
            players.add(new Player(2,"Virat Kohli","India","Batter","Right-hand bat · Top order",200));
            players.add(new Player(3,"Jasprit Bumrah","India","Fast Bowler","Right-arm fast",175));
            players.add(new Player(4,"Hardik Pandya","India","All-rounder","Right-hand bat · Right-arm medium",175));
            players.add(new Player(5,"Rishabh Pant","India","Wicketkeeper","Left-hand bat · Wicketkeeper",150));
            players.add(new Player(6,"Suryakumar Yadav","India","Batter","Right-hand bat · Middle order",150));
            players.add(new Player(7,"Ravindra Jadeja","India","All-rounder","Left-hand bat · Left-arm spin",150));
            players.add(new Player(8,"Shubman Gill","India","Batter","Right-hand bat · Opening batter",150));
            players.add(new Player(9,"Pat Cummins","Australia","Fast Bowler","Right-arm fast",175));
            players.add(new Player(10,"Travis Head","Australia","Batter","Left-hand bat · Top order",150));
            players.add(new Player(11,"Kane Williamson","New Zealand","Batter","Right-hand bat · Top order",125));
            players.add(new Player(12,"Rashid Khan","Afghanistan","Spinner","Right-arm leg spin",125));
            addEvent("Auction room is live. Rohit Sharma is the first player on the block.");
            addEvent("Four demo franchises have ₹100 crore each to build their squads.");
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.equals("/index.html")) {
                if (!"GET".equals(ex.getRequestMethod())) { send(ex,405,"Method not allowed","text/plain; charset=utf-8"); return; }
                send(ex,200,HTML,"text/html; charset=utf-8"); return;
            }
            if (path.equals("/favicon.ico")) { send(ex,204,"","text/plain"); return; }
            if (path.equals("/api/state") && "GET".equals(ex.getRequestMethod())) {
                synchronized(LOCK) { send(ex,200,toJson(),"application/json; charset=utf-8"); } return;
            }
            if (path.equals("/api/health") && "GET".equals(ex.getRequestMethod())) {
                send(ex,200,"{\"ok\":true,\"service\":\"Rohit Sharma Cricket Auction\"}","application/json; charset=utf-8"); return;
            }
            if (!path.startsWith("/api/")) { send(ex,404,"Not found","text/plain; charset=utf-8"); return; }
            if (!"POST".equals(ex.getRequestMethod())) { send(ex,405,"Use POST for auction actions","text/plain; charset=utf-8"); return; }
            Map<String,String> p=parseParams(ex);
            synchronized(LOCK) {
                switch(path) {
                    case "/api/bid" -> doBid(ex,p);
                    case "/api/select" -> doSelect(ex,p);
                    case "/api/sell" -> doSell(ex);
                    case "/api/unsold" -> doUnsold(ex);
                    case "/api/next" -> { advance(); reply(ex,true,"Moved to the next available player."); }
                    case "/api/reset" -> { resetData(); reply(ex,true,"Auction reset. Rohit Sharma is back on the block."); }
                    default -> send(ex,404,"Unknown API endpoint","text/plain; charset=utf-8");
                }
            }
        } catch (Exception e) {
            send(ex,500,"{\"ok\":false,\"message\":\"Server error: "+escape(e.getMessage()==null?"unknown":e.getMessage())+"\"}","application/json; charset=utf-8");
        }
    }

    private static void doSelect(HttpExchange ex, Map<String,String> p) throws IOException {
        int id;
        try { id=Integer.parseInt(p.getOrDefault("playerId","0")); }
        catch(Exception e){reply(ex,false,"Invalid player selection.");return;}
        for(int i=0;i<players.size();i++){
            if(players.get(i).id==id){
                if(!"AVAILABLE".equals(players.get(i).status)){reply(ex,false,"This player has already been processed.");return;}
                currentIndex=i; reply(ex,true,players.get(i).name+" selected for auction."); return;
            }
        }
        reply(ex,false,"Player not found.");
    }

    private static void doBid(HttpExchange ex, Map<String,String> p) throws IOException {
        Player pl=current();
        if (!"AVAILABLE".equals(pl.status)) { reply(ex,false,"This player has already been processed."); return; }
        Team t=findTeam(p.getOrDefault("teamId",""));
        if(t==null){reply(ex,false,"Please choose a franchise.");return;}
        long amount;
        try { amount=Long.parseLong(p.getOrDefault("amount","0")); }
        catch(Exception e){reply(ex,false,"Enter a valid bid amount.");return;}
        long minimum=pl.currentBid==0?pl.basePrice:pl.currentBid+25;
        if(amount<minimum){reply(ex,false,"Bid must be at least ₹"+minimum+" lakh.");return;}
        if(amount>t.remaining()){reply(ex,false,"Insufficient purse. "+t.name+" has ₹"+t.remaining()+" lakh remaining.");return;}
        pl.currentBid=amount;pl.highestBidder=t.id;
        addEvent(t.name+" bid ₹"+amount+" lakh for "+pl.name+".");
        reply(ex,true,"Bid accepted! "+t.name+" is the highest bidder.");
    }
    private static void doSell(HttpExchange ex) throws IOException {
        Player p=current();
        if(!"AVAILABLE".equals(p.status)){reply(ex,false,"This player has already been processed.");return;}
        if(p.highestBidder.isEmpty()){reply(ex,false,"No bids yet. Place a bid before selling.");return;}
        Team t=findTeam(p.highestBidder);t.spent+=p.currentBid;p.status="SOLD";p.teamId=t.id;soldCount++;
        addEvent(p.name+" SOLD to "+t.name+" for ₹"+p.currentBid+" lakh!");
        advance();reply(ex,true,p.name+" sold successfully to "+t.name+".");
    }
    private static void doUnsold(HttpExchange ex) throws IOException {
        Player p=current();
        if(!"AVAILABLE".equals(p.status)){reply(ex,false,"This player has already been processed.");return;}
        p.status="UNSOLD";unsoldCount++;addEvent(p.name+" went UNSOLD.");advance();
        reply(ex,true,p.name+" marked unsold.");
    }
    private static Player current() { return players.get(currentIndex); }
    private static void advance() {
        for(int i=0;i<players.size();i++){
            currentIndex=(currentIndex+1)%players.size();
            if("AVAILABLE".equals(players.get(currentIndex).status)) return;
        }
        currentIndex=0;
    }
    private static Team findTeam(String id){for(Team t:teams)if(t.id.equals(id))return t;return null;}
    private static void addEvent(String s){events.add(0,s);if(events.size()>12)events.remove(events.size()-1);}
    private static Map<String,String> parseParams(HttpExchange ex)throws IOException{
        String body=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
        Map<String,String> m=new HashMap<>();
        for(String pair:body.split("&")){if(pair.isEmpty())continue;String[] a=pair.split("=",2);
            String k=URLDecoder.decode(a[0],StandardCharsets.UTF_8);
            String v=a.length>1?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"";
            m.put(k,v);}
        return m;
    }
    private static void reply(HttpExchange ex,boolean ok,String message)throws IOException{
        send(ex,200,"{\"ok\":"+ok+",\"message\":\""+escape(message)+"\"}","application/json; charset=utf-8");
    }
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    private static void send(HttpExchange ex,int status,String body,String type)throws IOException{
        byte[] b=body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",type);
        ex.getResponseHeaders().set("Cache-Control","no-store");
        ex.sendResponseHeaders(status,b.length);
        try(OutputStream out=ex.getResponseBody()){out.write(b);}
    }
    private static String toJson(){
        StringBuilder b=new StringBuilder("{\"currentIndex\":").append(currentIndex).append(",\"soldCount\":").append(soldCount).append(",\"unsoldCount\":").append(unsoldCount).append(",\"players\":[");
        for(int i=0;i<players.size();i++){Player p=players.get(i);if(i>0)b.append(',');
            b.append("{\"id\":").append(p.id).append(",\"name\":\"").append(escape(p.name)).append("\",\"country\":\"").append(escape(p.country)).append("\",\"role\":\"").append(escape(p.role)).append("\",\"style\":\"").append(escape(p.style)).append("\",\"basePrice\":").append(p.basePrice).append(",\"currentBid\":").append(p.currentBid).append(",\"highestBidder\":\"").append(escape(p.highestBidder)).append("\",\"teamId\":\"").append(escape(p.teamId)).append("\",\"status\":\"").append(p.status).append("\"}");}
        b.append("],\"teams\":[");
        for(int i=0;i<teams.size();i++){Team t=teams.get(i);if(i>0)b.append(',');
            b.append("{\"id\":\"").append(t.id).append("\",\"name\":\"").append(escape(t.name)).append("\",\"shortName\":\"").append(t.shortName).append("\",\"color\":\"").append(t.color).append("\",\"purse\":").append(t.purse).append(",\"spent\":").append(t.spent).append(",\"remaining\":").append(t.remaining()).append("}");}
        b.append("],\"events\":[");
        for(int i=0;i<events.size();i++){if(i>0)b.append(',');b.append("\"").append(escape(events.get(i))).append("\"");}
        return b.append("]}").toString();
    }

    private static final String HTML = """
<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Rohit Sharma Cricket Auction | Auction Pro</title>
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
:root{--bg:#080d12;--panel:#111a23;--panel2:#15232d;--line:#293946;--muted:#8fa0ae;--text:#f3f7fa;--green:#50e3a4;--gold:#f7bd55;--red:#ff7483}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(ellipse at 78% -10%,#174c3c 0,transparent 35%),radial-gradient(ellipse at 0% 35%,#142d41 0,transparent 34%),var(--bg);color:var(--text);font-family:'DM Sans',sans-serif;min-height:100vh}button,input,select{font:inherit}button{cursor:pointer;transition:.18s}button:hover{filter:brightness(1.1);transform:translateY(-1px)}.shell{max-width:1400px;margin:auto;padding:22px 28px 40px}.top,.brand,.topright,.hero,.panelhead,.team,.event{display:flex;align-items:center}.top,.hero,.panelhead{justify-content:space-between;gap:16px}.top{margin-bottom:26px}.brand{gap:12px}.logo{height:45px;width:45px;border-radius:14px;display:grid;place-items:center;background:linear-gradient(135deg,var(--green),#188b70);font-size:24px}.brand h1{font:700 21px 'Space Grotesk';margin:0}.brand p{font-size:10px;letter-spacing:2px;color:var(--muted);margin:4px 0 0}.topright{gap:10px}.live,.tag{font-size:10px;letter-spacing:.7px;font-weight:800;border:1px solid #28654d;background:#10281f;color:var(--green);border-radius:20px;padding:9px 12px}.dot{display:inline-block;width:7px;height:7px;background:var(--green);border-radius:50%;margin-right:6px}.reset,.secondary,.pick{background:#1b2933;border:1px solid #344652;color:#d9e4ea;border-radius:9px;padding:10px 13px}.eyebrow{color:var(--green);font-size:11px;letter-spacing:2px;font-weight:800}.hero{align-items:flex-end;margin:20px 0}.hero h2{font:700 clamp(28px,4vw,43px) 'Space Grotesk';letter-spacing:-1.5px;margin:8px 0}.hero h2 span{color:var(--green)}.hero p,.sub{font-size:12px;color:var(--muted);margin:0}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:20px 0}.stat,.panel{background:linear-gradient(145deg,#121d26f5,#0e151df5);border:1px solid var(--line);border-radius:16px;overflow:hidden}.stat{padding:16px 18px}.label,.bidlabel{font-size:10px;color:var(--muted);letter-spacing:1px;text-transform:uppercase}.value{font:700 25px 'Space Grotesk';margin-top:9px}.green{color:var(--green)}.layout{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(300px,.8fr);gap:16px}.panelhead{padding:17px 19px;border-bottom:1px solid var(--line)}.panelhead h3{font:600 15px 'Space Grotesk';margin:0 0 4px}.tag{border-radius:5px;padding:6px 8px;font-size:9px}.playerarea{padding:18px}.playerhero{display:flex;justify-content:space-between;align-items:center;gap:15px;padding:24px;border:1px solid #2a414b;border-radius:15px;min-height:195px;background:radial-gradient(ellipse at 80% 45%,#1c5b48,transparent 46%),linear-gradient(120deg,#192832,#101820)}.role{display:inline-block;padding:6px 9px;border:1px solid #2c7758;background:#112c22;color:var(--green);border-radius:5px;font-size:10px;font-weight:800}.playername{font:700 clamp(27px,3vw,38px) 'Space Grotesk';margin:12px 0 5px}.meta,.style{font-size:12px;color:#aebdc7;margin-top:8px}.art{width:100px;height:125px;display:grid;place-items:center;flex-shrink:0;border-radius:60px 60px 20px 20px;background:linear-gradient(145deg,#5beab0,#16765e);font-size:53px;box-shadow:0 0 35px #50e3a426}.bidrow{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:12px 0}.bidbox{padding:15px;border:1px solid var(--line);border-radius:12px;background:#0c141b}.price{font:700 25px 'Space Grotesk';margin:7px 0 4px}.hint{font-size:11px;color:var(--muted)}.controls{display:grid;grid-template-columns:1fr 1fr;gap:10px}.field label{display:block;color:var(--muted);font-size:11px;margin-bottom:7px}.field select,.field input,.filters input,.filters select{width:100%;padding:12px;background:#0a1218;color:var(--text);border:1px solid #31434f;border-radius:9px;outline:none}.field select:focus,.field input:focus{border-color:var(--green)}.primary{width:100%;margin-top:11px;padding:13px;background:var(--green);color:#062219;border:0;border-radius:10px;font-weight:800}.actionrow{display:grid;grid-template-columns:1fr 1fr;gap:9px}.secondary{margin-top:9px;font-size:12px;font-weight:700}.danger{background:#2b191f;border-color:#66323d;color:#ff9ba7}.notice{display:none;margin-top:10px;padding:11px;border-radius:8px;font-size:12px}.notice.show{display:block}.notice.ok{color:#75efb8;background:#102a20;border:1px solid #285d43}.notice.bad{color:#ff9ba7;background:#2b191f;border:1px solid #63333d}.side{display:flex;flex-direction:column;gap:15px}.teamlist,.feed,.summary,.playerlist{padding:8px 17px 14px}.team{gap:10px;padding:12px 0;border-bottom:1px solid #202e38}.team:last-child,.event:last-child,.playerrow:last-child{border-bottom:0}.teammark,.avatar{width:35px;height:35px;border-radius:10px;display:grid;place-items:center;font-size:10px;font-weight:800;color:#07131a;flex-shrink:0}.teammeta{flex:1;min-width:0}.teamname,.pname{font-size:12px;font-weight:700}.teamspent,.pdetail{font-size:10px;color:var(--muted);margin-top:4px}.amount{text-align:right;font:600 12px 'Space Grotesk'}.bar{height:4px;background:#263540;border-radius:5px;margin-top:7px;overflow:hidden}.bar span{height:100%;display:block}.event{gap:9px;padding:10px 0;border-bottom:1px solid #202e38;font-size:11px;color:#c1cdd5;line-height:1.5}.eventdot{width:7px;height:7px;border-radius:50%;background:var(--green);margin-top:4px;flex-shrink:0}.bottom{display:grid;grid-template-columns:1.25fr .75fr;gap:16px;margin-top:16px}.filters{display:flex;gap:7px}.filters input,.filters select{padding:8px;font-size:11px;min-width:115px}.playerlist{max-height:320px;overflow:auto}.playerrow{display:grid;grid-template-columns:35px 1fr auto auto;gap:10px;align-items:center;padding:11px 0;border-bottom:1px solid #202e38}.avatar{background:#1c3731;color:var(--green)}.status{font-size:9px;font-weight:800;padding:5px 7px;border-radius:5px}.AVAILABLE{background:#123126;color:var(--green)}.SOLD{background:#172944;color:#9fc2ff}.UNSOLD{background:#38221f;color:#ffb5a6}.pick{padding:7px 9px;font-size:10px}.summaryline{display:flex;justify-content:space-between;padding:12px 0;border-bottom:1px solid #202e38;font-size:12px}.summaryline span{color:var(--muted)}.footer{text-align:center;color:#647887;font-size:10px;margin-top:24px;letter-spacing:.5px}
@media(max-width:850px){.layout,.bottom{grid-template-columns:1fr}.shell{padding:16px 12px}.stats{grid-template-columns:repeat(2,1fr)}.hero{align-items:flex-start;flex-direction:column}.top{align-items:flex-start}.topright{flex-wrap:wrap;justify-content:flex-end}.playerrow{grid-template-columns:35px 1fr auto}.playerrow .status{display:none}}
@media(max-width:480px){.brand h1{font-size:17px}.brand p{font-size:8px}.top{gap:8px}.topright{gap:5px}.live,.reset{font-size:9px;padding:8px}.stats{gap:7px}.stat{padding:12px}.value{font-size:21px}.playerarea{padding:12px}.playerhero{padding:17px}.art{width:72px;height:96px;font-size:38px}.controls{grid-template-columns:1fr}.panelhead{padding:14px}.bidrow{gap:7px}.price{font-size:21px}.filters{flex-wrap:wrap}.filters input,.filters select{min-width:0}}
</style></head><body><main class="shell">
<header class="top"><div class="brand"><div class="logo">🏏</div><div><h1>Rohit Sharma <span style="color:var(--green)">Auction Pro</span></h1><p>CRICKET FRANCHISE AUCTION SYSTEM</p></div></div><div class="topright"><div class="live"><span class="dot"></span>AUCTION ROOM LIVE</div><button class="reset" onclick="resetAuction()">↻ Reset Demo</button></div></header>
<section class="hero"><div><div class="eyebrow">SEASON 2026 · PLAYER DRAFT</div><h2>Build your <span>dream squad.</span></h2><p>Bid smart. Manage your purse. Win the auction.</p></div><span class="tag">● SIMULATION MODE</span></section>
<section class="stats"><div class="stat"><div class="label">Players in pool</div><div class="value" id="totalPlayers">—</div></div><div class="stat"><div class="label">Players sold</div><div class="value green" id="soldPlayers">—</div></div><div class="stat"><div class="label">Total auction spend</div><div class="value" id="totalSpend">—</div></div><div class="stat"><div class="label">Players unsold</div><div class="value" id="unsoldPlayers">—</div></div></section>
<div class="layout"><section class="panel"><div class="panelhead"><div><h3>Live Auction Floor</h3><div class="sub">Current player on the block</div></div><span class="tag" id="counter">PLAYER 01 / 12</span></div><div class="playerarea"><div class="playerhero"><div><span class="role" id="role">BATTER</span><div class="playername" id="name">Rohit Sharma</div><div class="meta" id="country">India · Player #001</div><div class="style" id="style">Right-hand bat · Opening batter</div></div><div class="art">🏏</div></div><div class="bidrow"><div class="bidbox"><div class="bidlabel">Base price</div><div class="price" id="base">₹200 L</div><div class="hint">Opening valuation</div></div><div class="bidbox"><div class="bidlabel">Current highest bid</div><div class="price green" id="current">₹0 L</div><div class="hint" id="leader">No bids yet</div></div></div><div class="controls"><div class="field"><label for="teamSelect">Bidding franchise</label><select id="teamSelect"></select></div><div class="field"><label for="amount">Your bid (₹ lakh)</label><input id="amount" type="number" min="1" step="25" value="200"></div></div><button class="primary" onclick="placeBid()">＋ &nbsp; Place Bid</button><div class="actionrow"><button class="secondary" onclick="sellPlayer()">✓ &nbsp; Sell Player</button><button class="secondary danger" onclick="markUnsold()">× &nbsp; Mark Unsold</button></div><div class="notice" id="notice" role="status" aria-live="polite"></div></div></section>
<div class="side"><section class="panel"><div class="panelhead"><div><h3>Franchise Purse</h3><div class="sub">Remaining budget by team</div></div><span class="tag">4 TEAMS</span></div><div class="teamlist" id="teams"></div></section><section class="panel"><div class="panelhead"><div><h3>Auction Activity</h3><div class="sub">Latest room updates</div></div><span class="sub">LIVE FEED</span></div><div class="feed" id="events"></div></section></div></div>
<div class="bottom"><section class="panel"><div class="panelhead" style="flex-wrap:wrap"><div><h3>Player Catalogue</h3><div class="sub">Search and browse the auction pool</div></div><div class="filters"><input id="search" placeholder="Search players..." oninput="renderPlayers()"><select id="roleFilter" onchange="renderPlayers()"><option value="">All roles</option><option>Batter</option><option>All-rounder</option><option>Fast Bowler</option><option>Wicketkeeper</option><option>Spinner</option></select></div></div><div class="playerlist" id="playerList"></div></section><section class="panel"><div class="panelhead"><div><h3>Auction Summary</h3><div class="sub">Room statistics</div></div></div><div class="summary" id="summary"></div></section></div><div class="footer">JAVA HTTP SERVER · IN-MEMORY AUCTION ENGINE · EDUCATIONAL PROJECT</div></main>
<script>
'use strict';
let state=null, requestBusy=false;
const $=id=>document.getElementById(id);
const money=n=>'₹'+Number(n).toLocaleString('en-IN')+' L';
async function api(path,body=null){
 const opts=body===null?{method:'GET',cache:'no-store'}:{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body:new URLSearchParams(body)};
 const response=await fetch(path,opts);
 if(!response.ok) throw new Error('Server returned HTTP '+response.status);
 return response.json();
}
function notice(message,ok){const el=$('notice');el.textContent=message;el.className='notice show '+(ok?'ok':'bad');}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function current(){return state.players[state.currentIndex]||state.players[0];}
function render(){
 if(!state||!state.players.length)return;
 const p=current(), team=state.teams.find(t=>t.id===p.highestBidder);
 $('totalPlayers').textContent=state.players.length;$('soldPlayers').textContent=state.soldCount;$('unsoldPlayers').textContent=state.unsoldCount;
 $('totalSpend').textContent=money(state.teams.reduce((sum,t)=>sum+t.spent,0));
 $('counter').textContent='PLAYER '+String(p.id).padStart(2,'0')+' / '+state.players.length;
 $('name').textContent=p.name;$('role').textContent=p.role.toUpperCase();$('country').textContent=p.country+' · Player #'+String(p.id).padStart(3,'0');$('style').textContent=p.style;
 $('base').textContent=money(p.basePrice);$('current').textContent=money(p.currentBid);$('leader').textContent=team?'Leading: '+team.name:'No bids yet';
 $('amount').min=p.currentBid?p.currentBid+25:p.basePrice;$('amount').value=p.currentBid?p.currentBid+25:p.basePrice;
 $('teamSelect').innerHTML=state.teams.map(t=>'<option value="'+esc(t.id)+'">'+esc(t.name)+' · '+money(t.remaining)+' left</option>').join('');
 $('teams').innerHTML=state.teams.map(t=>'<div class="team"><div class="teammark" style="background:'+esc(t.color)+'">'+esc(t.shortName)+'</div><div class="teammeta"><div class="teamname">'+esc(t.name)+'</div><div class="teamspent">Spent '+money(t.spent)+' of '+money(t.purse)+'</div><div class="bar"><span style="width:'+Math.min(100,t.spent/t.purse*100)+'%;background:'+esc(t.color)+'"></span></div></div><div class="amount">'+money(t.remaining)+'<div style="font:10px DM Sans;color:var(--muted);margin-top:4px">left</div></div></div>').join('');
 $('events').innerHTML=state.events.map(e=>'<div class="event"><span class="eventdot"></span><span>'+esc(e)+'</span></div>').join('');
 renderPlayers();
 const highest=state.players.filter(x=>x.status==='SOLD').sort((a,b)=>b.currentBid-a.currentBid)[0];
 $('summary').innerHTML='<div class="summaryline"><span>Players in pool</span><strong>'+state.players.length+'</strong></div><div class="summaryline"><span>Players sold</span><strong class="green">'+state.soldCount+'</strong></div><div class="summaryline"><span>Players unsold</span><strong>'+state.unsoldCount+'</strong></div><div class="summaryline"><span>Awaiting auction</span><strong>'+state.players.filter(x=>x.status==='AVAILABLE').length+'</strong></div><div class="summaryline"><span>Highest sale</span><strong>'+(highest?money(highest.currentBid):'—')+'</strong></div><div class="summaryline"><span>Franchises</span><strong>'+state.teams.length+'</strong></div>';
}
function renderPlayers(){
 if(!state)return;const q=$('search').value.toLowerCase(),role=$('roleFilter').value;
 const list=state.players.filter(p=>(p.name.toLowerCase().includes(q)||p.country.toLowerCase().includes(q))&&(!role||p.role===role));
 $('playerList').innerHTML=list.map(p=>'<div class="playerrow"><div class="avatar">'+esc(p.name.split(' ').map(x=>x[0]).join('').slice(0,2))+'</div><div><div class="pname">'+esc(p.name)+'</div><div class="pdetail">'+esc(p.role)+' · Base '+money(p.basePrice)+'</div></div><span class="status '+p.status+'">'+p.status+'</span><button class="pick" onclick="selectPlayer('+p.id+')">'+(p.id===current().id?'ON BLOCK':'VIEW')+'</button></div>').join('')||'<div class="hint">No matching players.</div>';
}
async function refresh(showError=true){try{state=await api('/api/state');render();return true;}catch(e){if(showError)notice('Could not load auction data: '+e.message+'. Make sure this page is opened at http://localhost:8080.',false);return false;}}
async function action(path,body=null){if(requestBusy)return;requestBusy=true;try{const result=await api(path,body);await refresh(false);notice(result.message,result.ok);return result;}catch(e){notice('Request failed: '+e.message+'. Check the Java terminal.',false);}finally{requestBusy=false;}}
function placeBid(){action('/api/bid',{teamId:$('teamSelect').value,amount:$('amount').value});}
function sellPlayer(){action('/api/sell');}
function markUnsold(){action('/api/unsold');}
async function resetAuction(){if(!confirm('Reset all bids and team purses for a fresh demo?'))return;await action('/api/reset');}
async function selectPlayer(id){const p=state.players.find(x=>x.id===id);if(!p)return;if(p.status!=='AVAILABLE'){notice('This player has already been processed.','bad');return;}await action('/api/select',{playerId:id});window.scrollTo({top:0,behavior:'smooth'});}
refresh();
setInterval(()=>{if(!requestBusy)refresh(false)},5000);
</script></body></html>
""";
}
