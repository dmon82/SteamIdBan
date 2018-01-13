package com.pveplands.steamidban;

import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.Question;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Properties;

public class CreateBanQuestion extends Question {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    Player responder;
    HashMap<String, BanEntry> bans;
    String steamId;
    String reason;
    
    public CreateBanQuestion(Player responder, HashMap bans, String steamId, String reason) {
        super(responder, "Create or edit ban", "Create or edit ban", 502, 0);
        
        this.bans = bans;
        this.steamId = steamId;
        this.reason = reason;
        this.responder = responder;
    }
    
    public CreateBanQuestion(Player player, HashMap bans, String steamId) {
        this(player, bans, steamId, "unspecified");
    }
    
    @Override
    public void answer(Properties props) {
        props.entrySet().stream().forEach(x -> logger.info(String.format("%s: %s", x.getKey(), x.getValue())));

        String steamid = props.getProperty("steamid", "");
        
        if (steamid.length() == 0) {
            getResponder().getCommunicator().sendAlertServerMessage("Steam ID field can not be empty.");
            return;
        }
        else if (!steamid.equals(this.steamId)) {
            responder.getCommunicator().sendAlertServerMessage("Steam ID of a ban can not be changed, please create a new ban for that.");
            return;
        }

        BanEntry ban = bans.getOrDefault(steamid, null);
        
        if (props.containsKey("ok")) {
            reason = props.getProperty("reason", reason);
            String expires = props.getProperty("expires", SteamIdBan.dateFormat.format(new Date()));
            if (ban == null) bans.put(steamid, ban = new BanEntry(getResponder().getName(), steamid, new Date(), TryParseDate(expires), reason));
            else {
                ban.setReason(reason);
                ban.setExpiry(TryParseDate(expires));
                ban.setIssuer(getResponder().getName());
                ban.setIssued(new Date());
            }
            
            responder.getCommunicator().sendAlertServerMessage("Ban has been edited/refreshed.");
        }
        else responder.getCommunicator().sendAlertServerMessage("No changed have been made to the ban.");
    }

    @Override
    public void sendQuestion() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getBmlHeader());
        
        BanEntry ban = bans.getOrDefault(steamId, null);
        
        sb.append("table{rows='6';cols='2';");
        
        sb.append("label{text='Steam ID:'}input{id='steamid';maxchars='24';text='").append(steamId).append("'}");
        sb.append("label{text='Reason:'}input{id='reason';maxchar='256';text='").append(ban == null ? reason : ban.getReason()).append("'}");
        sb.append("label{text='Issuer:'}label{text='").append(ban == null ? this.getResponder().getName() : ban.getIssuer()).append("'}");
        sb.append("label{text='Issued:'}label{text='").append(SteamIdBan.dateFormat.format(ban == null ? new Date() : ban.getIssued())).append("'}");
        sb.append("label{text='Expires:'}input{id='expires';maxchars='25';text='").append(SteamIdBan.dateFormat.format(ban == null ? new Date() : ban.getExpiry())).append("'}");
        sb.append("label{text='Characters:'}dropdown{id='characters';options='");
        ban.getNames().stream().forEach(x -> sb.append(x).append(","));
        if (!ban.getNames().isEmpty()) sb.delete(sb.lastIndexOf(","), sb.length());
        sb.append("'}");
        
        sb.append("}");

        sb.append("harray{button{id='ok';text='").append(ban == null ? "Ban" : "Edit").append("'}null;null;button{id='cancel';text='Cancel'}}");
        sb.append("}}null;null;}");
        
        this.getResponder().getCommunicator().sendBml(230, 180, true, true, sb.toString(), 255, 255, 255, "Create or edit ban");
    }
    
    private Date TryParseDate(String value) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        if (!df.isLenient()) df.setLenient(true);
        
        try { return df.parse(value); }
        catch (Exception ex) { }
        
        df.applyPattern("yyyy-MM-dd");
        
        try { return df.parse(value); }
        catch (Exception ex) { }
        
        return new GregorianCalendar(6969, 12, 31).getTime();
    }
}
