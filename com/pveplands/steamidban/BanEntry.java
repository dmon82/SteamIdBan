package com.pveplands.steamidban;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import javax.json.JsonString;

public class BanEntry {
    private String steamId;
    private List<String> names = new ArrayList<>();
    private Date issued;
    private Date expiry;
    private String reason;
    private String issuer;

    public BanEntry(String issuer, String steamId, Date issued, Date expiry, String reason, String... names) {
        this.issuer = issuer;
        this.steamId = steamId;
        this.names.addAll(Arrays.asList(names));
        this.issued = issued;
        this.expiry = expiry;
        this.reason = reason;
    }

    public BanEntry(String issuer, String steamId, Date issued, Date expiry, String reason, List<JsonString> names) {
        this.issuer = issuer;
        this.steamId = steamId;
        if (names != null) for (JsonString value : names) this.names.add(value.getString());
        this.issued = issued;
        this.expiry = expiry;
        this.reason = reason;
    }
    
    public boolean containsName(String name) {
        for (String value : names)
            if (value.equalsIgnoreCase(name))
                return true;
        
        return false;
    }
    
    public boolean addName(String name) {
        if (names.contains(name))
            return false;
        
        return names.add(name);
    }
    
    public boolean removeName(String name) {
        return names.remove(name);
    }
    
    /**
     * Sets the ban expiration to NOW, disabling the ban instead of deleting it.
     */
    public void disable() {
        this.expiry = new Date();
    }
    
    public boolean isExpired() {
        return expiry.before(new Date());
    }
    
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public List<String> getNames() {
        return names;
    }

    public Date getIssued() {
        return issued;
    }

    public void setIssued(Date issued) {
        this.issued = issued;
    }

    public Date getExpiry() {
        return expiry;
    }

    public void setExpiry(Date expiry) {
        this.expiry = expiry;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String ToString() {
        return String.format("ID: %s, Issuer: %s, Issued: %s, Reason: %s, Expires: %s",
            getSteamId(), getIssuer(), SteamIdBan.dateFormat.format(getIssued()),
            getReason(), SteamIdBan.dateFormat.format(getExpiry()));
    }
}
