package dev.phatanon.service;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
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

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class TwitchBotService {

    private static final Logger log = LoggerFactory.getLogger(TwitchBotService.class);

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

    private final SimpMessagingTemplate messagingTemplate;
    private final SongRepository songRepository;
    private final TwitchConfigRepository twitchConfigRepository;
    private TwitchClient twitchClient;

    private String currentAccessToken;
    private String currentChannelName;
    private int currentSongDelaySeconds;

    private final java.util.Queue<Song> songQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    private boolean isSongPlaying = false;

    private final Random random = new Random();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    public TwitchBotService(SimpMessagingTemplate messagingTemplate, SongRepository songRepository, TwitchConfigRepository twitchConfigRepository) {
        this.messagingTemplate = messagingTemplate;
        this.songRepository = songRepository;
        this.twitchConfigRepository = twitchConfigRepository;
    }

    @PostConstruct
    public void init() {
        if ("test-token".equals(accessToken)) {
            log.info("Skipping Twitch client initialization in test environment");
            return;
        }

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

        this.currentAccessToken = config.getAccessToken();
        this.currentChannelName = config.getChannelName();
        this.currentSongDelaySeconds = config.getSongDelaySeconds();

        OAuth2Credential credential = new OAuth2Credential("twitch", currentAccessToken);

        twitchClient = TwitchClientBuilder.builder()
                .withClientId(config.getClientId())
                .withClientSecret(config.getClientSecret())
                .withEnablePubSub(true)
                .withChatAccount(credential)
                .build();

        String channelId = twitchClient.getHelix().getUsers(currentAccessToken, null, List.of(currentChannelName)).execute().getUsers().get(0).getId();

        twitchClient.getPubSub().listenForChannelPointsRedemptionEvents(credential, channelId);

        twitchClient.getEventManager().onEvent(RewardRedeemedEvent.class, event -> {
            String title = event.getRedemption().getReward().getTitle();
            String user = event.getRedemption().getUser().getDisplayName();
            log.info("Reward '{}' redeemed by {}", title, user);
            playRandomSong(title);
        });

        log.info("Twitch bot initialized for channel: {}", currentChannelName);
    }

    public synchronized void playRandomSong(String redeemName) {
        List<Song> songs = songRepository.findByRedeemName(redeemName);
        if (songs.isEmpty()) {
            log.warn("No songs found in the database for redeem: {}", redeemName);
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        log.info("Queueing song: {} by {} for redeem: {}", song.getName(), song.getArtist(), redeemName);

        if (!isSongPlaying) {
            playSong(song);
        } else {
            songQueue.add(song);
            log.info("Song added to queue. Queue size: {}", songQueue.size());
        }
    }

    private void playSong(Song song) {
        isSongPlaying = true;
        log.info("Playing song: {} by {}", song.getName(), song.getArtist());
        messagingTemplate.convertAndSend("/topic/play", song);
    }

    public synchronized void handleSongFinished() {
        log.info("Song finished. Waiting {} seconds before next song...", currentSongDelaySeconds);
        isSongPlaying = false;

        scheduler.schedule(() -> {
            synchronized (this) {
                if (!songQueue.isEmpty()) {
                    playSong(songQueue.poll());
                } else {
                    log.info("Queue is empty, no more songs to play.");
                }
            }
        }, currentSongDelaySeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    public synchronized void playSongById(Long id) {
        songRepository.findById(id).ifPresent(song -> {
            log.info("Manually queueing song: {} by {}", song.getName(), song.getArtist());
            if (!isSongPlaying) {
                playSong(song);
            } else {
                songQueue.add(song);
                log.info("Song added to queue. Queue size: {}", songQueue.size());
            }
        });
    }

    public synchronized void playRandomSong() {
        List<Song> songs = songRepository.findAll();
        if (songs.isEmpty()) {
            log.warn("No songs found in the database");
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        log.info("Queueing song: {} by {}", song.getName(), song.getArtist());

        if (!isSongPlaying) {
            playSong(song);
        } else {
            songQueue.add(song);
            log.info("Song added to queue. Queue size: {}", songQueue.size());
        }
    }
}
