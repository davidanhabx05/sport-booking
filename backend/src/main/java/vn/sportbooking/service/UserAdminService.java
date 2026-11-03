package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Requests.UpdateUserRequest;
import vn.sportbooking.dto.Responses.UserDto;
import vn.sportbooking.entity.User;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.UserRepository;

import java.util.List;

/** Quản trị người dùng – chỉ ADMIN. */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAll().stream().map(UserDto::of).toList();
    }

    @Transactional
    public UserDto update(Long adminId, Long userId, UpdateUserRequest req) {
        if (adminId.equals(userId)) throw ApiException.badRequest("Không thể tự thay đổi quyền/khoá chính mình");
        User u = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        if (req.role() != null) u.setRole(req.role());
        if (req.enabled() != null) u.setEnabled(req.enabled());
        return UserDto.of(u);
    }
}
