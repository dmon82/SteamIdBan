package com.pveplands.steamidban;

import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.Question;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;

public class BanQuestion extends Question {
    private Player responder;
    private HashMap<String, BanEntry> bans;
    private int page;
    private int perPage = 20;
    private DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    
    public BanQuestion(Player owner, HashMap bans, int page) {
        super(owner, "Steam ID bans", "Manage bans", 501, 0);
        this.bans = bans;
        this.page = page;
        this.responder = owner;
    }

    public BanQuestion(Player owner, HashMap bans) {
        this(owner, bans, 0);
    }
    
    @Override
    public void answer(Properties props) {
        if (props.containsKey("pageback"))
            new BanQuestion(responder, bans, page - 1).sendQuestion();
        
        if (props.containsKey("nextpage"))
            new BanQuestion(responder, bans, page + 1).sendQuestion();
        
        if (props.containsKey("selectedunban")) {
            props.entrySet().stream().filter(x -> x.getKey().toString().startsWith("unban") && x.getValue().equals("true")).forEach(x -> { 
                String steamId = x.getKey().toString().substring(5);
                responder.getCommunicator().sendAlertServerMessage(String.format("%s has been unbanned.", steamId));
                bans.get(steamId).disable();
                responder.getLogger().info(String.format("Unbanned Steam ID %s.", steamId));
            });
        }
        
        if (props.containsKey("selectededit")) {
            Optional<Entry<Object, Object>> first = 
                props.entrySet().stream().filter(x -> x.getKey().toString().startsWith("unban") && x.getValue().equals("true")).findFirst();
            
            if (!first.isPresent()) {
                responder.getCommunicator().sendAlertServerMessage("No ban selected to edit.");
                return;
            }
            
            BanEntry ban = bans.get(((String)first.get().getKey()).substring(5));
            
            new CreateBanQuestion(responder, bans, ban.getSteamId()).sendQuestion();
        }
    }

    @Override
    public void sendQuestion() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getBmlHeader());
        
        BanEntry[] entries = bans.values().toArray(new BanEntry[0]);
        
        int rows = 9;
        
        if (entries.length < page * perPage + 9)
            rows = page * perPage + 8 - entries.length;
        
        sb.append("table{rows='").append(++rows).append("';cols='8';"); // +1 row for header
        sb.append("label{text='#'}label{text='Steam ID'}label{text=''}label{text='Issuer'}label{text='Issued'}label{text='Reason'}label{text='Expiry'}label{text='Characters'}");
        
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            
            if (index >= entries.length)
                break;
            
            sb.append("label{text='").append(index).append("'}");
            sb.append("label{text='").append(entries[index].getSteamId()).append("'}");
            sb.append("checkbox{text='';id='unban").append(entries[index].getSteamId()).append("'};");
            sb.append("label{text='").append(entries[index].getIssuer()).append("'}");
            sb.append("label{text='").append(df.format(entries[index].getIssued())).append("'}");
            sb.append("label{text='").append(entries[index].getReason()).append("'}");
            sb.append("label{").append(entries[index].isExpired() ? "color='0,192,0';" : "").append("text='").append(df.format(entries[index].getExpiry())).append("'}");
            sb.append("dropdown{id='characters';options='");
            
            entries[index].getNames().stream().forEach(x -> sb.append(x).append(","));
            if (!entries[index].getNames().isEmpty()) sb.delete(sb.lastIndexOf(","), sb.length());
            
            sb.append("'}");
        }
        
        sb.append("}");
        
        
        boolean previousPages = page > 0;
        boolean morePages = entries.length / perPage > page;
        
        sb.append("harray{");
        sb.append("button{text='Unban selected';id='selectedunban'}");
        sb.append("null;null;button{text='Edit selected';id='selectededit'}");
        
        if (previousPages) sb.append("null;null;button{text='Prev page';id='pageback'}");
        if (morePages) sb.append("null;null;button{text='Next page';id='nextpage'}");

        sb.append("}");
        
        sb.append("}}null;null;}");
        
        responder.getCommunicator().sendBml(670, 407, true, true, sb.toString(), 255, 255, 255, "Manage Steam ID bans");
    }
}
