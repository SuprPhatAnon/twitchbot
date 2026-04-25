package dev.phatanon.service;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.eventsub.EventSubSubscription;
import com.github.twitch4j.eventsub.EventSubSubscriptionStatus;
import com.github.twitch4j.eventsub.EventSubTransport;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionType;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.events.StreamOfflineEvent;
import com.github.twitch4j.eventsub.events.ChannelChatMessageEvent;
import com.github.twitch4j.eventsub.events.ChannelCheerEvent;
import com.github.twitch4j.eventsub.events.ChannelFollowEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscribeEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscriptionGiftEvent;
import com.github.twitch4j.eventsub.events.ChannelSubscriptionMessageEvent;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
import com.github.twitch4j.helix.domain.EventSubSubscriptionList;
import com.github.twitch4j.helix.domain.UserList;
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

    private final Map<String, String> subscriptionStatuses = new java.util.concurrent.ConcurrentHashMap<>();

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
    private TwitchClient botTwitchClient;
    private CredentialManager credentialManager;

    private String currentAccessToken;
    private String currentBotAccessToken;
    private String currentAppAccessToken;
    private String currentChannelName;
    private String broadcasterId;
    private String botUserId;
    private String currentWebhookSecret;
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
    public void broadcastQueueSize() {
        messagingTemplate.convertAndSend("/topic/queue-size", getQueueSize());
        messagingTemplate.convertAndSend("/topic/queue", getQueue());
    }

    /**
     * Broadcasts the currently playing song to WebSocket subscribers on /topic/current-song.
     */
    public void broadcastCurrentSong() {
        messagingTemplate.convertAndSend("/topic/current-song", currentlyPlayingSong != null ? currentlyPlayingSong : "null");
    }

    /**
     * Broadcasts a message to refresh the songs list on the UI.
     */
    protected void broadcastSongsRefresh() {
        messagingTemplate.convertAndSend("/topic/songs", "refresh");
    }
    private boolean isStreamOnline = false;
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
                TwitchClient clientToUse = botTwitchClient != null ? botTwitchClient : twitchClient;
                ChatMessage chatMessage = ChatMessage.builder()
                        .broadcasterId(broadcasterId)
                        .senderId(botUserId)
                        .message(message)
                        .build();
                clientToUse.getHelix().sendChatMessage(token, chatMessage).execute();
                log.info("Sent chat message to {}: {}", currentChannelName, message);
            } catch (Exception e) {
                log.error("Failed to send chat message via Helix: {}", e.getMessage(), e);
                refreshTokens();
            }
        } else {
            log.warn("Cannot send chat message: Twitch connection is not ready.");
        }
    }

    /**
     * Checks if the streamer's EventSub client is connected.
     * @return true if connected, false otherwise
     */
    @Override
    public synchronized boolean isStreamerConnected() {
        return twitchClient != null;
    }

    /**
     * Checks if the bot's EventSub client is connected.
     * @return true if connected, false otherwise
     */
    @Override
    public synchronized boolean isBotConnected() {
        return botTwitchClient != null || (twitchClient != null && currentBotAccessToken == null);
    }

    public synchronized Map<String, String> getSubscriptionStatuses() {
        return new java.util.HashMap<>(subscriptionStatuses);
    }

    /**
     * Checks if both Twitch EventSub clients are connected (if configured).
     * @return true if connected, false otherwise
     */
    public synchronized boolean isTwitchConnected() {
        return twitchClient != null;
    }

    public synchronized String getChannelName() {
        return currentChannelName;
    }

    public synchronized String getBroadcasterId() {
        return broadcasterId;
    }

    public synchronized String getBotUserId() {
        return botUserId;
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
        log.info("Reconnection requested. Closing existing Twitch clients...");
        if (twitchClient != null) {
            try {
                twitchClient.close();
            } catch (Exception e) {
                log.error("Error closing streamer Twitch client: {}", e.getMessage());
            }
            twitchClient = null;
        }
        if (botTwitchClient != null) {
            try {
                botTwitchClient.close();
            } catch (Exception e) {
                log.error("Error closing bot Twitch client: {}", e.getMessage());
            }
            botTwitchClient = null;
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
            currentAppAccessToken = null; // Reset on re-init
            currentChannelName = config.getChannelName();
            currentWebhookSecret = config.getWebhookSecret();
            currentSongDelaySeconds = config.getSongDelaySeconds() > 0 ? config.getSongDelaySeconds() : defaultSongDelaySeconds;

            log.info("Attempting to initialize Twitch bot for channel: {}", currentChannelName);
            log.info("Client ID: {}", config.getClientId() != null ? config.getClientId().substring(0, Math.min(config.getClientId().length(), 4)) + "..." : "null");
            log.info("Has Streamer Access Token: {}", config.getAccessToken() != null && !config.getAccessToken().isBlank());
            log.info("Has Bot Access Token: {}", config.getBotAccessToken() != null && !config.getBotAccessToken().isBlank());
            
            this.credentialManager = CredentialManagerBuilder.builder().build();
            TwitchIdentityProvider identityProvider = new TwitchIdentityProvider(config.getClientId(), config.getClientSecret(), null);
            credentialManager.registerIdentityProvider(identityProvider);
            
            OAuth2Credential streamerCredential = new OAuth2Credential(
                    TwitchIdentityProvider.PROVIDER_NAME,
                    config.getAccessToken(),
                    config.getRefreshToken(),
                    null,
                    null,
                    config.getExpiresIn(),
                    null
            );
            log.info("Streamer credential scopes: {}", streamerCredential.getScopes());
            
            // Force refresh streamer token on startup
            try {
                log.info("Refreshing streamer token on startup...");
                identityProvider.refreshCredential(streamerCredential).ifPresent(newCredential -> {
                    if (!config.getAccessToken().equals(newCredential.getAccessToken())) {
                        log.info("Streamer token refreshed. Saving to database.");
                        log.info("Refreshed streamer credential scopes: {}", newCredential.getScopes());
                        config.setAccessToken(newCredential.getAccessToken());
                        config.setRefreshToken(newCredential.getRefreshToken());
                        config.setExpiresIn(newCredential.getExpiresIn());
                        twitchConfigRepository.save(config);
                        currentAccessToken = config.getAccessToken();
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to refresh streamer token on startup: {}", e.getMessage());
            }

            OAuth2Credential botCredential;
            if (config.getBotAccessToken() != null && !config.getBotAccessToken().isBlank()) {
                botCredential = new OAuth2Credential(
                        TwitchIdentityProvider.PROVIDER_NAME,
                        config.getBotAccessToken(),
                        config.getBotRefreshToken(),
                        null,
                        null,
                        config.getBotExpiresIn(),
                        null
                );
                log.info("Bot credential scopes: {}", botCredential.getScopes());

                // Force refresh bot token on startup
                try {
                    log.info("Refreshing bot token on startup...");
                    identityProvider.refreshCredential(botCredential).ifPresent(newCredential -> {
                        if (!config.getBotAccessToken().equals(newCredential.getAccessToken())) {
                            log.info("Bot token refreshed. Saving to database.");
                            log.info("Refreshed bot credential scopes: {}", newCredential.getScopes());
                            config.setBotAccessToken(newCredential.getAccessToken());
                            config.setBotRefreshToken(newCredential.getRefreshToken());
                            config.setBotExpiresIn(newCredential.getExpiresIn());
                            twitchConfigRepository.save(config);
                            currentBotAccessToken = config.getBotAccessToken();
                        }
                    });
                } catch (Exception e) {
                    log.warn("Failed to refresh bot token on startup: {}", e.getMessage());
                }
            } else {
                botCredential = streamerCredential;
            }
            
            log.info("Building TwitchClients...");
            TwitchClientBuilder streamerBuilder = TwitchClientBuilder.builder()
                    .withClientId(config.getClientId())
                    .withClientSecret(config.getClientSecret())
                    .withCredentialManager(credentialManager)
                    .withEnableEventSocket(false)
                    .withEnableHelix(true)
                    .withDefaultAuthToken(streamerCredential);

            if (twitchClient == null) {
                log.info("Creating new streamer TwitchClient instance...");
                twitchClient = streamerBuilder.build();
            } else {
                log.info("Streamer TwitchClient instance already exists, but re-init was called.");
            }

            if (config.getBotAccessToken() != null && !config.getBotAccessToken().isBlank()) {
                TwitchClientBuilder botBuilder = TwitchClientBuilder.builder()
                        .withClientId(config.getClientId())
                        .withClientSecret(config.getClientSecret())
                        .withCredentialManager(credentialManager)
                        .withEnableEventSocket(false)
                        .withEnableHelix(true)
                        .withDefaultAuthToken(botCredential);
                
                if (botTwitchClient == null) {
                    log.info("Creating new bot TwitchClient instance with user credentials...");
                    botTwitchClient = botBuilder.build();
                } else {
                    log.info("Bot TwitchClient instance already exists, but re-init was called.");
                }
            } else {
                log.info("No bot user credentials provided. Using Client Credentials flow for bot client.");
                TwitchClientBuilder botBuilder = TwitchClientBuilder.builder()
                        .withClientId(config.getClientId())
                        .withClientSecret(config.getClientSecret())
                        .withCredentialManager(credentialManager)
                        .withEnableEventSocket(false)
                        .withEnableHelix(true);
                
                if (botTwitchClient == null) {
                    log.info("Creating new bot TwitchClient instance with Client Credentials grant flow...");
                    botTwitchClient = botBuilder.build();
                } else {
                    log.info("Bot TwitchClient instance already exists, but re-init was called.");
                }
            }
            
            // Obtain App Access Token for webhook subscriptions and helix calls
            try {
                com.github.philippheuer.credentialmanager.domain.OAuth2Credential appToken = identityProvider.getAppAccessToken();
                if (appToken != null) {
                    currentAppAccessToken = appToken.getAccessToken();
                    log.info("Successfully obtained App Access Token for Webhook subscriptions and Helix interactions.");
                }
            } catch (Exception e) {
                log.warn("Could not obtain App Access Token: {}. Webhook subscriptions might fail if requiring App Token context.", e.getMessage());
            }
            
            log.info("TwitchClients built successfully.");

            registerEventListeners();

            // Refresh tokens if they are close to expiring
            long initialDelay = 1; // Start almost immediately to check current status
            tokenRefreshTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    List<TwitchConfig> currentConfigs = twitchConfigRepository.findAll();
                    if (!currentConfigs.isEmpty()) {
                        TwitchConfig currentConfig = currentConfigs.get(0);
                        boolean updated = false;

                        // Check streamer token
                        if (streamerCredential.getExpiresIn() != null && streamerCredential.getExpiresIn() < 600) {
                            log.info("[TOKEN] Streamer token expiring soon ({}s). Refreshing...", streamerCredential.getExpiresIn());
                            identityProvider.refreshCredential(streamerCredential).ifPresent(newCredential -> {
                                currentConfig.setAccessToken(newCredential.getAccessToken());
                                currentConfig.setRefreshToken(newCredential.getRefreshToken());
                                currentConfig.setExpiresIn(newCredential.getExpiresIn());
                                currentAccessToken = currentConfig.getAccessToken();
                            });
                            updated = true;
                        } else if (streamerCredential.getAccessToken() != null && !streamerCredential.getAccessToken().equals(currentConfig.getAccessToken())) {
                            log.info("[TOKEN] Streamer access token refreshed background. Saving new tokens to database.");
                            currentConfig.setAccessToken(streamerCredential.getAccessToken());
                            currentConfig.setRefreshToken(streamerCredential.getRefreshToken());
                            currentConfig.setExpiresIn(streamerCredential.getExpiresIn());
                            currentAccessToken = currentConfig.getAccessToken();
                            updated = true;
                        }

                        // Check bot token
                        if (botCredential != streamerCredential) {
                            if (botCredential.getExpiresIn() != null && botCredential.getExpiresIn() < 600) {
                                log.info("[TOKEN] Bot token expiring soon ({}s). Refreshing...", botCredential.getExpiresIn());
                                identityProvider.refreshCredential(botCredential).ifPresent(newCredential -> {
                                    currentConfig.setBotAccessToken(newCredential.getAccessToken());
                                    currentConfig.setBotRefreshToken(newCredential.getRefreshToken());
                                    currentConfig.setBotExpiresIn(newCredential.getExpiresIn());
                                    currentBotAccessToken = currentConfig.getBotAccessToken();
                                });
                                updated = true;
                            } else if (botCredential.getAccessToken() != null && !botCredential.getAccessToken().equals(currentConfig.getBotAccessToken())) {
                                log.info("[TOKEN] Bot access token refreshed background. Saving new tokens to database.");
                                currentConfig.setBotAccessToken(botCredential.getAccessToken());
                                currentConfig.setBotRefreshToken(botCredential.getRefreshToken());
                                currentConfig.setBotExpiresIn(botCredential.getExpiresIn());
                                currentBotAccessToken = currentConfig.getBotAccessToken();
                                updated = true;
                            }
                        }

                        if (updated) {
                            twitchConfigRepository.save(currentConfig);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error checking for token refreshes: {}", e.getMessage());
                }
            }, initialDelay, 5, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("Failed to initialize Twitch bot: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Registers explicit EventSub subscriptions for the broadcaster.
     */
    private void registerExplicitSubscriptions() {
        if (broadcasterId == null) {
            log.error("Cannot register explicit subscriptions: broadcasterId is null");
            return;
        }

        if (currentWebhookSecret == null || currentWebhookSecret.isBlank()) {
            log.warn("Webhook secret not configured. Skipping Webhook registration.");
            return;
        }

        String callbackUrl = redirectUriHost + "/api/twitch/callback";
        log.info("Registering EventSub Webhook subscriptions using App Access Token. Callback: {}", callbackUrl);

        com.github.twitch4j.eventsub.EventSubTransport transport = com.github.twitch4j.eventsub.EventSubTransport.builder()
            .method(com.github.twitch4j.eventsub.EventSubTransportMethod.WEBHOOK)
            .callback(callbackUrl)
            .secret(currentWebhookSecret)
            .build();

        // Bot Client Subscriptions
        try {
            log.info("Checking existing EventSub subscriptions at startup...");
            String helixToken = currentAppAccessToken != null ? currentAppAccessToken : currentAccessToken;
            
            EventSubSubscriptionList subsList = twitchClient.getHelix().getEventSubSubscriptions(helixToken, null, null, null, null, null).execute();
            
            java.util.Set<String> activeSubs = new java.util.HashSet<>();

            for (EventSubSubscription s : subsList.getSubscriptions()) {
                log.info(
                        "Found subscription: ID={}, Type={}, Status={}, Callback={}",
                        s.getId(),
                        s.getType()
                         .getName(),
                        s.getStatus(),
                        s.getTransport()
                         .getCallback()
                        );

                EventSubSubscriptionStatus status = s.getStatus();
                EventSubTransport sTransport = s.getTransport();
                String sCallback = sTransport.getCallback();
                
                if (EventSubSubscriptionStatus.ENABLED.equals(status) && callbackUrl.equals(sCallback)) {
                    String type = s.getType().getName();
                    activeSubs.add(type);
                }
            }

            log.info("Found {} active subscriptions for this callback.", activeSubs.size());

            subscribeIfMissing(SubscriptionTypes.CHANNEL_POINTS_CUSTOM_REWARD_REDEMPTION_ADD, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);
            subscribeIfMissing(SubscriptionTypes.CHANNEL_CHEER, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);
            subscribeIfMissing(SubscriptionTypes.CHANNEL_SUBSCRIBE, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);
            subscribeIfMissing(SubscriptionTypes.CHANNEL_SUBSCRIPTION_GIFT, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);
            subscribeIfMissing(SubscriptionTypes.CHANNEL_SUBSCRIPTION_MESSAGE, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);
            subscribeIfMissing(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);
            subscribeIfMissing(SubscriptionTypes.STREAM_OFFLINE, b -> b.broadcasterUserId(broadcasterId).build(), transport, activeSubs);

            if (botUserId != null) {
                subscribeIfMissing(SubscriptionTypes.CHANNEL_CHAT_MESSAGE, b -> b.broadcasterUserId(broadcasterId).userId(botUserId).build(), transport, activeSubs);
            }
        } catch (Exception e) {
            log.error("Error registering explicit subscriptions: {}", e.getMessage());
        }
    }

    private <C extends com.github.twitch4j.eventsub.condition.EventSubCondition, B> void subscribeIfMissing(com.github.twitch4j.eventsub.subscriptions.SubscriptionType<C, B, ?> type, java.util.function.Function<B, C> conditionBuilder, com.github.twitch4j.eventsub.EventSubTransport transport, java.util.Set<String> activeSubs) {
        String typeName = type.getName();
        if (activeSubs.contains(typeName)) {
            log.info("Subscription already exists and is enabled: {}", typeName);
            subscriptionStatuses.put(typeName, "ENABLED");
        } else {
            log.info("Requesting subscription: {}", typeName);
            try {
                String helixToken = currentAppAccessToken != null ? currentAppAccessToken : currentAccessToken;
                twitchClient.getHelix().createEventSubSubscription(helixToken, type.prepareSubscription(conditionBuilder, transport)).execute();
                subscriptionStatuses.put(typeName, "REQUESTED");
            } catch (Exception e) {
                log.error("Failed to create subscription {}: {}", typeName, e.getMessage());
                subscriptionStatuses.put(typeName, "FAILED: " + e.getMessage());
            }
        }
    }

    /**
     * Registers EventSub event listeners with the Twitch client.
     */
    private void registerEventListeners() {
        try {
            log.info("Fetching channel ID for {} using Helix...", currentChannelName);
            String helixToken = currentAppAccessToken != null ? currentAppAccessToken : currentAccessToken;
            UserList userList = twitchClient.getHelix().getUsers(helixToken, null, List.of(currentChannelName)).execute();
            if (userList.getUsers().isEmpty()) {
                log.error("Could not find Twitch user for channel name: {}", currentChannelName);
                return;
            }
            broadcasterId = userList.getUsers().get(0).getId();
            log.info("Channel ID for {}: {}", currentChannelName, broadcasterId);

            log.info("Fetching bot user ID using Helix...");
            try {
                String botToken = currentBotAccessToken != null && !currentBotAccessToken.isBlank() ? currentBotAccessToken : currentAccessToken;
                UserList botUserList = twitchClient.getHelix().getUsers(botToken, null, null).execute();
                if (botUserList.getUsers().isEmpty()) {
                    log.error("Could not find Twitch user for bot token.");
                } else {
                    botUserId = botUserList.getUsers().get(0).getId();
                    log.info("Bot User ID: {}", botUserId);
                }
            } catch (Exception e) {
                log.warn("Could not fetch bot user ID: {}. Bot-specific features might be limited.", e.getMessage());
            }

            log.info("Checking initial stream status for broadcasterId {}...", broadcasterId);
            StreamList streamList = twitchClient.getHelix().getStreams(helixToken, null, null, 1, null, null, List.of(broadcasterId), null).execute();
            isStreamOnline = !streamList.getStreams().isEmpty();
            log.info("Initial stream status for {}: {}", currentChannelName, isStreamOnline ? "ONLINE" : "OFFLINE");

            if (isStreamOnline) {
                scheduleThumboGreeting();
            }

            registerExplicitSubscriptions();
        } catch (Exception e) {
            log.error("Error during Helix API calls in registerEventListeners: {}", e.getMessage(), e);
        }

        twitchClient.getEventManager().onEvent(CustomRewardRedemptionAddEvent.class, event -> {
            String title = event.getReward().getTitle();
            String user = event.getUserName();
            log.info("[EVENT] Reward '{}' redeemed by {}", title, user);

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
            log.info("[EVENT] {} cheered {} bits", user, bits);

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
            log.info("[EVENT] {} followed the channel", user);

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
            log.info("[EVENT] {} subscribed at Tier {}", user, tier);

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
            log.info("[EVENT] {} {}", user, logMsg);

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
            log.info("[EVENT] {} resubscribed for {} months (Tier {})", user, months, tier);

            RedeemLog redeemLog = new RedeemLog(user, "Resubscribed (" + months + " months, Tier " + tier + ")", LocalDateTime.now());
            synchronized (recentRedeems) {
                if (recentRedeems.size() >= MAX_REDEEM_LOGS) {
                    recentRedeems.remove(0);
                }
                recentRedeems.add(redeemLog);
            }
            messagingTemplate.convertAndSend("/topic/redeems", redeemLog);
        });

        twitchClient.getEventManager().onEvent(StreamOnlineEvent.class, event -> {
            log.info("[EVENT] Stream started for channel: {}", event.getBroadcasterUserName());
            isStreamOnline = true;
            messagingTemplate.convertAndSend("/topic/stream-status", true);
            scheduleThumboGreeting();
        });

        twitchClient.getEventManager().onEvent(StreamOfflineEvent.class, event -> {
            log.info("[EVENT] Stream ended for channel: {}", event.getBroadcasterUserName());
            isStreamOnline = false;
            messagingTemplate.convertAndSend("/topic/stream-status", false);
            greetingSentThisSession.set(false);
            synchronized (this) {
                songQueue.clear();
                log.info("Stream ended. Song queue cleared.");
                broadcastQueueSize();
            }
        });

        twitchClient.getEventManager().onEvent(ChannelChatMessageEvent.class, event -> {
            //log.debug("[EVENT] [CHAT] Streamer Client received message: {}", event.getMessage().getText());
            ChatMessageContext context = ChatMessageContext.builder()
                    .message(event.getMessage().getText())
                    .senderName(event.getChatterUserName())
                    .channelName(event.getBroadcasterUserName())
                    .source("Streamer")
                    .build();
            chatMessageService.processMessage(context);
        });

        if (botTwitchClient != null) {
            botTwitchClient.getEventManager().onEvent(ChannelChatMessageEvent.class, event -> {
            //    log.debug("[EVENT] [CHAT] Bot Client received message: {}", event.getMessage().getText());
                ChatMessageContext context = ChatMessageContext.builder()
                        .message(event.getMessage().getText())
                        .senderName(event.getChatterUserName())
                        .channelName(event.getBroadcasterUserName())
                        .source("Bot")
                        .build();
                chatMessageService.processMessage(context);
            });
        }

        log.info("Twitch bot initialized for channel: {}", currentChannelName);
    }

    /**
     * Checks if the stream is currently online.
     * Stream status is updated via {@link StreamOnlineEvent} and {@link StreamOfflineEvent}.
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
                    TwitchClient clientToUse = botTwitchClient != null ? botTwitchClient : twitchClient;
                    ChatMessage chatMessage = ChatMessage.builder()
                            .broadcasterId(broadcasterId)
                            .senderId(botUserId)
                            .message("Hi Thumbo <3")
                            .build();
                    clientToUse.getHelix().sendChatMessage(token, chatMessage).execute();
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
        if (!isSongPlaying && currentlyPlayingSong == null && songQueue.isEmpty()) {
            log.info("handleSongFinished called but nothing is playing and queue is empty. Ignoring.");
            return;
        }

        log.info("Song finished. Waiting {} seconds before next song...", currentSongDelaySeconds);
        isSongPlaying = false;
        // Don't set currentlyPlayingSong to null yet, to keep it on the UI during delay
        // broadcastCurrentSong(); // Also don't broadcast null yet

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
        /*
        if (!isStreamOnline) {
            log.info("Stream is offline. Ignoring playSongById request for song ID: {}", id);
            return;
        }
        */
        songRepository.findById(id).ifPresent(song -> {
            if (!song.isEnabled()) {
                log.warn("Attempted to play disabled song ID: {}. Ignoring.", id);
                return;
            }
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
            String token = currentAppAccessToken != null ? currentAppAccessToken : currentAccessToken;
            UserList userList = twitchClient.getHelix().getUsers(token, null, List.of(currentChannelName)).execute();
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
