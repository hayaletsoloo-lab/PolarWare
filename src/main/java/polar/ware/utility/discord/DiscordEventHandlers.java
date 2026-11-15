package polar.ware.utility.discord;

import java.util.Arrays;
import java.util.List;
import polar.ware.utility.discord.callbacks.JoinGameCallback;
import polar.ware.utility.discord.callbacks.ErroredCallback;
import polar.ware.utility.discord.callbacks.ReadyCallback;
import polar.ware.utility.discord.callbacks.SpectateGameCallback;
import polar.ware.utility.discord.callbacks.JoinRequestCallback;
import polar.ware.utility.discord.callbacks.DisconnectedCallback;
import com.sun.jna.Structure;

public class DiscordEventHandlers extends Structure {
    public DisconnectedCallback disconnected;
    public JoinRequestCallback joinRequest;
    public SpectateGameCallback spectateGame;
    public ReadyCallback ready;
    public ErroredCallback errored;
    public JoinGameCallback joinGame;
    
    protected List<String> getFieldOrder() {
        return Arrays.asList("ready", "disconnected", "errored", "joinGame", "spectateGame", "joinRequest");
    }
}