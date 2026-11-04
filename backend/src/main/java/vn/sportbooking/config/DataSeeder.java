package vn.sportbooking.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.entity.*;
import vn.sportbooking.entity.Enums.*;
import vn.sportbooking.repository.*;
import vn.sportbooking.service.PricingService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Tạo dữ liệu mẫu khi database còn trống: tài khoản demo, 6 cụm sân tại Hà Nội,
 * lịch sử đơn đặt 2 tuần gần nhất (để có số liệu thống kê) và vài kèo đang mở.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final MatchPostRepository matchPostRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final PricingService pricingService;

    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || userRepository.count() > 0) return;
        log.info("Seeding demo data...");

        User admin = user("admin@sportbooking.vn", "admin123", "Quản trị viên", "0900000001", Role.ADMIN);
        User owner = user("owner@sportbooking.vn", "owner123", "Nguyễn Văn Chủ", "0900000002", Role.OWNER);
        User owner2 = user("owner2@sportbooking.vn", "owner123", "Trần Thị Sân", "0900000003", Role.OWNER);
        User an = user("an@gmail.com", "123456", "Lê Minh An", "0912345678", Role.CUSTOMER);
        User binh = user("binh@gmail.com", "123456", "Phạm Thanh Bình", "0987654321", Role.CUSTOMER);
        User chi = user("chi@gmail.com", "123456", "Đỗ Khánh Chi", "0934567890", Role.CUSTOMER);
        an.setSkillLevel(SkillLevel.INTERMEDIATE);
        binh.setSkillLevel(SkillLevel.BEGINNER);
        chi.setSkillLevel(SkillLevel.ADVANCED);
        List<User> customers = List.of(an, binh, chi);

        List<Venue> venues = new ArrayList<>();
        venues.add(venue(owner, "Pickleball Cầu Giấy Arena", "68 Trần Thái Tông", "Cầu Giấy",
                "Cụm sân pickleball trong nhà, mặt sân tiêu chuẩn, có cho thuê vợt và bóng.", 6, 22,
                court("Sân P1", SportType.PICKLEBALL, 150_000L, 200_000L),
                court("Sân P2", SportType.PICKLEBALL, 150_000L, 200_000L),
                court("Sân P3", SportType.PICKLEBALL, 150_000L, 200_000L),
                court("Sân P4", SportType.PICKLEBALL, 130_000L, 180_000L)));
        venues.add(venue(owner, "Cầu lông Đống Đa Sport", "15 Thái Hà", "Đống Đa",
                "Sân cầu lông thảm PVC, ánh sáng chống chói, có phòng thay đồ và gửi xe miễn phí.", 5, 23,
                court("Sân 1", SportType.BADMINTON, 80_000L, 120_000L),
                court("Sân 2", SportType.BADMINTON, 80_000L, 120_000L),
                court("Sân 3", SportType.BADMINTON, 80_000L, 120_000L),
                court("Sân 4", SportType.BADMINTON, 80_000L, 120_000L),
                court("Sân 5", SportType.BADMINTON, 70_000L, 100_000L)));
        venues.add(venue(owner2, "Sân bóng Thanh Xuân Green", "250 Nguyễn Trãi", "Thanh Xuân",
                "Sân cỏ nhân tạo thế hệ mới, có đèn chiếu sáng ban đêm, nước uống miễn phí.", 6, 23,
                outdoor(court("Sân 5A", SportType.FOOTBALL_5, 300_000L, 450_000L)),
                outdoor(court("Sân 5B", SportType.FOOTBALL_5, 300_000L, 450_000L)),
                outdoor(court("Sân 7", SportType.FOOTBALL_7, 600_000L, 850_000L))));
        venues.add(venue(owner2, "Hà Đông Pickleball & Badminton", "12 Quang Trung", "Hà Đông",
                "Tổ hợp sân pickleball và cầu lông, phù hợp nhóm bạn và gia đình.", 6, 22,
                court("Pickleball 1", SportType.PICKLEBALL, 120_000L, 170_000L),
                court("Pickleball 2", SportType.PICKLEBALL, 120_000L, 170_000L),
                court("Cầu lông 1", SportType.BADMINTON, 70_000L, 100_000L),
                court("Cầu lông 2", SportType.BADMINTON, 70_000L, 100_000L)));
        venues.add(venue(owner, "Long Biên Tennis Club", "45 Nguyễn Văn Cừ", "Long Biên",
                "Sân tennis mặt cứng, có huấn luyện viên kèm theo yêu cầu.", 6, 22,
                outdoor(court("Tennis 1", SportType.TENNIS, 250_000L, 350_000L)),
                outdoor(court("Tennis 2", SportType.TENNIS, 250_000L, 350_000L))));
        venues.add(venue(owner2, "Hai Bà Trưng Football Center", "120 Minh Khai", "Hai Bà Trưng",
                "Sân bóng đá mini trung tâm, gần bến xe buýt, có căng-tin.", 7, 23,
                outdoor(court("Sân A", SportType.FOOTBALL_5, 350_000L, 500_000L)),
                outdoor(court("Sân B", SportType.FOOTBALL_5, 350_000L, 500_000L)),
                outdoor(court("Sân C", SportType.FOOTBALL_7, 650_000L, 900_000L))));
        venueRepository.saveAll(venues);

        // Lịch sử đơn đặt: 14 ngày trước → hôm qua (COMPLETED), mỗi sân vài khung giờ không trùng nhau
        Random rnd = new Random(42);
        LocalDate today = LocalDate.now();
        int[] hours = {7, 9, 16, 18, 20};
        for (int d = 14; d >= 1; d--) {
            LocalDate date = today.minusDays(d);
            for (Venue v : venues) {
                for (Court c : v.getCourts()) {
                    for (int h : hours) {
                        if (h + 1 > v.getCloseTime().getHour() || h < v.getOpenTime().getHour()) continue;
                        // giờ cao điểm đông hơn
                        double chance = v.isPeak(LocalTime.of(h, 0)) ? 0.7 : 0.3;
                        if (rnd.nextDouble() < chance) {
                            // các mốc 7,9,16,18,20 cách nhau ≥ 2 giờ nên đặt 1–2 giờ không bị chồng
                            int len = (c.getSportType() == SportType.BADMINTON && rnd.nextBoolean()) ? 2 : 1;
                            booking(customers.get(rnd.nextInt(3)), c, date, h, h + len, BookingStatus.COMPLETED);
                        }
                    }
                }
            }
        }

        // Vài đơn sắp tới đã thanh toán + kèo ghép đang mở
        LocalDate tomorrow = today.plusDays(1);
        Court p1 = venues.get(0).getCourts().get(0);
        Court badminton1 = venues.get(1).getCourts().get(0);
        Court football5 = venues.get(2).getCourts().get(0);

        Booking b1 = booking(an, p1, tomorrow, 18, 20, BookingStatus.CONFIRMED);
        Booking b2 = booking(binh, badminton1, tomorrow, 19, 21, BookingStatus.CONFIRMED);
        Booking b3 = booking(chi, football5, today.plusDays(2), 19, 20, BookingStatus.CONFIRMED);
        booking(an, venues.get(1).getCourts().get(1), today.plusDays(3), 7, 9, BookingStatus.CONFIRMED);

        matchPostRepository.save(MatchPost.builder().host(an).booking(b1)
                .title("Tìm 2 bạn đánh đôi pickleball tối mai")
                .description("Trình trung bình, đánh vui vẻ, chia tiền sân. Có sẵn vợt dư.")
                .skillLevel(SkillLevel.INTERMEDIATE).slotsNeeded(2).pricePerPerson(100_000L)
                .status(MatchStatus.OPEN).build());
        matchPostRepository.save(MatchPost.builder().host(binh).booking(b2)
                .title("Cầu lông giao lưu – cần 3 người")
                .description("Mới chơi cũng được, không áp lực thắng thua.")
                .skillLevel(SkillLevel.BEGINNER).slotsNeeded(3).pricePerPerson(60_000L)
                .status(MatchStatus.OPEN).build());
        matchPostRepository.save(MatchPost.builder().host(chi).booking(b3)
                .title("Đá bóng sân 5 – thiếu 4 người")
                .description("Team văn phòng, đá giao hữu, cần thêm 4 bạn đá khá.")
                .skillLevel(SkillLevel.ADVANCED).slotsNeeded(4).pricePerPerson(80_000L)
                .status(MatchStatus.OPEN).build());

        review(venues.get(0), an, 5, "Sân đẹp, chủ sân nhiệt tình, giờ tối hơi đông.");
        review(venues.get(0), binh, 4, "Mặt sân tốt, gửi xe hơi chật.");
        review(venues.get(1), chi, 5, "Thảm êm, ánh sáng tốt, giá hợp lý.");
        review(venues.get(2), an, 4, "Cỏ mới, đèn sáng. Cuối tuần nên đặt sớm.");

        log.info("Seed done: {} users, {} venues, {} bookings", userRepository.count(), venues.size(), bookingRepository.count());
    }

    private User user(String email, String pw, String name, String phone, Role role) {
        return userRepository.save(User.builder().email(email).password(passwordEncoder.encode(pw))
                .fullName(name).phone(phone).role(role).skillLevel(SkillLevel.BEGINNER).build());
    }

    private Venue venue(User owner, String name, String address, String district, String desc,
                        int open, int close, Court... courts) {
        Venue v = Venue.builder().name(name).address(address + ", " + district + ", Hà Nội").district(district)
                .description(desc).phone(owner.getPhone()).owner(owner)
                .openTime(LocalTime.of(open, 0)).closeTime(LocalTime.of(close, 0))
                .peakStart(LocalTime.of(17, 0)).peakEnd(LocalTime.of(21, 0)).build();
        for (Court c : courts) {
            c.setVenue(v);
            v.getCourts().add(c);
        }
        return v;
    }

    private Court court(String name, SportType type, Long price, Long peak) {
        return Court.builder().name(name).sportType(type).pricePerHour(price).peakPricePerHour(peak).build();
    }

    private Court outdoor(Court c) {
        c.setIndoor(false);
        return c;
    }

    private Booking booking(User u, Court c, LocalDate date, int from, int to, BookingStatus status) {
        LocalTime s = LocalTime.of(from, 0), e = LocalTime.of(to, 0);
        String code = "SB" + date.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%04d", bookingRepository.count() + 1);
        return bookingRepository.save(Booking.builder().code(code).user(u).court(c).bookingDate(date)
                .startTime(s).endTime(e).totalPrice(pricingService.totalPrice(c, s, e)).status(status)
                .createdAt(date.minusDays(1).atTime(10, 0)).build());
    }

    private void review(Venue v, User u, int rating, String comment) {
        reviewRepository.save(Review.builder().venue(v).user(u).rating(rating).comment(comment).build());
    }
}
