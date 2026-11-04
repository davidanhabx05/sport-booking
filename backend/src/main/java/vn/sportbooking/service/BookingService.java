package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Requests.CreateBookingRequest;
import vn.sportbooking.dto.Requests.RecurringBookingRequest;
import vn.sportbooking.dto.Responses.BookingDto;
import vn.sportbooking.entity.*;
import vn.sportbooking.entity.Enums.BookingStatus;
import vn.sportbooking.entity.Enums.NotificationType;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.*;
import vn.sportbooking.security.UserPrincipal;

import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final MatchPostRepository matchPostRepository;
    private final PricingService pricingService;
    private final VenueService venueService;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Value("${app.booking.hold-minutes:10}")
    private int holdMinutes;
    @Value("${app.booking.max-hours:4}")
    private int maxHours;
    @Value("${app.booking.max-days-ahead:30}")
    private int maxDaysAhead;
    @Value("${app.booking.cancel-before-hours:2}")
    private int cancelBeforeHours;
    @Value("${app.booking.max-pending-per-user:3}")
    private int maxPendingPerUser;
    @Value("${app.booking.recurring-discount-min-weeks:4}")
    private int discountMinWeeks;
    @Value("${app.booking.recurring-discount-percent:10}")
    private int discountPercent;

    /**
     * Tạo đơn đặt sân ở trạng thái PENDING_PAYMENT và giữ chỗ trong {@code holdMinutes} phút.
     *
     * Chống đặt trùng (double-booking):
     *  1. Khoá dòng Court bằng SELECT ... FOR UPDATE → các request cùng sân phải chờ nhau.
     *  2. Trong khi giữ khoá, đếm các đơn còn hiệu lực bị chồng giờ.
     *  3. Nếu không trùng thì lưu đơn; khoá được nhả khi transaction commit.
     */
    @Transactional
    public BookingDto create(Long userId, CreateBookingRequest req) {
        LocalDateTime now = LocalDateTime.now();
        validateTimeRange(req.date(), req.startTime(), req.endTime(), now);
        Court court = lockBookableCourt(req.courtId(), req.startTime(), req.endTime());
        checkPendingLimit(userId, now);

        long overlaps = bookingRepository.countOverlapping(court.getId(), req.date(), req.startTime(), req.endTime(), now, null);
        if (overlaps > 0) {
            throw ApiException.conflict("Khung giờ này vừa có người đặt, vui lòng chọn giờ khác");
        }

        Booking booking = newBooking(userId, court, req.date(), req.startTime(), req.endTime(),
                pricingService.totalPrice(court, req.startTime(), req.endTime()), now, null, req.note());
        bookingRepository.save(booking);
        log.info("Booking {} created: court={} {} {}-{}", booking.getCode(), court.getId(), req.date(), req.startTime(), req.endTime());
        return toDto(booking);
    }

    /**
     * Đặt định kỳ: cùng sân, cùng khung giờ, lặp lại mỗi tuần trong N tuần (2–8).
     * Tất cả buổi được tạo trong MỘT transaction: chỉ cần một tuần bị trùng là không tạo buổi nào
     * (all-or-nothing), và báo cho khách biết những ngày bị trùng để chọn giờ khác.
     * Đặt từ {@code discountMinWeeks} tuần trở lên được giảm {@code discountPercent}%.
     */
    @Transactional
    public List<BookingDto> createRecurring(Long userId, RecurringBookingRequest req) {
        LocalDateTime now = LocalDateTime.now();
        validateTimeRange(req.startDate(), req.startTime(), req.endTime(), now);
        Court court = lockBookableCourt(req.courtId(), req.startTime(), req.endTime());
        checkPendingLimit(userId, now);

        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < req.weeks(); i++) dates.add(req.startDate().plusWeeks(i));

        List<LocalDate> conflicts = dates.stream()
                .filter(d -> bookingRepository.countOverlapping(court.getId(), d, req.startTime(), req.endTime(), now, null) > 0)
                .toList();
        if (!conflicts.isEmpty()) {
            throw ApiException.conflict("Những ngày sau đã có người đặt khung giờ này: "
                    + conflicts.stream().map(d -> d.format(SHORT_DATE)).collect(Collectors.joining(", "))
                    + ". Vui lòng chọn khung giờ khác.");
        }

        int discount = req.weeks() >= discountMinWeeks ? discountPercent : 0;
        long basePrice = pricingService.totalPrice(court, req.startTime(), req.endTime());
        long price = discountedPrice(basePrice, discount);
        String groupCode = generateCode(req.startDate()).replaceFirst("^SB", "GR");

        List<Booking> created = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            String note = "Định kỳ buổi " + (i + 1) + "/" + dates.size()
                    + (discount > 0 ? " (giảm " + discount + "%)" : "")
                    + (req.note() == null || req.note().isBlank() ? "" : ". " + req.note());
            Booking b = newBooking(userId, court, dates.get(i), req.startTime(), req.endTime(), price, now, groupCode, note);
            bookingRepository.save(b);
            created.add(b);
        }
        log.info("Recurring group {} created: {} weeks, court={}", groupCode, dates.size(), court.getId());
        return created.stream().map(this::toDto).toList();
    }

    /** Giảm giá theo %, làm tròn tới 1.000đ. */
    static long discountedPrice(long base, int percent) {
        if (percent <= 0) return base;
        return Math.round(base * (100 - percent) / 100.0 / 1000.0) * 1000;
    }

    @Transactional(readOnly = true)
    public List<BookingDto> myBookings(Long userId) {
        return bookingRepository.findByUserWithCourt(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BookingDto get(UserPrincipal me, Long id) {
        Booking b = getBooking(id);
        checkCanView(me, b);
        return toDto(b);
    }

    /** Khách huỷ đơn của chính mình. */
    @Transactional
    public BookingDto cancelByCustomer(Long userId, Long id, String reason) {
        Booking b = getBooking(id);
        if (!b.getUser().getId().equals(userId)) throw ApiException.forbidden("Đây không phải đơn của bạn");

        if (b.getStatus() == BookingStatus.CONFIRMED) {
            if (b.startDateTime().minusHours(cancelBeforeHours).isBefore(LocalDateTime.now())) {
                throw ApiException.badRequest("Chỉ được huỷ trước giờ chơi tối thiểu " + cancelBeforeHours + " tiếng");
            }
        } else if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("Không thể huỷ đơn ở trạng thái hiện tại");
        }
        cancel(b, reason == null || reason.isBlank() ? "Khách huỷ" : reason);
        return toDto(b);
    }

    // ===================== OWNER / ADMIN =====================

    @Transactional(readOnly = true)
    public List<BookingDto> adminList(UserPrincipal me, Long venueId, LocalDate from, LocalDate to, BookingStatus status) {
        List<Long> ids = venueId != null
                ? List.of(venueService.getManagedVenue(me, venueId).getId())
                : venueService.managedVenueIds(me);
        if (ids.isEmpty()) return List.of();
        LocalDate f = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate t = to != null ? to : LocalDate.now().plusDays(30);
        return bookingRepository.findForAdmin(ids, f, t, status).stream().map(this::toDto).toList();
    }

    @Transactional
    public BookingDto adminCheckIn(UserPrincipal me, Long id) {
        Booking b = getBooking(id);
        venueService.checkManage(me, b.getCourt().getVenue());
        if (b.getStatus() != BookingStatus.CONFIRMED) throw ApiException.badRequest("Chỉ check-in được đơn đã xác nhận");
        b.setStatus(BookingStatus.COMPLETED);
        return toDto(b);
    }

    @Transactional
    public BookingDto adminCancel(UserPrincipal me, Long id, String reason) {
        Booking b = getBooking(id);
        venueService.checkManage(me, b.getCourt().getVenue());
        if (b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("Không thể huỷ đơn ở trạng thái hiện tại");
        }
        boolean wasPaid = b.getStatus() == BookingStatus.CONFIRMED;
        String why = reason == null || reason.isBlank() ? "Chủ sân huỷ" : reason;
        cancel(b, why);

        User customer = b.getUser();
        String when = describe(b);
        notificationService.notify(customer.getId(), NotificationType.BOOKING_CANCELLED,
                "Chủ sân đã huỷ đơn " + b.getCode(), when + ". Lý do: " + why, "/bookings/" + b.getId());
        String email = customer.getEmail();
        String body = "Xin chào " + customer.getFullName() + ",\n\n"
                + "Đơn đặt sân " + b.getCode() + " (" + when + ") đã bị chủ sân huỷ.\nLý do: " + why + "\n"
                + (wasPaid ? "Chủ sân sẽ liên hệ để hoàn tiền " + money(b.getTotalPrice()) + ".\n" : "")
                + "\nSânNay";
        AfterCommit.run(() -> mailService.send(email, "[SânNay] Đơn " + b.getCode() + " đã bị huỷ", body));
        return toDto(b);
    }

    // ===================== PAYMENT CALLBACK =====================

    /** Số tiền phải trả cho lần thanh toán này: cả nhóm định kỳ nếu có, ngược lại chỉ đơn này. */
    public long payableAmount(Booking b) {
        LocalDateTime now = LocalDateTime.now();
        return sameOrder(b).stream()
                .filter(x -> x.getStatus() == BookingStatus.PENDING_PAYMENT && x.getHoldExpiresAt() != null
                        && x.getHoldExpiresAt().isAfter(now))
                .mapToLong(Booking::getTotalPrice).sum();
    }

    /**
     * Gọi khi cổng thanh toán báo thành công. Xác nhận đơn (và cả nhóm nếu là đặt định kỳ),
     * sau đó gửi thông báo realtime + email cho khách và báo đơn mới cho chủ sân.
     */
    @Transactional
    public void markPaid(Long bookingId) {
        Booking first = getBooking(bookingId);
        List<Booking> confirmed = new ArrayList<>();
        for (Booking b : sameOrder(first)) {
            // Buổi khách đã tự huỷ trong nhóm không nằm trong số tiền thanh toán → bỏ qua
            if (b != first && b.getStatus() == BookingStatus.CANCELLED) continue;
            if (markPaidSingle(b)) confirmed.add(b);
        }
        if (confirmed.isEmpty()) return;

        User customer = first.getUser();
        Venue venue = first.getCourt().getVenue();
        long total = confirmed.stream().mapToLong(Booking::getTotalPrice).sum();
        String summary = confirmed.size() == 1
                ? describe(confirmed.get(0))
                : confirmed.size() + " buổi " + first.getCourt().getName() + ", " + venue.getName()
                  + ", từ " + confirmed.get(0).getBookingDate().format(VN_DATE);

        notificationService.notify(customer.getId(), NotificationType.BOOKING_CONFIRMED,
                "Đặt sân thành công " + first.getCode(), summary, "/bookings/" + first.getId());
        if (venue.getOwner() != null) {
            notificationService.notify(venue.getOwner().getId(), NotificationType.NEW_BOOKING,
                    "Đơn mới " + first.getCode() + " – " + money(total),
                    customer.getFullName() + ": " + summary, "/admin/bookings");
        }

        StringBuilder body = new StringBuilder("Xin chào ").append(customer.getFullName()).append(",\n\n")
                .append("Thanh toán thành công. Thông tin đặt sân:\n\n");
        for (Booking b : confirmed) {
            body.append("- Mã ").append(b.getCode()).append(": ").append(describe(b)).append("\n");
        }
        body.append("\nTổng tiền: ").append(money(total)).append("\nĐịa chỉ: ").append(venue.getAddress())
                .append("\n\nVui lòng đọc mã đặt sân cho nhân viên khi đến check-in.\nSânNay");
        String email = customer.getEmail();
        String subject = "[SânNay] Xác nhận đặt sân " + first.getCode();
        String text = body.toString();
        AfterCommit.run(() -> mailService.send(email, subject, text));
    }

    /**
     * Xác nhận một đơn. Nếu đơn đã hết hạn giữ chỗ nhưng sân vẫn trống thì vẫn xác nhận;
     * nếu sân đã bị người khác đặt thì huỷ và đánh dấu cần hoàn tiền.
     * @return true nếu đơn vừa chuyển sang CONFIRMED
     */
    private boolean markPaidSingle(Booking b) {
        if (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED) return false;

        if (b.getStatus() == BookingStatus.EXPIRED
                || (b.getStatus() == BookingStatus.PENDING_PAYMENT && b.getHoldExpiresAt().isBefore(LocalDateTime.now()))) {
            courtRepository.findByIdForUpdate(b.getCourt().getId());
            long overlaps = bookingRepository.countOverlapping(b.getCourt().getId(), b.getBookingDate(),
                    b.getStartTime(), b.getEndTime(), LocalDateTime.now(), b.getId());
            if (overlaps > 0) {
                b.setStatus(BookingStatus.CANCELLED);
                b.setCancelReason("Thanh toán sau khi hết hạn giữ chỗ và sân đã có người đặt – cần hoàn tiền");
                return false;
            }
        } else if (b.getStatus() == BookingStatus.CANCELLED) {
            if (b.getCancelReason() == null || !b.getCancelReason().contains("cần hoàn tiền")) {
                b.setCancelReason((b.getCancelReason() == null ? "" : b.getCancelReason() + ". ")
                        + "Đã thanh toán sau khi huỷ – cần hoàn tiền");
            }
            return false;
        }
        b.setStatus(BookingStatus.CONFIRMED);
        b.setHoldExpiresAt(null);
        return true;
    }

    // ===================== SCHEDULED JOBS =====================

    /** Mỗi phút: chuyển các đơn quá hạn giữ chỗ sang EXPIRED để giải phóng sân. */
    @Scheduled(fixedDelayString = "${app.booking.expire-job-ms:60000}")
    @Transactional
    public void expireUnpaidBookings() {
        List<Booking> expired = bookingRepository.findByStatusAndHoldExpiresAtBefore(BookingStatus.PENDING_PAYMENT, LocalDateTime.now());
        expired.forEach(b -> b.setStatus(BookingStatus.EXPIRED));
        // Mỗi đơn (hoặc mỗi nhóm định kỳ) chỉ báo một lần
        expired.stream()
                .collect(Collectors.toMap(b -> b.getGroupCode() != null ? b.getGroupCode() : b.getCode(), b -> b, (a, b) -> a))
                .values()
                .forEach(b -> notificationService.notify(b.getUser().getId(), NotificationType.BOOKING_EXPIRED,
                        "Hết hạn giữ chỗ " + b.getCode(),
                        "Bạn chưa thanh toán trong " + holdMinutes + " phút nên khung giờ " + describe(b) + " đã được mở lại.",
                        "/venues/" + b.getCourt().getVenue().getId()));
        if (!expired.isEmpty()) log.info("Expired {} unpaid bookings", expired.size());
    }

    /** 00:05 hằng ngày: các đơn đã xác nhận của ngày hôm trước coi như đã hoàn thành. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void completePastBookings() {
        List<Booking> past = bookingRepository.findByStatusAndBookingDateBefore(BookingStatus.CONFIRMED, LocalDate.now());
        past.forEach(b -> b.setStatus(BookingStatus.COMPLETED));
    }

    // ===================== HELPERS =====================

    public Booking getBooking(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn đặt sân"));
    }

    public void checkCanView(UserPrincipal me, Booking b) {
        if (b.getUser().getId().equals(me.getId())) return;
        venueService.checkManage(me, b.getCourt().getVenue());
    }

    public BookingDto toDto(Booking b) {
        List<Booking> order = sameOrder(b);
        return BookingDto.of(b, matchPostRepository.existsByBookingId(b.getId()), order.size(), payableAmount(b));
    }

    /** Các đơn cùng một lần đặt: cả nhóm định kỳ, hoặc chỉ chính nó. */
    private List<Booking> sameOrder(Booking b) {
        return b.getGroupCode() == null ? List.of(b) : bookingRepository.findByGroupCodeOrderByBookingDateAsc(b.getGroupCode());
    }

    private Court lockBookableCourt(Long courtId, LocalTime start, LocalTime end) {
        Court court = courtRepository.findByIdForUpdate(courtId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sân"));
        Venue venue = court.getVenue();
        if (!court.isActive() || !venue.isActive()) {
            throw ApiException.badRequest("Sân đang tạm ngưng hoạt động");
        }
        if (start.isBefore(venue.getOpenTime()) || end.isAfter(venue.getCloseTime())) {
            throw ApiException.badRequest("Ngoài giờ hoạt động của sân (" + venue.getOpenTime() + " - " + venue.getCloseTime() + ")");
        }
        return court;
    }

    /** Chống "giữ chỗ ảo": giới hạn số lần đặt chưa thanh toán (một nhóm định kỳ tính là 1). */
    private void checkPendingLimit(Long userId, LocalDateTime now) {
        long pendingOrders = bookingRepository.findByUserWithCourt(userId).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING_PAYMENT && b.getHoldExpiresAt().isAfter(now))
                .map(b -> b.getGroupCode() != null ? b.getGroupCode() : b.getCode())
                .distinct().count();
        if (pendingOrders >= maxPendingPerUser) {
            throw ApiException.badRequest("Bạn đang có " + pendingOrders
                    + " đơn chưa thanh toán. Vui lòng thanh toán hoặc huỷ trước khi đặt thêm");
        }
    }

    private Booking newBooking(Long userId, Court court, LocalDate date, LocalTime start, LocalTime end,
                               long price, LocalDateTime now, String groupCode, String note) {
        return Booking.builder()
                .code(generateCode(date))
                .user(userRepository.getReferenceById(userId))
                .court(court)
                .bookingDate(date)
                .startTime(start)
                .endTime(end)
                .totalPrice(price)
                .status(BookingStatus.PENDING_PAYMENT)
                .holdExpiresAt(now.plusMinutes(holdMinutes))
                .groupCode(groupCode)
                .note(note)
                .build();
    }

    private void cancel(Booking b, String reason) {
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancelReason(reason);
        b.setHoldExpiresAt(null);
        matchPostRepository.findByBookingId(b.getId())
                .ifPresent(m -> m.setStatus(Enums.MatchStatus.CLOSED));
    }

    void validateTimeRange(LocalDate date, LocalTime start, LocalTime end, LocalDateTime now) {
        if (start.getMinute() != 0 || end.getMinute() != 0 || start.getSecond() != 0 || end.getSecond() != 0) {
            throw ApiException.badRequest("Giờ đặt phải tròn giờ (ví dụ 18:00 - 20:00)");
        }
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("Giờ kết thúc phải sau giờ bắt đầu");
        }
        long hours = Duration.between(start, end).toHours();
        if (hours > maxHours) {
            throw ApiException.badRequest("Mỗi lần chỉ được đặt tối đa " + maxHours + " giờ");
        }
        if (LocalDateTime.of(date, start).isBefore(now)) {
            throw ApiException.badRequest("Không thể đặt khung giờ đã qua");
        }
        if (date.isAfter(now.toLocalDate().plusDays(maxDaysAhead))) {
            throw ApiException.badRequest("Chỉ được đặt trước tối đa " + maxDaysAhead + " ngày");
        }
    }

    private static String describe(Booking b) {
        return b.getCourt().getName() + ", " + b.getCourt().getVenue().getName() + ", "
                + b.getBookingDate().format(VN_DATE) + " " + b.getStartTime() + "–" + b.getEndTime();
    }

    private static String money(long v) {
        return String.format("%,d", v).replace(',', '.') + "đ";
    }

    private String generateCode(LocalDate date) {
        for (int i = 0; i < 10; i++) {
            String code = "SB" + date.format(CODE_DATE) + String.format("%04d", RANDOM.nextInt(10_000));
            if (bookingRepository.findByCode(code).isEmpty()) return code;
        }
        return "SB" + System.currentTimeMillis();
    }
}
