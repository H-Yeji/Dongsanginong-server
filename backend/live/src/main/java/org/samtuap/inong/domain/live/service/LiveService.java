package org.samtuap.inong.domain.live.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.samtuap.inong.common.exception.BaseCustomException;
import org.samtuap.inong.domain.chat.dto.CouponDetailResponse;
import org.samtuap.inong.domain.chat.dto.KickMessage;
import org.samtuap.inong.domain.live.dto.*;
import org.samtuap.inong.domain.live.entity.Live;
import org.samtuap.inong.domain.live.repository.LiveRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.samtuap.inong.common.client.FarmFeign;
import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.Session;
import io.openvidu.java.client.SessionProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.samtuap.inong.common.exceptionType.LiveExceptionType.SESSION_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveService {

    private final LiveRepository liveRepository;
    private final FarmFeign farmFeign;
    private final OpenVidu openVidu;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LIVE_PARTICIPANTS_KEY_PREFIX = "live:participants:";
    private static final String LIVE_COUPON_KEY_PREFIX = "live:coupon:";

    /**
     * feign 요청용
     */
    @Transactional
    public List<FavoritesLiveListResponse> getFavoritesFarmLiveList(List<Long> favoriteFarmList) {
        // favoriteFarmList => 즐겨찾기 한 농장 id만 담겨있는 list
        List<Live> liveList = liveRepository.findByFarmIdInAndEndAtIsNull(favoriteFarmList);
        List<FavoritesLiveListResponse> list = new ArrayList<>();

        for (Live live: liveList) {
            int participantCount = getParticipantCount(live.getSessionId());
            FavoritesLiveListResponse dto = FavoritesLiveListResponse.from(live, participantCount);
            list.add(dto);
        }
        return list;
    }

    public Page<ActiveLiveListGetResponse> getActiveLiveList(Pageable pageable) {

        Page<Live> activeLiveList = liveRepository.findActiveLives(pageable);

        return activeLiveList.map(live -> {
            FarmResponse farmResponse = farmFeign.getFarmById(live.getFarmId());
            String farmName = farmResponse.farmName();
            int participantCount = getParticipantCount(live.getSessionId());
            return ActiveLiveListGetResponse.fromEntity(live, farmName, participantCount);
        });
    }

    /**
     * 라이브 스트리밍 시작 및 db에 기록 저장
     */
    @Transactional
    public LiveSessionResponse createLiveSession(Long sellerId, LiveSessionRequest request) throws Exception {
        // sellerId로 farmId 가져오기 => feign
        FarmDetailGetResponse farm = farmFeign.getFarmInfoWithSeller(sellerId);

        Live live = Live.builder()
                        .farmId(farm.id())
                        .ownerId(sellerId)
                        .title(request.title())
                        .liveImage(request.liveImage())
                        .category(request.category())
                        .build();
        liveRepository.save(live); // live 시작 > 먼저 디비에 저장

        // OpenVidu 세션 생성
        SessionProperties properties = SessionProperties.fromJson(new HashMap<>()).build();
        Session session = openVidu.createSession(properties);

        live.updateSessionId(session.getSessionId()); // sessionId를 라이브 시작하고 받아서 저장
        liveRepository.save(live);

        return LiveSessionResponse.fromEntity(request, live, farm, session);
    }

    public LiveSessionResponse getSessionIdByLiveId(Long id) {
        Live live = liveRepository.findByIdOrThrow(id);
        if (live.getSessionId() == null) {
            throw new BaseCustomException(SESSION_NOT_FOUND);
        }
        FarmResponse farm = farmFeign.getFarmById(live.getFarmId());
        log.info("{}로 session id 받자 : {}", id, live.getSessionId());
        return LiveSessionResponse.liveFromEntity(live, farm);  // 라이브의 세션 ID 반환
    }

    /**
     * session 종료
     */
    @Transactional
    public void leaveSession(String sessionId) {
        // 방송이 종료되었을 때, 해당 세션의 종료 시간을 기록 (endAt)
        Live live = liveRepository.findBySessionIdOrThrow(sessionId);
        live.updateEndAt(LocalDateTime.now());
        redisTemplate.expire("live:participants:" + sessionId, 1, TimeUnit.HOURS);
        redisTemplate.expire("kicked:users:" + sessionId, 1, TimeUnit.HOURS);
        liveRepository.save(live);
    }

    public int getParticipantCount(String sessionId) {
        String key = LIVE_PARTICIPANTS_KEY_PREFIX + sessionId;
        Object countObj = redisTemplate.opsForValue().get(key);
        if (countObj instanceof Number) {
            return ((Number) countObj).intValue();
        }
        return 0;
    }

    @Transactional
    public void saveCoupon(String sessionId, CouponDetailResponse coupon) {
        log.debug("Saving coupon: {}", coupon);
        String key = LIVE_COUPON_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, coupon);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        log.info("쿠폰 저장: sessionId = {}, coupon = {}", sessionId, coupon);
        messagingTemplate.convertAndSend("/topic/live/" + sessionId + "/coupon", coupon);
        log.debug("Sent coupon to WebSocket: /topic/live/{}/coupon", sessionId);
    }

    public CouponDetailResponse getCoupon(String sessionId) {
        String key = LIVE_COUPON_KEY_PREFIX + sessionId;
        Object couponObj = redisTemplate.opsForValue().get(key);
        if (couponObj instanceof CouponDetailResponse) {
            return (CouponDetailResponse) couponObj;
        }
        return null;
    }

    public void broadcastKickMessage(String sessionId, KickMessage kickMessage) {
        messagingTemplate.convertAndSend("/topic/live/" + sessionId + "/kick", kickMessage);
        log.info("KickMessage 브로드캐스트: sessionId = {}, kickMessage = {}", sessionId, kickMessage);
    }
}
