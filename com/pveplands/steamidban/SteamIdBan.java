package com.pveplands.steamidban;

import com.wurmonline.server.LoginHandler;
import com.wurmonline.server.Players;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.players.PlayerInfo;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javax.json.*;
import javax.json.stream.JsonGenerator;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PlayerMessageListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

public class SteamIdBan implements WurmServerMod, Configurable, PreInitable, Initable, PlayerMessageListener {
    private static final Logger logger = Logger.getLogger(getLoggerName(SteamIdBan.class));
    private static final Logger idLogger = Logger.getLogger("SteamID64");
    
    private Path banfile = Paths.get("steamidbans.json");
    private HashMap<String, BanEntry> bans = new HashMap();
    private boolean logSteamIds = false;
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    
    private Properties properties;
    private int steamBanPower = 5;
    private int steamUnbanPower = 5;
    private int logLimit = 1048576; // 1 MB
    private int logCount = 8;
    
    public SteamIdBan() {
    }
    
    @Override
    public void configure(Properties properties) {
        this.properties = properties;

        steamBanPower = Integer.valueOf(properties.getProperty("steamBanPower", String.valueOf(steamBanPower)));
        logger.info(String.format("Steam ID ban power: %d", steamBanPower));
        
        steamUnbanPower = Integer.valueOf(properties.getProperty("steamUnbanPower", String.valueOf(steamUnbanPower)));
        logger.info(String.format("Steam ID unban power: %d", steamUnbanPower));

        logSteamIds = Boolean.valueOf(properties.getProperty("logSteamIds", String.valueOf(logSteamIds)));
        logger.info(String.format("Log Steam IDs: %s", String.valueOf(logSteamIds)));
        
        logLimit = Integer.valueOf(properties.getProperty("logLimit", String.valueOf(logLimit)));
        logLimit = Math.min(Math.max(0, logLimit), Integer.MAX_VALUE);
        logger.info(String.format("Log size limit: %d bytes", logLimit));
        
        logCount = Integer.valueOf(properties.getProperty("logCount", String.valueOf(logCount)));
        logCount = Math.min(Math.max(1, logCount), Integer.MAX_VALUE);
        logger.info(String.format("Log files count: %d files", logCount));
    }

    @Override
    public void preInit() {
        try { idLogger.addHandler(new FileHandler("steamids.%g.log", logLimit, logCount, true)); }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Can't create file handler to log Steam IDs.", ex);
        }
        
