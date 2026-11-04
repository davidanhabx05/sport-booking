package vn.sportbooking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.sportbooking.dto.Requests.CreateBookingRequest;
import vn.sportbooking.dto.Responses.BookingDto;
import vn.sportbooking.entity.*;
import vn.sportbooking.entity.Enums.*;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.*;
import vn.sportbooking.service.BookingService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceTest {

    @Autowired BookingService bookingService;
    @Autowired UserRepository userRepository;
    @Autowired VenueRepository venueRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired MatchPostRepository matchPostRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired NotificationRepository notificationRepository;

    private Court court;
    private final List<User> users = new ArrayList<>();
    private final LocalDate date = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        paymentRepository.deleteAll();
        matchPostRepository.deleteAll();
        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        venueRepository.deleteAll();
        userRepository.deleteAll();
        users.clear();

        for (int i = 0; i < 10; i++) {
            users.add(userRepository.save(User.builder().email("u" + i + "@test.vn").password("x")
                    .fullName("User " + i).role(Role.CUSTOMER).build()));
        }
        Venue v = Venue.builder().name("Test Venue").address("HN").district("Cầu Giấy")
                .openTime(LocalTime.of(6, 0)).closeTime(LocalTime.of(22, 0))
                .peakStart(LocalTime.of(17, 0)).peakEnd(LocalTime.of(21, 0)).build();
        Court c = Court.builder().name("P1").sportType(SportType.PICKLEBALL)
                .pricePerHour(100_000L).peakPricePerHour(150_000L).venue(v).build();
        v.getCourts().add(c);
        venueRepository.save(v);
        court = v.getCourts().get(0);
    }

    private CreateBookingRequest req(int from, int to) {
        return new CreateBookingRequest(court.getId(), date, LocalTime.of(from, 0), LocalTime.of(to, 0), null);
    }

    @Test
    void priceUsesPeakHours() {
        // 16-17 giờ thường (100k) + 17-18 cao điểm (150k)
        BookingDto b = bookingService.create(users.get(0).getId(), req(16, 18));
        assertEquals(250_000L, b.totalPrice());
        assertEquals(BookingStatus.PENDING_PAYMENT, b.status());
    }

    @Test
    void overlappingBookingIsRejected() {
        bookingService.create(users.get(0).getId(), req(18, 20));
        ApiException ex = assertThrows(ApiException.class, () -> bookingService.create(users.get(1).getId(), req(19, 21)));
        assertEquals(409, ex.getStatus().value());
        // Khung giờ liền kề thì được phép
        assertDoesNotThrow(() -> bookingService.create(users.get(1).getId(), req(20, 21)));
    }

    @Test
    void outsideOpeningHoursIsRejected() {
        assertThrows(ApiException.class, () -> bookingService.create(users.get(0).getId(), req(21, 23)));
    }

    @Test
    void expiredHoldReleasesSlot() {
        BookingDto b = bookingService.create(users.get(0).getId(), req(8, 9));
        Booking entity = bookingRepository.findById(b.id()).orElseThrow();
        entity.setHoldExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(entity);

        bookingService.expireUnpaidBookings();
        assertEquals(BookingStatus.EXPIRED, bookingRepository.findById(b.id()).orElseThrow().getStatus());
        assertDoesNotThrow(() -> bookingService.create(users.get(1).getId(), req(8, 9)));
    }

    /** 10 người bấm đặt cùng một khung giờ cùng lúc → chỉ đúng 1 người thành công. */
    @Test
    void concurrentBookingsOnlyOneSucceeds() throws Exception {
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (User u : users) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    bookingService.create(u.getId(), req(19, 20));
                    ok.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getStatus().value() == 409) conflict.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, ok.get());
        assertEquals(n - 1, conflict.get());
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void recurringCreatesWeeklyGroupWithDiscount() {
        var dtos = bookingService.createRecurring(users.get(0).getId(),
                new vn.sportbooking.dto.Requests.RecurringBookingRequest(court.getId(), date,
                        LocalTime.of(8, 0), LocalTime.of(10, 0), 4, null));
        assertEquals(4, dtos.size());
        String group = dtos.get(0).groupCode();
        assertNotNull(group);
        assertTrue(dtos.stream().allMatch(d -> group.equals(d.groupCode())));
        assertEquals(date.plusWeeks(3), dtos.get(3).date());
        // 2 giờ x 100k = 200k, giảm 10% = 180k mỗi buổi; cả nhóm 720k trả một lần
        assertEquals(180_000L, dtos.get(0).totalPrice());
        assertEquals(720_000L, dtos.get(0).payableAmount());

        bookingService.markPaid(dtos.get(0).id());
        assertTrue(bookingRepository.findByGroupCodeOrderByBookingDateAsc(group).stream()
                .allMatch(b -> b.getStatus() == BookingStatus.CONFIRMED));
        // Khách được báo "đặt sân thành công" đúng 1 lần cho cả nhóm
        assertEquals(1, notificationRepository.countByUserIdAndReadFalse(users.get(0).getId()));
    }

    @Test
    void recurringIsAllOrNothingWhenOneWeekConflicts() {
        bookingService.create(users.get(1).getId(), new CreateBookingRequest(court.getId(), date.plusWeeks(2),
                LocalTime.of(8, 0), LocalTime.of(9, 0), null));
        ApiException ex = assertThrows(ApiException.class, () -> bookingService.createRecurring(users.get(0).getId(),
                new vn.sportbooking.dto.Requests.RecurringBookingRequest(court.getId(), date,
                        LocalTime.of(8, 0), LocalTime.of(10, 0), 4, null)));
        assertEquals(409, ex.getStatus().value());
        assertEquals(1, bookingRepository.count()); // không buổi nào của nhóm được tạo
    }

    @Test
    void markPaidConfirmsBooking() {
        BookingDto b = bookingService.create(users.get(0).getId(), req(10, 11));
        bookingService.markPaid(b.id());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(b.id()).orElseThrow().getStatus());
        assertEquals(0L, bookingService.get(new vn.sportbooking.security.UserPrincipal(users.get(0)), b.id()).payableAmount());
    }
}
