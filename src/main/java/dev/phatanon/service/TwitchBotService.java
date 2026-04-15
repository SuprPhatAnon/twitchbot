package dev.phatanon.service;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.auth.domain.TwitchScopes;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.client.websocket.domain.WebsocketConnectionState;
import com.github.twitch4j.common.events.domain.EventChannel;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.eventsub.events.ChannelCheerEvent;
import com.github.twitch4j.eventsub.events.ChannelFollowEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscribeEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscriptionGiftEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscriptionMessageEvent;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
import com.github.twitch4j.helix.domain.CustomReward;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.helix.domain.ChatMessage;
import com.github.twitch4j.helix.domain.StreamList;
import dev.phatanon.ConnectionStartupLogger;
import dev.phatanon.entity.Song;
import dev.phatanon.entity.SongPlay;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.model.ChatMessageContext;
import dev.phatanon.service.chat.ChatMessageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Service responsible for interacting with Twitch API and managing song playback.
 * It handles Twitch EventSub events for messages and rewards, and maintains a song queue.
 */
@Service
public class TwitchBotService implements ConnectionStartupLogger.ITwitchBotService {

    private static final Logger log = LoggerFactory.getLogger(TwitchBotService.class);

    /**
     * Record to log recent channel point redeems.
     */
    public record RedeemLog(String user, String rewardTitle, LocalDateTime timestamp) {}

    /**
     * Represents a song in the queue with its trigger source.
     */
    public record QueuedSong(Song song, String source, boolean incrementStats) {}

    /**
     * Recent channel point redeems stored in-memory.
     */
    private final List<RedeemLog> recentRedeems = new LinkedList<>();
    private static final int MAX_REDEEM_LOGS = 50;

    @Value("${twitch.api-key:default_secret_key}")
    private String apiKey;

    @Value("${twitch.song-delay-seconds:5}")
    private int defaultSongDelaySeconds;

    @Value("${twitch.redirect-uri-host:https://music.phat.wtf}")
    private String redirectUriHost;


    private final SimpMessagingTemplate messagingTemplate;
    private final SongRepository songRepository;
    private final SongPlayRepository songPlayRepository;
    private final TwitchConfigRepository twitchConfigRepository;
    private TwitchClient twitchClient;
    private CredentialManager credentialManager;

    private String currentAccessToken;
    private String currentBotAccessToken;
    private String currentChannelName;
    private String broadcasterId;
    private String botUserId;
    private int currentSongDelaySeconds;

    private final java.util.Queue<QueuedSong> songQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    private boolean isSongPlaying = false;
    private Song currentlyPlayingSong = null;

    /**
     * Retrieves the current number of songs in the queue.
     * @return The size of the song queue.
     */
    public synchronized int getQueueSize() {
        return songQueue.size();
    }

    /**
     * Retrieves the current song queue.
     * @return A list of songs currently in the queue.
     */
    public synchronized List<QueuedSong> getQueue() {
        return new LinkedList<>(songQueue);
    }

    /**
     * Broadcasts the current queue size and the full queue to WebSocket subscribers.
     */
    protected void broadcastQueueSize() {
        messagingTemplate.convertAndSend("/topic/queue-size", getQueueSize());
        messagingTemplate.convertAndSend("/topic/queue", getQueue());
    }

    /**
     * Broadcasts the currently playing song to WebSocket subscribers on /topic/current-song.
     */
    protected void broadcastCurrentSong() {
        if (currentlyPlayingSong != null) {
            messagingTemplate.convertAndSend("/topic/current-song", currentlyPlayingSong);
        } else {
            messagingTemplate.convertAndSend("/topic/current-song", "null");
        }
    }

    /**
     * Broadcasts a message to refresh the songs list on the UI.
     */
    protected void broadcastSongsRefresh() {
        messagingTemplate.convertAndSend("/topic/songs", "refresh");
    }
    private boolean isStreamOnline = false;
    private boolean isStreamerConnected = false;
    private boolean isBotConnected = false;
    private final AtomicBoolean greetingSentThisSession = new AtomicBoolean(false);

