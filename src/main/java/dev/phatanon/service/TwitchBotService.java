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
import com.github.twitch4j.common.events.domain.EventChannel;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.eventsub.events.ChannelCheerEvent;
import com.github.twitch4j.eventsub.events.ChannelFollowEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscribeEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscriptionGiftEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscriptionMessageEvent;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
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

/**
 * Service responsible for interacting with Twitch API and managing song playback.
 * It handles Twitch IRC connection, EventSub events for rewards, and maintains a song queue.
 */
@Service
public class TwitchBotService implements ConnectionStartupLogger.ITwitchBotService {

    private static final Logger log = LoggerFactory.getLogger(TwitchBotService.class);

    /**
     * Record to log recent channel point redeems.
     */
    public record RedeemLog(String user, String rewardTitle, LocalDateTime timestamp) {}

    /**
     * Recent channel point redeems stored in-memory.
     */
    private final List<RedeemLog> recentRedeems = new LinkedList<>();
    private static final int MAX_REDEEM_LOGS = 50;

    @Value("${twitch.client-id}")
    private String clientId;

    @Value("${twitch.client-secret}")
    private String clientSecret;

    @Value("${twitch.access-token}")
    private String accessToken;

    @Value("${twitch.bot-access-token:}")
    private String botAccessToken;

    @Value("${twitch.refresh-token:}")
    private String refreshToken;

    @Value("${twitch.bot-refresh-token:}")
    private String botRefreshToken;

    @Value("${twitch.channel-name}")
    private String channelName;

    @Value("${twitch.redeem-title}")
    private String redeemTitle;

    @Value("${twitch.song-delay-seconds:5}")
    private int defaultSongDelaySeconds;

    @Value("${twitch.use-local-cli:false}")
    private boolean useLocalCli;

    @Value("${twitch.local-cli-url:http://localhost:8080}")
    private String localCliUrl;

    private final SimpMessagingTemplate messagingTemplate;
    private final SongRepository songRepository;
    private final SongPlayRepository songPlayRepository;
    private final TwitchConfigRepository twitchConfigRepository;
    private TwitchClient twitchClient;

    private String currentAccessToken;
    private String currentBotAccessToken;
    private String currentChannelName;
    private String broadcasterId;
    private String botUserId;
    private int currentSongDelaySeconds;

    private final java.util.Queue<Song> songQueue = new java.util.concurrent.LinkedBlockingQueue<>();
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
     * Broadcasts the current queue size to WebSocket subscribers on /topic/queue-size.
     */
    private void broadcastQueueSize() {
        messagingTemplate.convertAndSend("/topic/queue-size", getQueueSize());
    }

    /**
     * Broadcasts the currently playing song to WebSocket subscribers on /topic/current-song.
     */
    private void broadcastCurrentSong() {
        messagingTemplate.convertAndSend("/topic/current-song", currentlyPlayingSong);
    }
    private boolean isStreamOnline = false;
    private final AtomicBoolean greetingSentThisSession = new AtomicBoolean(false);

    private final Random random = new Random();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    public TwitchBotService(SimpMessagingTemplate messagingTemplate, SongRepository songRepository, SongPlayRepository songPlayRepository, TwitchConfigRepository twitchConfigRepository) {
        this.messagingTemplate = messagingTemplate;
        this.songRepository = songRepository;
        this.songPlayRepository = songPlayRepository;
        this.twitchConfigRepository = twitchConfigRepository;
    }

    /**
     * Checks if the Twitch IRC client is connected.
     * @return true if connected, false otherwise
     */
    public synchronized boolean isTwitchConnected() {
        return twitchClient != null && 
               twitchClient.getChat().getConnectionState().name().equals("CONNECTED");
    }

