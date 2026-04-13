package dev.phatanon.service;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final SongRepository songRepository;
    private TwitchClient twitchClient;

    private final Random random = new Random();

    public TwitchBotService(SimpMessagingTemplate messagingTemplate, SongRepository songRepository) {
        this.messagingTemplate = messagingTemplate;
        this.songRepository = songRepository;
    }

    @PostConstruct
    public void init() {
        if ("test-token".equals(accessToken)) {
            log.info("Skipping Twitch client initialization in test environment");
            return;
        }
        OAuth2Credential credential = new OAuth2Credential("twitch", accessToken);

        twitchClient = TwitchClientBuilder.builder()
                .withClientId(clientId)
                .withClientSecret(clientSecret)
                .withEnablePubSub(true)
                .withChatAccount(credential)
                .build();

        String channelId = twitchClient.getHelix().getUsers(accessToken, null, List.of(channelName)).execute().getUsers().get(0).getId();

        twitchClient.getPubSub().listenForChannelPointsRedemptionEvents(credential, channelId);

        twitchClient.getEventManager().onEvent(RewardRedeemedEvent.class, event -> {
            String title = event.getRedemption().getReward().getTitle();
            log.info("Reward redeemed: {}", title);
            if (redeemTitle.equalsIgnoreCase(title)) {
                playRandomSong();
            }
        });
        
        log.info("Twitch bot initialized for channel: {}", channelName);
    }

    public void playRandomSong() {
        List<Song> songs = songRepository.findAll();
        if (songs.isEmpty()) {
            log.warn("No songs found in the database");
            return;
        }
        Song song = songs.get(random.nextInt(songs.size()));
        log.info("Playing song: {} by {}", song.getName(), song.getArtist());
        messagingTemplate.convertAndSend("/topic/play", song);
    }
}
