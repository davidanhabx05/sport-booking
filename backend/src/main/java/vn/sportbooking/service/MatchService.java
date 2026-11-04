package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Requests.CreateMatchRequest;
import vn.sportbooking.dto.Responses.*;
import vn.sportbooking.entity.*;
import vn.sportbooking.entity.Enums.MatchStatus;
import vn.sportbooking.entity.Enums.ParticipantStatus;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Ghép kèo: người đã có đơn đặt sân CONFIRMED đăng tin tìm thêm người;
 * người khác gửi yêu cầu tham gia, chủ kèo duyệt; đủ người thì kèo chuyển FULL.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPostRepository matchPostRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<MatchDto> listUpcoming(Long viewerId, Enums.SportType sport, Enums.SkillLevel level, String district) {
        String d = (district == null || district.isBlank()) ? null : district;
        return matchPostRepository.findUpcoming(LocalDate.now(), sport, level, d).stream()
                .filter(m -> LocalDateTime.of(m.getBooking().getBookingDate(), m.getBooking().getStartTime())
                        .isAfter(LocalDateTime.now()))
                .map(m -> toDto(m, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchDto> mine(Long userId) {
        return matchPostRepository.findMine(userId).stream().map(m -> toDto(m, userId)).toList();
    }

    @Transactional(readOnly = true)
    public MatchDto get(Long id, Long viewerId) {
        return toDto(getMatch(id), viewerId);
    }

    @Transactional
    public MatchDto create(Long userId, CreateMatchRequest req) {
        Booking b = bookingRepository.findById(req.bookingId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn đặt sân"));
        if (!b.getUser().getId().equals(userId)) throw ApiException.forbidden("Chỉ người đặt sân mới được tạo kèo");
        if (b.getStatus() != Enums.BookingStatus.CONFIRMED) {
            throw ApiException.badRequest("Chỉ tạo kèo cho đơn đã thanh toán thành công");
        }
        if (b.startDateTime().isBefore(LocalDateTime.now())) throw ApiException.badRequest("Giờ chơi đã qua");
        if (matchPostRepository.existsByBookingId(b.getId())) throw ApiException.conflict("Đơn này đã có bài ghép kèo");

        MatchPost m = MatchPost.builder()
                .host(b.getUser()).booking(b)
                .title(req.title().trim()).description(req.description())
                .skillLevel(req.skillLevel()).slotsNeeded(req.slotsNeeded())
                .pricePerPerson(req.pricePerPerson())
                .status(MatchStatus.OPEN).build();
        matchPostRepository.save(m);
        return toDto(m, userId);
    }

    @Transactional
    public MatchDto join(Long userId, Long matchId, String message) {
        MatchPost m = getMatch(matchId);
        if (m.getHost().getId().equals(userId)) throw ApiException.badRequest("Bạn là chủ kèo");
        if (m.getStatus() != MatchStatus.OPEN) throw ApiException.badRequest("Kèo đã đủ người hoặc đã đóng");
        if (m.getBooking().startDateTime().isBefore(LocalDateTime.now())) throw ApiException.badRequest("Kèo đã diễn ra");

        Optional<MatchParticipant> existing = m.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(userId)).findFirst();
        if (existing.isPresent()) {
            MatchParticipant p = existing.get();
            if (p.getStatus() == ParticipantStatus.REJECTED) throw ApiException.badRequest("Yêu cầu của bạn đã bị từ chối");
            throw ApiException.conflict("Bạn đã gửi yêu cầu tham gia kèo này");
        }
        User joiner = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        m.getParticipants().add(MatchParticipant.builder()
                .matchPost(m).user(joiner)
                .status(ParticipantStatus.PENDING).message(message).build());
        matchPostRepository.flush();
        notificationService.notify(m.getHost().getId(), Enums.NotificationType.MATCH_REQUEST,
                joiner.getFullName() + " xin vào kèo của bạn",
                m.getTitle() + (message == null || message.isBlank() ? "" : ": “" + message + "”"), "/matches");
        return toDto(m, userId);
    }

    @Transactional
    public MatchDto leave(Long userId, Long matchId) {
        MatchPost m = getMatch(matchId);
        boolean removed = m.getParticipants().removeIf(p -> p.getUser().getId().equals(userId));
        if (!removed) throw ApiException.badRequest("Bạn chưa tham gia kèo này");
        if (m.getStatus() == MatchStatus.FULL) m.setStatus(MatchStatus.OPEN);
        return toDto(m, userId);
    }

    @Transactional
    public MatchDto respond(Long hostId, Long matchId, Long participantId, boolean accept) {
        MatchPost m = getMatch(matchId);
        if (!m.getHost().getId().equals(hostId)) throw ApiException.forbidden("Chỉ chủ kèo mới được duyệt");
        MatchParticipant p = m.getParticipants().stream().filter(x -> x.getId().equals(participantId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu tham gia"));
        if (accept) {
            if (p.getStatus() != ParticipantStatus.ACCEPTED && m.acceptedCount() >= m.getSlotsNeeded()) {
                throw ApiException.badRequest("Kèo đã đủ người");
            }
            p.setStatus(ParticipantStatus.ACCEPTED);
        } else {
            p.setStatus(ParticipantStatus.REJECTED);
        }
        if (m.getStatus() != MatchStatus.CLOSED) {
            m.setStatus(m.acceptedCount() >= m.getSlotsNeeded() ? MatchStatus.FULL : MatchStatus.OPEN);
        }
        Booking b = m.getBooking();
        String when = b.getCourt().getVenue().getName() + ", " + b.getBookingDate() + " lúc " + b.getStartTime();
        notificationService.notify(p.getUser().getId(),
                accept ? Enums.NotificationType.MATCH_ACCEPTED : Enums.NotificationType.MATCH_REJECTED,
                accept ? "Bạn đã được nhận vào kèo" : "Kèo chưa phù hợp lần này",
                accept ? m.getTitle() + ". " + when + ". Liên hệ chủ kèo: " + m.getHost().getPhone()
                       : m.getTitle() + ". Bạn có thể tìm kèo khác cùng trình độ.",
                "/matches");
        return toDto(m, hostId);
    }

    @Transactional
    public MatchDto close(Long hostId, Long matchId) {
        MatchPost m = getMatch(matchId);
        if (!m.getHost().getId().equals(hostId)) throw ApiException.forbidden("Chỉ chủ kèo mới được đóng kèo");
        m.setStatus(MatchStatus.CLOSED);
        return toDto(m, hostId);
    }

    private MatchPost getMatch(Long id) {
        return matchPostRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy kèo"));
    }

    /**
     * Quyền riêng tư: số điện thoại chủ kèo chỉ hiện cho người đã được duyệt;
     * danh sách người xin tham gia (kèm SĐT) chỉ hiện cho chủ kèo.
     */
    private MatchDto toDto(MatchPost m, Long viewerId) {
        Booking b = m.getBooking();
        Court c = b.getCourt();
        Venue v = c.getVenue();
        boolean isHost = viewerId != null && m.getHost().getId().equals(viewerId);
        Optional<MatchParticipant> mine = viewerId == null ? Optional.empty()
                : m.getParticipants().stream().filter(p -> p.getUser().getId().equals(viewerId)).findFirst();
        boolean accepted = mine.map(p -> p.getStatus() == ParticipantStatus.ACCEPTED).orElse(false);

        List<ParticipantDto> participants = m.getParticipants().stream()
                .filter(p -> isHost || p.getStatus() == ParticipantStatus.ACCEPTED)
                .map(p -> new ParticipantDto(p.getId(), PublicUserDto.of(p.getUser()), p.getStatus(),
                        isHost ? p.getMessage() : null,
                        isHost ? p.getUser().getPhone() : null,
                        p.getCreatedAt()))
                .toList();

        return new MatchDto(m.getId(), m.getTitle(), m.getDescription(), m.getSkillLevel(), m.getSlotsNeeded(),
                m.acceptedCount(), m.getPricePerPerson(), m.getStatus(),
                PublicUserDto.of(m.getHost()), (isHost || accepted) ? m.getHost().getPhone() : null,
                b.getId(), b.getBookingDate(), b.getStartTime(), b.getEndTime(),
                c.getSportType(), c.getName(), v.getId(), v.getName(), v.getAddress(), v.getDistrict(),
                participants, mine.map(p -> p.getStatus().name()).orElse(null), isHost, m.getCreatedAt());
    }
}