    /**
     * Initializes the Twitch client and registers event listeners.
     * This method is called after dependency injection is complete.
     */
    @PostConstruct
    public void init() {
        if ("test-token".equals(accessToken) || "your_access_token".equals(accessToken)) {
            log.info("Skipping Twitch client initialization in test or default environment");
            return;
        }

        try {
            log.info("Checking for existing Twitch configuration...");
            List<TwitchConfig> configs = twitchConfigRepository.findAll();
            TwitchConfig config;
            if (configs.isEmpty()) {
                log.info("No Twitch configuration found in database. Creating initial configuration from properties.");
                TwitchConfig newConfig = new TwitchConfig();
                newConfig.setClientId(clientId);
                newConfig.setClientSecret(clientSecret);
                newConfig.setAccessToken(accessToken);
                newConfig.setRefreshToken(refreshToken);
                newConfig.setBotAccessToken(botAccessToken);
                newConfig.setBotRefreshToken(botRefreshToken);
                newConfig.setChannelName(channelName);
                newConfig.setRedeemTitle(redeemTitle);
                newConfig.setSongDelaySeconds(defaultSongDelaySeconds);
                config = twitchConfigRepository.save(newConfig);
            } else {
                config = configs.get(0);
            }

        currentAccessToken = config.getAccessToken();
        currentBotAccessToken = config.getBotAccessToken();
        currentChannelName = config.getChannelName();

        log.info("Attempting to initialize Twitch bot for channel: {}", currentChannelName);
        
        CredentialManager credentialManager = CredentialManagerBuilder.builder().build();
        TwitchIdentityProvider twitchIdentityProvider = new TwitchIdentityProvider(config.getClientId(), config.getClientSecret(), null);
        credentialManager.registerIdentityProvider(twitchIdentityProvider);

        OAuth2Credential streamerCredential = new OAuth2Credential(
                "twitch",
                config.getAccessToken(),
                config.getRefreshToken(),
                null,
                null,
                null,
                null
        );
        credentialManager.addCredential("streamer", streamerCredential);
        
        OAuth2Credential botCredential = (config.getBotAccessToken() != null && !config.getBotAccessToken().isBlank())
                ? new OAuth2Credential(
                        "twitch",
                        config.getBotAccessToken(),
                        config.getBotRefreshToken(),
                        null,
                        null,
                        null,
                        null
                )
                : streamerCredential;
        
        if (botCredential != streamerCredential) {
            credentialManager.addCredential("bot", botCredential);
        }

        log.info("Building TwitchClient...");
        TwitchClientBuilder builder = TwitchClientBuilder.builder()
                .withClientId(config.getClientId())
                .withClientSecret(config.getClientSecret())
                .withCredentialManager(credentialManager)
                .withEnableEventSocket(true)
                .withEnableHelix(true)
                .withDefaultAuthToken(streamerCredential)
                .withChatAccount(botCredential);

        if (useLocalCli) {
            log.info("Using local Twitch CLI mock server at {}", localCliUrl);
            builder.withHelixBaseUrl(localCliUrl);
        }

        twitchClient = builder.build();
        log.info("TwitchClient built successfully.");

        scheduler.scheduleAtFixedRate(() -> {
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

        log.info("Registering connection state listeners...");
        twitchClient.getEventManager().onEvent(EventSocketConnectionStateEvent.class, event -> {
            log.info("EventSub Socket Connection State Change: {} -> {}", event.getPreviousState(), event.getState());
            messagingTemplate.convertAndSend("/topic/connection-status", event.getState().name().equals("CONNECTED"));
        });

        log.info("Fetching channel ID for {}...", currentChannelName);
        broadcasterId = twitchClient.getHelix().getUsers(currentAccessToken, null, List.of(currentChannelName)).execute().getUsers().get(0).getId();
        log.info("Channel ID for {}: {}", currentChannelName, broadcasterId);

        log.info("Fetching bot user ID...");
        botUserId = twitchClient.getHelix().getUsers(currentBotAccessToken != null && !currentBotAccessToken.isBlank() ? currentBotAccessToken : currentAccessToken, null, null).execute().getUsers().get(0).getId();
        log.info("Bot User ID: {}", botUserId);

        // Check initial stream status
        log.info("Checking initial stream status...");
        StreamList streamList = twitchClient.getHelix().getStreams(currentAccessToken, null, null, 1, null, null, List.of(broadcasterId), null).execute();
        isStreamOnline = !streamList.getStreams().isEmpty();
        log.info("Initial stream status for {}: {}", currentChannelName, isStreamOnline ? "ONLINE" : "OFFLINE");

        if (isStreamOnline) {
            scheduleThumboGreeting();
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

        log.info("Twitch bot initialized for channel: {}", currentChannelName);
        } catch (Exception e) {
            log.error("Failed to initialize Twitch bot: {}", e.getMessage());
            if ("test-token".equals(accessToken)) {
                log.warn("Allowing initialization failure in test environment");
            } else {
                throw e;
            }
        }
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
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playRandomSong request for redeem: {}", redeemName);
            return;
        }
        List<Song> songs = songRepository.findByRedeemTitle(redeemName).stream()
                .filter(Song::isEnabled)
                .toList();
        if (songs.isEmpty()) {
            log.warn("No enabled songs found in the database for redeem: {}", redeemName);
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        song.incrementPlayCount();
        songRepository.save(song);
        songPlayRepository.save(new SongPlay(song, LocalDateTime.now(), redeemName));
        log.info("Queueing song: {} by {} for redeem: {}", song.getName(), song.getArtist(), redeemName);

        if (!isSongPlaying) {
            playSong(song);
        } else {
            songQueue.add(song);
            log.info("Song added to queue. Queue size: {}", songQueue.size());
            broadcastQueueSize();
        }
    }

    private void playSong(Song song) {
        isSongPlaying = true;
        currentlyPlayingSong = song;
        log.info("Playing song: {} by {}", song.getName(), song.getArtist());
        messagingTemplate.convertAndSend("/topic/play", song);
        broadcastCurrentSong();
    }

    /**
     * Handles the event when a song has finished playing.
     * Triggers the playback of the next song in the queue after a configured delay.
     */
    public synchronized void handleSongFinished() {
        if (!isStreamOnline) {
            log.info("Song finished, but stream is offline. Stopping playback and clearing queue.");
            isSongPlaying = false;
            currentlyPlayingSong = null;
            songQueue.clear();
            broadcastQueueSize();
            broadcastCurrentSong();
            return;
        }
        log.info("Song finished. Waiting {} seconds before next song...", currentSongDelaySeconds);
        isSongPlaying = false;

        if (songQueue.isEmpty()) {
            currentlyPlayingSong = null;
            broadcastCurrentSong();
        }

        scheduler.schedule(() -> {
            synchronized (this) {
                if (!songQueue.isEmpty()) {
                    playSong(songQueue.poll());
                    broadcastQueueSize();
                } else {
                    log.info("Queue is empty, no more songs to play.");
                    currentlyPlayingSong = null;
                    broadcastCurrentSong();
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
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playSongById request for song ID: {}", id);
            return;
        }
        songRepository.findById(id).ifPresent(song -> {
            log.info("Manually queueing song: {} by {} (incrementStats: {})", song.getName(), song.getArtist(), incrementStats);
            if (incrementStats) {
                song.incrementPlayCount();
                songRepository.save(song);
                songPlayRepository.save(new SongPlay(song, LocalDateTime.now(), "manual"));
            }
            if (!isSongPlaying) {
                playSong(song);
            } else {
                songQueue.add(song);
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
     * Retrieves a list of recent channel point redeems.
     * @return A list of {@link RedeemLog} objects.
     */
    public List<RedeemLog> getRecentRedeems() {
        synchronized (recentRedeems) {
            return List.copyOf(recentRedeems);
        }
    }

    /**
     * Queues a random song from all available songs in the repository.
     */
    public synchronized void playRandomSong() {
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playRandomSong request.");
            return;
        }
        List<Song> songs = songRepository.findByEnabled(true);
        if (songs.isEmpty()) {
            log.warn("No enabled songs found in the database");
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        song.incrementPlayCount();
        songRepository.save(song);
        songPlayRepository.save(new SongPlay(song, LocalDateTime.now(), "random"));
        log.info("Queueing song: {} by {}", song.getName(), song.getArtist());

        if (!isSongPlaying) {
            playSong(song);
        } else {
            songQueue.add(song);
            log.info("Song added to queue. Queue size: {}", songQueue.size());
            broadcastQueueSize();
        }
    }
}