    private final Random random = new Random();
    private final ScheduledExecutorService scheduler;
    private final ChatMessageService chatMessageService;
    private ScheduledFuture<?> tokenRefreshTask;

    public TwitchBotService(SimpMessagingTemplate messagingTemplate, SongRepository songRepository, SongPlayRepository songPlayRepository, TwitchConfigRepository twitchConfigRepository, ScheduledExecutorService scheduler, ChatMessageService chatMessageService) {
        this.messagingTemplate = messagingTemplate;
        this.songRepository = songRepository;
        this.songPlayRepository = songPlayRepository;
        this.twitchConfigRepository = twitchConfigRepository;
        this.scheduler = scheduler;
        this.chatMessageService = chatMessageService;
    }

    /**
     * Checks if a song is currently playing.
     * @return true if a song is playing, false otherwise.
     */
    public synchronized boolean isSongPlaying() {
        return isSongPlaying;
    }

    /**
     * Sends a chat message to the configured Twitch channel.
     * @param message The message to send.
     */
    public void sendChatMessage(String message) {
        if (isTwitchConnected()) {
            try {
                String token = currentBotAccessToken != null && !currentBotAccessToken.isBlank() ? currentBotAccessToken : currentAccessToken;
                ChatMessage chatMessage = ChatMessage.builder()
                        .broadcasterId(broadcasterId)
                        .senderId(botUserId)
                        .message(message)
                        .build();
                twitchClient.getHelix().sendChatMessage(token, chatMessage).execute();
                log.info("Sent chat message to {}: {}", currentChannelName, message);
            } catch (Exception e) {
                log.error("Failed to send chat message via Helix: {}", e.getMessage());
            }
        } else {
            log.warn("Cannot send chat message: Twitch EventSub is not connected.");
        }
    }

    /**
     * Checks if the streamer's EventSub client is connected.
     * @return true if connected, false otherwise
     */
    public synchronized boolean isStreamerConnected() {
        return isStreamerConnected;
    }

    /**
     * Checks if the bot's chat client is connected.
     * @return true if connected, false otherwise
     */
    public synchronized boolean isBotConnected() {
        return isBotConnected;
    }

    /**
     * Checks if the Twitch EventSub client is connected.
     * @return true if connected, false otherwise
     * @deprecated Use {@link #isStreamerConnected()} or {@link #isBotConnected()} instead.
     */
    @Deprecated
    public synchronized boolean isTwitchConnected() {
        return isStreamerConnected();
    }

    /**
     * Retrieves the current Twitch client instance.
     * @return The {@link TwitchClient} instance.
     */
    public TwitchClient getTwitchClient() {
        return twitchClient;
    }

    protected void setTwitchClient(TwitchClient twitchClient) {
        this.twitchClient = twitchClient;
    }

    /**
     * Reconnects to Twitch by closing the existing client and re-initializing.
     */
    public synchronized void reconnect() {
        log.info("Reconnection requested. Closing existing Twitch client...");
        isStreamerConnected = false;
        isBotConnected = false;
        if (twitchClient != null) {
            try {
                twitchClient.close();
            } catch (Exception e) {
                log.error("Error closing Twitch client: {}", e.getMessage());
            }
            twitchClient = null;
        }
        if (tokenRefreshTask != null) {
            tokenRefreshTask.cancel(false);
            tokenRefreshTask = null;
        }
        init();
    }