        ModifyQuestionClass();
    }
    
    @Override
    public void init() {
        InputStream stream = null;
      
        logger.info("Loading Steam ID bans JSON.");
        try {
            if (!Files.exists(banfile)) logger.info("File does not exist yet.");
            else {
                JsonReader reader = null;
                try {
                    reader = Json.createReader(stream = Files.newInputStream(banfile));
                    JsonObject json = reader.readObject();
                    JsonArray arr = json.getJsonArray("bans");
                    
                    if (arr.isEmpty()) logger.info("The JSON set seems to be empty or is malformed.");
                    else {
                        Iterator iter = arr.iterator();
                        while (iter.hasNext()) {
                            JsonObject ban = (JsonObject)iter.next();
                            
                            if (!ban.containsKey("steamid")) {
                                logger.warning("JSON ban does not include a mandatory steamid entry, skipping.");
                                continue;
                            }
                            
                            BanEntry banEntry = new BanEntry(
                                ban.getString("issuer", "unknown"),
                                ban.getString("steamid"),
                                dateFormat.parse(ban.getString("issued", dateFormat.format(new Date()))),
                                dateFormat.parse(ban.getString("expiry", dateFormat.format(new Date()))),
                                ban.getString("reason", "unspecified"),
                                ban.containsKey("characters") ? ban.getJsonArray("characters").getValuesAs(JsonString.class) : null);
                            
                            if (bans.containsKey(banEntry.getSteamId()))
                                logger.warning(String.format("Duplicate ban entry for steamid %s, skipping.", banEntry.getSteamId()));
                            else {
                                
                                StringBuilder sb = new StringBuilder();
                                sb.append(String.format("Loading steamid ban for %s, issued at %s by %s for reason: %s, expires %s, known characters: ",
                                    banEntry.getSteamId(), 
                                    dateFormat.format(banEntry.getIssued()),
                                    banEntry.getIssuer(),
                                    banEntry.getReason(), 
                                    dateFormat.format(banEntry.getExpiry())));
                                for (String name : banEntry.getNames())
                                    sb.append(String.format("%s, ", name));
                                if (banEntry.getNames().size() > 0)
                                    sb.delete(sb.length() - 2, sb.length());
                                sb.append(".");
                                logger.info(sb.toString());
                                ArrayList<Integer> test = new ArrayList<>();
                                Arrays.toString(test.toArray()).replace(" ", "");

                                bans.put(banEntry.getSteamId(), banEntry);
                            }
                        }
                    }
                }
                catch (Exception ex) {
                    logger.log(Level.SEVERE, "Reading JSON banlist failed.", ex);
                }
                finally {
                    try { if (reader != null) reader.close(); }
                    catch (Exception ex) { logger.log(Level.SEVERE, "JsonReader failed to close.", ex); }
                }
            }
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Can't open JSON steamid banlist.", ex);
        }
        finally {
            try { if (stream != null) stream.close(); }
            catch (Exception ex) { logger.log(Level.SEVERE, "InputStream failed to close.", ex); }
        }
        
        InvocationHandlerFactory factory = new InvocationHandlerFactory() {
            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {
                    public Object invoke(Object object, Method method, Object[] args) throws Throwable {
                        LoginHandler handler = (LoginHandler)object;
                        String username = (String)args[0];
                        String steamid = (String)args[1];
                        
                        if (logSteamIds)
                            idLogger.info(String.format("%s trying to login with steam ID %s on IP %s",
                                username, steamid, handler.getConnectionIp()));
                        
                        // Is Steam ID banned?
                        if (bans.containsKey(steamid)) {
                            BanEntry ban = bans.get(steamid);
                            
                            // Ban has expired?
                            if (ban.isExpired()) {
                                // Keep a record of new characters for previous bans.
                                if (ban.addName(username))
                                    logger.warning(String.format("Adding new charcters %s to previously banned entry for %s that has expired.", username, steamid));
                                
                                return method.invoke(object, args); 
                            }
                            
                            handler.sendLoginAnswer(false, "You are banned from this server.", 0f, 0f, 0f, 0f, 0, "model.player.broken.", (byte)0, 0, (byte)0, (byte)0, 0L, 0, (byte)0, -10L, 0f);
                            
                            logger.warning(String.format("%s tried to login with Steam ID %s, but is banned for reason: %s",
                                username, steamid, properties.getProperty(steamid, "No reason specified.")));
                        
                            if (ban.addName(username))
                                logger.warning(String.format("%s was a new character to this ID and was added to the known list.", username));
                            
                            return false;
                        }
                        
                        return method.invoke(object, args);
                    }
                };
            }
        };
        HookManager.getInstance().registerHook("com.wurmonline.server.LoginHandler", "preValidateLogin", "(Ljava/lang/String;Ljava/lang/String;)Z", factory);
        
        factory = new InvocationHandlerFactory() {
            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {
                    public Object invoke(Object object, Method method, Object[] args) throws Throwable {
                        saveJson();
                        
                        return method.invoke(object, args);
                    }
                };
            }
        };
        HookManager.getInstance().registerHook("com.wurmonline.server.Server", "startShutdown", "(ILjava/lang/String;)V", factory);
        
        factory = new InvocationHandlerFactory() {
            @Override
            public InvocationHandler createInvocationHandler() {
                return new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        PlayerInfo info = (PlayerInfo)proxy;
                        Player player = Players.getInstance().getPlayerOrNull(info.getPlayerId());
                        
                        if (player == null || !bans.containsKey(player.SteamId))
                            return method.invoke(proxy, args);
                        
                        return true;
                    }
                };
            }
        };
        HookManager.getInstance().registerHook("com.wurmonline.server.players.PlayerInfo", "isBanned", "()Z", factory);
    }

    public void saveJson() {
        logger.info("Saving JSON banlist.");
            
        HashMap<String, Boolean> factoryOptions = new HashMap();
        factoryOptions.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory factory = Json.createWriterFactory(factoryOptions);
        
        JsonWriter writer = null;
        JsonArrayBuilder builder = Json.createArrayBuilder();
        
        for (BanEntry ban : bans.values()) {
            JsonArrayBuilder characters = Json.createArrayBuilder();
            ban.getNames().stream().forEach(x -> characters.add(x));
            
            builder.add(
                Json.createObjectBuilder()
                .add("steamid", ban.getSteamId())
                .add("issuer", ban.getIssuer())
                .add("issued", dateFormat.format(ban.getIssued()))
                .add("reason", ban.getReason())
                .add("expiry", dateFormat.format(ban.getExpiry()))
                .add("characters", characters)
            );
        }
        
        JsonObject json = Json.createObjectBuilder().add("bans", builder).build();

        logger.info("JSON object of banlist was created.");
        
        try {
            writer = factory.createWriter(Files.newOutputStream(banfile));
            writer.writeObject(json);
            
            logger.info("JSON banlist was saved to disk.");
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not write JSON banfile.", ex);
        }
        finally {
            try { if (writer != null) writer.close(); }
            catch (Exception ex) { logger.log(Level.SEVERE, "JSON banlist writer failed to close.", ex); }
        }
        
        logger.info("Saving JSON banlist done.");
    }
    
    /* This hooks into LoginHandler.sendLoggedInPeople(Player player), but the
        Player object does not hold the steam ID, if it's a newly created
        character. The ban still works, but the log message will show an empty
        Steam ID, as it's not set in LoginHandler.createPlayer at step == 13.
    
        Logging now happens in preValidateLogin instead, with no access to a
        Player object for usernames that do not have a character yet (existing
        players can be grabbed from an instance of Players.
    */
    /*public void onPlayerLogin(Player player) {
        idLogger.info(String.format("%s logged in with steam ID %s on IP %s with MAC %s.",
            player.getName(), player.SteamId, player.getCommunicator().getConnection().getIp(), player.getCommunicator().macAddr));
    }*/

    @Override
    public boolean onPlayerMessage(Communicator communicator, String str) {
        Creature creature = (Creature)communicator.getPlayer();
        
        if (str.startsWith("#steamban ")) {
            if (communicator.getPlayer().getPower() < steamBanPower)
                return true;
            
            String[] fields = str.split(" ");
            if (fields.length < 2) {
                communicator.sendAlertServerMessage("Syntax: #steamban <steamid64> [reason] (e.g. #steamban 12345 ate all the cookies).");
                return true;
            }
            
            String steamid = fields[1];
            
            if (!steamid.matches("[0-9]*")) {
                communicator.sendAlertServerMessage("Steam ID is numerical only.");
                return true;
            }
            
            if (bans.containsKey(steamid)){
                BanEntry ban = bans.get(steamid);
                communicator.sendAlertServerMessage(String.format("%s has already been banned for reason: %s by %s at %s, expires at %s. Opening edit dialogue.", 
                    steamid, ban.getReason(), ban.getIssuer(), dateFormat.format(ban.getIssued()), dateFormat.format(ban.getExpiry())));
                
                new CreateBanQuestion(communicator.getPlayer(), bans, steamid).sendQuestion();
            }
            else {
                String reason;
                
                if (fields.length >= 3) reason = str.substring(str.indexOf(" ", str.indexOf(" ") + 1) + 1);
                else reason = "unspecified";
                
                if (!reason.matches("[a-zA-Z_0-9,.\\s]*")) {
                    communicator.sendAlertServerMessage("Only alphanumeric characters are allowed for the reason.");
                    return true;
                }
                
                BanEntry ban = new BanEntry(
                    communicator.getPlayer().getName(), // issuer
                    steamid, // steamid
                    new Date(), // issued date
                    new GregorianCalendar(6969,12,31).getTime(), // expiry
                    reason); // reason
                
                bans.put(steamid, ban);
                
                String logMessage = String.format("%s has been banned. Reason: %s", steamid, reason);
                communicator.sendAlertServerMessage(logMessage);
                communicator.getPlayer().getLogger().warning(logMessage); // separate player logfile.
                logger.warning(logMessage);
                
                for (Player player : Players.getInstance().getPlayers()) {
                    try {
                        if (player.SteamId.equals(steamid) && !player.isLoggedOut()){
                            player.logoutIn(10, "You are banned from this server.");
                            ban.addName(player.getName());
                        }
                    }
                    catch (Exception ex) {
                        logger.log(Level.SEVERE, String.format(
                            "Player %s was banned by %s via SteamID64 but could not be logged out.", 
                            player.getName(), communicator.getPlayer().getName()), ex);
                    }
                }

                saveJson();
                
                new CreateBanQuestion(communicator.getPlayer(), bans, steamid, reason).sendQuestion();
            }
            
            return true;
        }
        else if (str.startsWith("#steamunban ")) {
            if (communicator.getPlayer().getPower() < steamUnbanPower)
                return true;
            
            String[] fields = str.split(" ");
            if (fields.length < 2) {
                communicator.sendAlertServerMessage("Syntax: #steamunban steamid64");
                return true;
            }
            
            String steamid = fields[1];
            
            if (!bans.containsKey(steamid)) communicator.sendAlertServerMessage(String.format("%s was not banned.", fields[1]));
            else {
                bans.get(steamid).disable();
                String logMessage = String.format("The ban for %s has been disabled. It's still on record with the current date and time as expiration.", fields[1]);
                communicator.sendAlertServerMessage(logMessage);
                communicator.getPlayer().getLogger().warning(logMessage);
                logger.warning(logMessage);
                
                saveJson();
            }
            
            return true;
        }
        else if (str.startsWith("#steambans")) {
            if (communicator.getPlayer().getPower() < steamUnbanPower)
                return true;

            Player owner = communicator.getPlayer();
            new BanQuestion(owner, bans, 0).sendQuestion();
            
            return true;
        }
        
        return false;
    }
    
    private void ModifyQuestionClass() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.questions.Question");
            
            for (CtConstructor ctor : ctClass.getConstructors())
                ctor.setModifiers((ctor.getModifiers() & ~(Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.PUBLIC);
            
            for (CtMethod method : ctClass.getDeclaredMethods())
                if (method.getName().startsWith("getBml") || method.getName().startsWith("create"))
                    method.setModifiers((method.getModifiers() & ~(Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.PUBLIC);
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not modify com.wurmonline.server.questions.Question.", ex);
        }
    }
    
    public static String getLoggerName(Class c) {
        return String.format("%s (v%s)", c.getName(), c.getPackage().getImplementationVersion());
    }
    
    public static Logger getLogger(Class c) {
        return Logger.getLogger(getLoggerName(c));
    }
}
