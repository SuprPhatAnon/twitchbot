package dev.phatanon.service;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.common.events.domain.EventChannel;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.pubsub.events.ChannelBitsEvent;
import com.github.twitch4j.pubsub.events.PubSubConnectionStateEvent;
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import dev.phatanon.ConnectionStartupLogger;
import dev.phatanon.entity.Song;
import dev.phatanon.entity.TwitchConfig;
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
 * It handles Twitch IRC connection, PubSub events for rewards, and maintains a song queue.
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
    private final TwitchConfigRepository twitchConfigRepository;
    private TwitchClient twitchClient;

    private String currentAccessToken;
    private String currentChannelName;
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

    private void broadcastQueueSize() {
        messagingTemplate.convertAndSend("/topic/queue-size", getQueueSize());
    }

    private void broadcastCurrentSong() {
        messagingTemplate.convertAndSend("/topic/current-song", currentlyPlayingSong);
    }
    private boolean isStreamOnline = false;
    private final AtomicBoolean greetingSentThisSession = new AtomicBoolean(false);

    private final Random random = new Random();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    public TwitchBotService(SimpMessagingTemplate messagingTemplate, SongRepository songRepository, TwitchConfigRepository twitchConfigRepository) {
        this.messagingTemplate = messagingTemplate;
        this.songRepository = songRepository;
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
        if ("test-token".equals(accessToken) && !useLocalCli) {
            log.info("Skipping Twitch client initialization in test environment without local CLI");
            return;
        }

        try {
            TwitchConfig config = twitchConfigRepository.findAll().stream().findFirst().orElseGet(() -> {
            log.info("No Twitch configuration found in database. Creating initial configuration from properties.");
            TwitchConfig newConfig = new TwitchConfig();
            newConfig.setClientId(clientId);
            newConfig.setClientSecret(clientSecret);
            newConfig.setAccessToken(accessToken);
            newConfig.setChannelName(channelName);
            newConfig.setRedeemTitle(redeemTitle);
            newConfig.setSongDelaySeconds(defaultSongDelaySeconds);
            return twitchConfigRepository.save(newConfig);
        });

        currentAccessToken = config.getAccessToken();
        currentChannelName = config.getChannelName();
        currentSongDelaySeconds = config.getSongDelaySeconds();

        log.info("Attempting to initialize Twitch bot for channel: {}", currentChannelName);
        OAuth2Credential credential = new OAuth2Credential("twitch", currentAccessToken);

        log.info("Building TwitchClient...");
        TwitchClientBuilder builder = TwitchClientBuilder.builder()
                .withClientId(config.getClientId())
                .withClientSecret(config.getClientSecret())
                .withEnablePubSub(!useLocalCli)
                .withEnableHelix(true)
                .withChatAccount(credential);

        if (useLocalCli) {
            log.info("Using local Twitch CLI mock server at {}", localCliUrl);
            builder.withHelixBaseUrl(localCliUrl);
        }

        twitchClient = builder.build();
        log.info("TwitchClient built successfully.");

        log.info("Registering connection state listeners...");
        twitchClient.getEventManager().onEvent(ChatConnectionStateEvent.class, event -> {
            log.info("IRC Connection State Change: {} -> {}", event.getPreviousState(), event.getState());
            messagingTemplate.convertAndSend("/topic/connection-status", event.getState().name().equals("CONNECTED"));
        });

        twitchClient.getEventManager().onEvent(PubSubConnectionStateEvent.class, event -> {
            log.info("PubSub Connection State Change: {} -> {}", event.getPreviousState(), event.getState());
        });

        twitchClient.getEventManager().onEvent(EventSocketConnectionStateEvent.class, event -> {
            log.info("EventSub Socket Connection State Change: {} -> {}", event.getPreviousState(), event.getState());
        });

        log.info("Fetching channel ID for {}...", currentChannelName);
        String channelId = twitchClient.getHelix().getUsers(currentAccessToken, null, List.of(currentChannelName)).execute().getUsers().get(0).getId();
        log.info("Channel ID for {}: {}", currentChannelName, channelId);

        // Check initial stream status
        log.info("Checking initial stream status...");
        StreamList streamList = twitchClient.getHelix().getStreams(currentAccessToken, null, null, 1, null, null, List.of(channelId), null).execute();
        isStreamOnline = !streamList.getStreams().isEmpty();
        log.info("Initial stream status for {}: {}", currentChannelName, isStreamOnline ? "ONLINE" : "OFFLINE");

        if (isStreamOnline) {
            scheduleThumboGreeting();
        }

        if (!useLocalCli) {
            twitchClient.getPubSub().listenForChannelPointsRedemptionEvents(credential, channelId);
            twitchClient.getPubSub().listenForCheerEvents(credential, channelId);
        }

        twitchClient.getEventManager().onEvent(RewardRedeemedEvent.class, event -> {
            String title = event.getRedemption().getReward().getTitle();
            String user = event.getRedemption().getUser().getDisplayName();
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

        twitchClient.getEventManager().onEvent(ChannelBitsEvent.class, event -> {
            String user = event.getData().getUserName();
            Integer bits = event.getData().getBitsUsed();
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
                twitchClient.getChat().sendMessage(currentChannelName, "Hi Thumbo <3");
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
        List<Song> songs = songRepository.findByRedeemName(redeemName).stream()
                .filter(Song::isEnabled)
                .toList();
        if (songs.isEmpty()) {
            log.warn("No enabled songs found in the database for redeem: {}", redeemName);
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
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
        currentlyPlayingSong = null;
        broadcastCurrentSong();

        scheduler.schedule(() -> {
            synchronized (this) {
                if (!songQueue.isEmpty()) {
                    playSong(songQueue.poll());
                    broadcastQueueSize();
                } else {
                    log.info("Queue is empty, no more songs to play.");
                }
            }
        }, currentSongDelaySeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Triggers the playback of a specific song by its ID.
     * This is typically used by the Admin UI for manual testing.
     * @param id The ID of the song to play.
     */
    public synchronized void playSongById(Long id) {
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playSongById request for song ID: {}", id);
            return;
        }
        songRepository.findById(id).ifPresent(song -> {
            log.info("Manually queueing song: {} by {}", song.getName(), song.getArtist());
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