    /**
     * Initializes the Twitch client and registers event listeners.
     * This method is called after dependency injection is complete.
     */
    @PostConstruct
    public void init() {
        try {
            isStreamerConnected = false;
            isBotConnected = false;
            log.info("Checking for existing Twitch configuration...");
            List<TwitchConfig> configs = twitchConfigRepository.findAll();
            if (configs.isEmpty()) {
                log.warn("No Twitch configuration found in database. Waiting for manual configuration.");
                return;
            }

            TwitchConfig config = configs.get(0);

            if (config.getClientId() == null || config.getClientId().isBlank() ||
                config.getClientSecret() == null || config.getClientSecret().isBlank() ||
                config.getAccessToken() == null || config.getAccessToken().isBlank()) {
                log.warn("Twitch configuration is incomplete (missing Client ID, Secret, or Access Token). Waiting for manual configuration.");
                return;
            }

            currentAccessToken = config.getAccessToken();
            currentBotAccessToken = config.getBotAccessToken();
            currentChannelName = config.getChannelName();
            currentSongDelaySeconds = config.getSongDelaySeconds() > 0 ? config.getSongDelaySeconds() : defaultSongDelaySeconds;

            log.info("Attempting to initialize Twitch bot for channel: {}", currentChannelName);
            log.info("Client ID: {}", config.getClientId() != null ? config.getClientId().substring(0, Math.min(config.getClientId().length(), 4)) + "..." : "null");
            log.info("Has Streamer Access Token: {}", config.getAccessToken() != null && !config.getAccessToken().isBlank());
            log.info("Has Bot Access Token: {}", config.getBotAccessToken() != null && !config.getBotAccessToken().isBlank());
            
            CredentialManager credentialManager = CredentialManagerBuilder.builder().build();
            TwitchIdentityProvider identityProvider = new TwitchIdentityProvider(config.getClientId(), config.getClientSecret(), null);
            credentialManager.registerIdentityProvider(identityProvider);
            
            OAuth2Credential streamerCredential = new OAuth2Credential(
                    TwitchIdentityProvider.PROVIDER_NAME,
                    config.getAccessToken(),
                    config.getRefreshToken(),
                    null,
                    null,
                    null,
                    null
            );
            
            OAuth2Credential botCredential = (config.getBotAccessToken() != null && !config.getBotAccessToken().isBlank())
                    ? new OAuth2Credential(
                            TwitchIdentityProvider.PROVIDER_NAME,
                            config.getBotAccessToken(),
                            config.getBotRefreshToken(),
                            null,
                            null,
                            null,
                            null
                    )
                    : streamerCredential;
            
            log.info("Building TwitchClient...");
            TwitchClientBuilder builder = TwitchClientBuilder.builder()
                    .withClientId(config.getClientId())
                    .withClientSecret(config.getClientSecret())
                    .withCredentialManager(credentialManager)
                    .withEnableChat(true) // Ensure Chat is enabled if we want listeners
                    .withEnableEventSocket(true)
                    .withEnableHelix(true)
                    .withDefaultAuthToken(streamerCredential)
                    .withChatAccount(botCredential);

            if (twitchClient == null) {
                log.info("Creating new TwitchClient instance...");
                twitchClient = builder.build();
            } else {
                log.info("TwitchClient instance already exists, but re-init was called.");
            }
            log.info("TwitchClient built successfully.");

            registerEventListeners();

            tokenRefreshTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    List<TwitchConfig> currentConfigs = twitchConfigRepository.findAll();
                    if (!currentConfigs.isEmpty()) {
                        TwitchConfig currentConfig = currentConfigs.get(0);
                        boolean updated = false;

                        if (streamerCredential.getAccessToken() != null && !streamerCredential.getAccessToken().equals(currentConfig.getAccessToken())) {
                            log.info("Streamer access token changed (refreshed). Saving new tokens.");
                            currentConfig.setAccessToken(streamerCredential.getAccessToken());
                            currentConfig.setRefreshToken(streamerCredential.getRefreshToken());
                            updated = true;
                        }

                        if (botCredential != streamerCredential && botCredential.getAccessToken() != null && !botCredential.getAccessToken().equals(currentConfig.getBotAccessToken())) {
                            log.info("Bot access token changed (refreshed). Saving new tokens.");
                            currentConfig.setBotAccessToken(botCredential.getAccessToken());
                            currentConfig.setBotRefreshToken(botCredential.getRefreshToken());
                            updated = true;
                        }

                        if (updated) {
                            twitchConfigRepository.save(currentConfig);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error checking for token refreshes: {}", e.getMessage());
                }
            }, 5, 5, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("Failed to initialize Twitch bot: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Registers EventSub event listeners with the Twitch client.
     */
    private void registerEventListeners() {
        log.info("Registering connection state listeners...");
        twitchClient.getEventManager().onEvent(EventSocketConnectionStateEvent.class, event -> {
            log.info("EventSub Socket Connection State Change (Streamer): {} -> {}", event.getPreviousState(), event.getState());
            isStreamerConnected = event.getState() == WebsocketConnectionState.CONNECTED;
            if (event.getState() == WebsocketConnectionState.DISCONNECTED || event.getState() == WebsocketConnectionState.LOST) {
                log.warn("EventSub Socket disconnected! Reason: {}", event.getPreviousState());
            }
            messagingTemplate.convertAndSend("/topic/streamer-connection-status", isStreamerConnected);
        });

        twitchClient.getEventManager().onEvent(ChatConnectionStateEvent.class, event -> {
            log.info("Chat Connection State Change (Bot): {} -> {}", event.getPreviousState(), event.getState());
            isBotConnected = event.getState() == WebsocketConnectionState.CONNECTED;
            if (!isBotConnected && (event.getState() == WebsocketConnectionState.DISCONNECTED || event.getState() == WebsocketConnectionState.LOST)) {
                log.warn("Chat connection lost!");
            }
            messagingTemplate.convertAndSend("/topic/bot-connection-status", isBotConnected);
        });

        try {
            log.info("Fetching channel ID for {} using Helix...", currentChannelName);
            UserList userList = twitchClient.getHelix().getUsers(currentAccessToken, null, List.of(currentChannelName)).execute();
            if (userList.getUsers().isEmpty()) {
                log.error("Could not find Twitch user for channel name: {}", currentChannelName);
                return;
            }
            broadcasterId = userList.getUsers().get(0).getId();
            log.info("Channel ID for {}: {}", currentChannelName, broadcasterId);

            log.info("Fetching bot user ID using Helix...");
            String botToken = currentBotAccessToken != null && !currentBotAccessToken.isBlank() ? currentBotAccessToken : currentAccessToken;
            UserList botUserList = twitchClient.getHelix().getUsers(botToken, null, null).execute();
            if (botUserList.getUsers().isEmpty()) {
                log.error("Could not find Twitch user for bot token.");
            } else {
                botUserId = botUserList.getUsers().get(0).getId();
                log.info("Bot User ID: {}", botUserId);
            }

            // Check initial stream status
            log.info("Checking initial stream status for broadcasterId {}...", broadcasterId);
            StreamList streamList = twitchClient.getHelix().getStreams(currentAccessToken, null, null, 1, null, null, List.of(broadcasterId), null).execute();
            isStreamOnline = !streamList.getStreams().isEmpty();
            log.info("Initial stream status for {}: {}", currentChannelName, isStreamOnline ? "ONLINE" : "OFFLINE");

            if (isStreamOnline) {
                scheduleThumboGreeting();
            }
        } catch (Exception e) {
            log.error("Error during Helix API calls in registerEventListeners: {}", e.getMessage(), e);
        }

        twitchClient.getEventManager().onEvent(CustomRewardRedemptionAddEvent.class, event -> {
            String title = event.getReward().getTitle();
            String user = event.getUserName();
            log.info("Reward '{}' redeemed by {}", title, user);

            RedeemLog redeemLog = new RedeemLog(user, title, LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);

            playRandomSong(title);
        });

        twitchClient.getEventManager().onEvent(ChannelCheerEvent.class, event -> {
            String user = event.getUserName();
            Integer bits = event.getBits();
            log.info("{} cheered {} bits", user, bits);

            RedeemLog redeemLog = new RedeemLog(user, bits + " Bits", LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        });

        twitchClient.getEventManager().onEvent(ChannelFollowEvent.class, event -> {
            String user = event.getUserName();
            log.info("{} followed the channel", user);

            RedeemLog redeemLog = new RedeemLog(user, "Followed!", LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        });

        twitchClient.getEventManager().onEvent(ChannelSubscribeEvent.class, event -> {
            String user = event.getUserName();
            String tier = event.getTier().name();
            log.info("{} subscribed at Tier {}", user, tier);

            RedeemLog redeemLog = new RedeemLog(user, "Subscribed (Tier " + tier + ")", LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        });

        twitchClient.getEventManager().onEvent(ChannelSubscriptionGiftEvent.class, event -> {
            String user = event.getUserName();
            Integer count = event.getTotal();
            String tier = event.getTier().name();
            String logMsg = count != null && count > 1 ? "gifted " + count + " subs (Tier " + tier + ")" : "gifted a sub (Tier " + tier + ")";
            log.info("{} {}", user, logMsg);

            RedeemLog redeemLog = new RedeemLog(user, logMsg, LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        });

        twitchClient.getEventManager().onEvent(ChannelSubscriptionMessageEvent.class, event -> {
            String user = event.getUserName();
            int months = event.getCumulativeMonths();
            String tier = event.getTier().name();
            log.info("{} resubscribed for {} months (Tier {})", user, months, tier);

            RedeemLog redeemLog = new RedeemLog(user, "Resubscribed (" + months + " months, Tier " + tier + ")", LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        });

        twitchClient.getEventManager().onEvent(ChannelGoLiveEvent.class, event -> {
            log.info("Stream started for channel: {}", event.getChannel().getName());
            isStreamOnline = true;
            messagingTemplate.convertAndSend("/topic/stream-status", true);
            scheduleThumboGreeting();
        });

        twitchClient.getEventManager().onEvent(ChannelGoOfflineEvent.class, event -> {
            log.info("Stream ended for channel: {}", event.getChannel().getName());
            isStreamOnline = false;
            messagingTemplate.convertAndSend("/topic/stream-status", false);
            greetingSentThisSession.set(false);
            synchronized (this) {
                songQueue.clear();
                log.info("Stream ended. Song queue cleared.");
                broadcastQueueSize();
            }
        });

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            ChatMessageContext context = ChatMessageContext.builder()
                    .message(event.getMessage())
                    .senderName(event.getUser().getName())
                    .channelName(event.getChannel().getName())
                    .build();
            chatMessageService.processMessage(context);
        });

        log.info("Twitch bot initialized for channel: {}", currentChannelName);
    }

    /**
     * Checks if the stream is currently online.
     * Stream status is updated via {@link ChannelGoLiveEvent} and {@link ChannelGoOfflineEvent}.
     * @return true if online, false otherwise
     */
    public synchronized boolean isStreamOnline() {
        return isStreamOnline;
    }

    /**
     * Retrieves the currently playing song.
     * @return The currently playing {@link Song}, or null if no song is playing.
     */
    public synchronized Song getCurrentlyPlayingSong() {
        return currentlyPlayingSong;
    }

    /**
     * Schedules a greeting message for the channel.
     * The greeting is sent 2 minutes after the stream goes online, and only once per stream session.
     */
    private void scheduleThumboGreeting() {
        if (greetingSentThisSession.get()) {
            return;
        }

        log.info("Scheduling Thumbo greeting in 2 minutes...");
        scheduler.schedule(() -> {
            if (isStreamOnline() && greetingSentThisSession.compareAndSet(false, true)) {
                log.info("Sending Thumbo greeting message.");
                try {
                    String token = currentBotAccessToken != null && !currentBotAccessToken.isBlank() ? currentBotAccessToken : currentAccessToken;
                    ChatMessage chatMessage = ChatMessage.builder()
                            .broadcasterId(broadcasterId)
                            .senderId(botUserId)
                            .message("Hi Thumbo <3")
                            .build();
                    twitchClient.getHelix().sendChatMessage(token, chatMessage).execute();
                } catch (Exception e) {
                    log.error("Failed to send Thumbo greeting via Helix: {}", e.getMessage());
                }
            } else {
                log.info("Thumbo greeting conditions not met: stream online: {}, greeting already sent: {}",
                        isStreamOnline(), greetingSentThisSession.get());
            }
        }, 2, TimeUnit.MINUTES);
    }

    /**
     * Queues a random song from the repository for a specific redeem name.
     * @param redeemName The name of the redeem that triggered this call.
     */
    public synchronized void playRandomSong(String redeemName) {
        /*
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playRandomSong request for redeem: {}", redeemName);
            return;
        }
        */
        List<Song> songs = songRepository.findByRedeemTitle(redeemName).stream()
                .filter(Song::isEnabled)
                .toList();
        if (songs.isEmpty()) {
            log.warn("No enabled songs found in the database for redeem: {}", redeemName);
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        log.info("Queueing song: {} by {} for redeem: {}", song.getName(), song.getArtist(), redeemName);

        if (!isSongPlaying) {
            playSong(song, redeemName, true);
        } else {
            songQueue.add(new QueuedSong(song, redeemName, true));
            log.info("Song added to queue. Queue size: {}", songQueue.size());
            broadcastQueueSize();
        }
    }

    /**
     * Clears the song queue and stops the currently playing song.
     * Broadcasts the updated state to all clients.
     */
    public synchronized void clearQueue() {
        log.info("Clearing song queue and stopping current playback.");
        songQueue.clear();
        isSongPlaying = false;
        currentlyPlayingSong = null;
        broadcastQueueSize();
        broadcastCurrentSong();
    }

    private void playSong(Song song, String source, boolean incrementStats) {
        isSongPlaying = true;
        currentlyPlayingSong = song;
        log.info("Playing song: {} by {} (source: {}, incrementStats: {})", song.getName(), song.getArtist(), source, incrementStats);

        if (incrementStats) {
            song.incrementPlayCount();
            songRepository.save(song);
            songPlayRepository.save(new SongPlay(song, LocalDateTime.now(), source));
            broadcastSongsRefresh();
        }

        messagingTemplate.convertAndSend("/topic/play", song);
        broadcastCurrentSong();
    }

    /**
     * Handles the event when a song has finished playing.
     * Triggers the playback of the next song in the queue after a configured delay.
     */
    public synchronized void handleSongFinished() {
        log.info("Song finished. Waiting {} seconds before next song...", currentSongDelaySeconds);
        isSongPlaying = false;
        currentlyPlayingSong = null;
        broadcastCurrentSong();

        scheduler.schedule(() -> {
            synchronized (this) {
                if (isSongPlaying) {
                    log.info("Another song started during delay. Skipping queue poll.");
                    return;
                }
                if (!songQueue.isEmpty()) {
                    QueuedSong next = songQueue.poll();
                    log.info("Queue polling successful. Next song: {} by {}", next.song().getName(), next.song().getArtist());
                    playSong(next.song(), next.source(), next.incrementStats());
                    broadcastQueueSize();
                } else {
                    log.info("Queue is empty, no more songs to play.");
                }
            }
        }, currentSongDelaySeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Triggers the playback of a specific song by its ID.
     * This is typically used by the Admin UI or Streamer UI for manual testing.
     * @param id The ID of the song to play.
     * @param incrementStats Whether to increment the play count for this song.
     */
    public synchronized void playSongById(Long id, boolean incrementStats) {
        /*
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playSongById request for song ID: {}", id);
            return;
        }
        */
        songRepository.findById(id).ifPresent(song -> {
            log.info("Manually queueing song: {} by {} (incrementStats: {})", song.getName(), song.getArtist(), incrementStats);
            if (!isSongPlaying) {
                playSong(song, "manual", incrementStats);
            } else {
                songQueue.add(new QueuedSong(song, "manual", incrementStats));
                log.info("Song added to queue. Queue size: {}", songQueue.size());
                broadcastQueueSize();
            }
        });
    }

    /**
     * Triggers the playback of a specific song by its ID.
     * This is typically used by the Admin UI for manual testing.
     * @param id The ID of the song to play.
     */
    public synchronized void playSongById(Long id) {
        playSongById(id, false);
    }

    /**
     * Simulates a Twitch redeem for testing purposes.
     * @param title The title of the redeem to simulate.
     */
    public void simulateRedeem(String title) {
        log.info("Simulating redeem: {}", title);
        RedeemLog redeemLog = new RedeemLog("TestUser", title, LocalDateTime.now());
        synchronized (recentRedeems) {
            if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                recentRedeems.remove(0);
            }
            recentRedeems.add(redeemLog);
        }
        messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        playRandomSong(title);
    }

    /**
     * Manually triggers a re-sync of Twitch EventSub listeners.
     */
    public void syncEventSub() {
        log.info("Manually syncing EventSub...");
        reconnect();
    }

    /**
     * Verifies the current Twitch credentials.
     * @return true if credentials are valid, false otherwise.
     */
    public boolean testConnection() {
        log.info("Testing Twitch connection for channel: {}...", currentChannelName);
        try {
            if (twitchClient == null) {
                log.info("Twitch client is null, initializing...");
                init();
            }
            if (twitchClient == null) {
                log.error("Twitch client still null after initialization.");
                return false;
            }
            log.info("Executing Helix getUsers call for channel: {}...", currentChannelName);
            UserList userList = twitchClient.getHelix().getUsers(currentAccessToken, null, List.of(currentChannelName)).execute();
            if (userList.getUsers().isEmpty()) {
                log.warn("Twitch connection test: No user found for channel: {}", currentChannelName);
                return false;
            }
            log.info("Twitch connection test successful. User ID: {}", userList.getUsers().get(0).getId());
            return true;
        } catch (Exception e) {
            log.error("Twitch connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Forcefully refreshes the access tokens.
     */
    public void refreshTokens() {
        log.info("Manually refreshing Twitch tokens...");
        reconnect();
    }

    /**
     * Retrieves a list of recent channel point redeems.
     * @return A list of {@link RedeemLog} objects.
     */
    public List<RedeemLog> getRecentRedeems() {
        synchronized (recentRedeems) {
            return List.copyOf(recentRedeems);
        }
    }

    /**
     * Removes a song from the queue by its index.
     * @param index The index of the song to remove.
     */
    public synchronized void removeFromQueue(int index) {
        if (index >= 0 && index < songQueue.size()) {
            List<QueuedSong> list = new LinkedList<>(songQueue);
            list.remove(index);
            songQueue.clear();
            songQueue.addAll(list);
            broadcastQueueSize();
        }
    }

    /**
     * Queues a random song from all available songs in the repository.
     */
    public synchronized void playRandomSong() {
        /*
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playRandomSong request.");
            return;
        }
        */
        List<Song> songs = songRepository.findByEnabled(true);
        if (songs.isEmpty()) {
            log.warn("No enabled songs found in the database");
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        log.info("Queueing song: {} by {}", song.getName(), song.getArtist());

        if (!isSongPlaying) {
            playSong(song, "random", true);
        } else {
            songQueue.add(new QueuedSong(song, "random", true));
            log.info("Song added to queue. Queue size: {}", songQueue.size());
            broadcastQueueSize();
        }
    }
}
