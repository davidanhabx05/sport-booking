package vn.sportbooking.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportbooking.dto.Requests.CreateMatchRequest;
import vn.sportbooking.dto.Requests.JoinMatchRequest;
import vn.sportbooking.dto.Responses.MatchDto;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.security.CurrentUser;
import vn.sportbooking.security.UserPrincipal;
import vn.sportbooking.service.MatchService;

import java.util.List;

@Tag(name = "Matchmaking", description = "Ghép kèo – tìm người chơi cùng")
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping
    public List<MatchDto> list(@RequestParam(required = false) Enums.SportType sport,
                               @RequestParam(required = false) Enums.SkillLevel level,
                               @RequestParam(required = false) String district) {
        Long viewer = CurrentUser.get().map(UserPrincipal::getId).orElse(null);
        return matchService.listUpcoming(viewer, sport, level, district);
    }

    @GetMapping("/my")
    public List<MatchDto> mine() {
        return matchService.mine(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public MatchDto get(@PathVariable Long id) {
        return matchService.get(id, CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDto create(@Valid @RequestBody CreateMatchRequest req) {
        return matchService.create(CurrentUser.id(), req);
    }

    @PostMapping("/{id}/join")
    public MatchDto join(@PathVariable Long id, @Valid @RequestBody(required = false) JoinMatchRequest req) {
        return matchService.join(CurrentUser.id(), id, req == null ? null : req.message());
    }

    @PostMapping("/{id}/leave")
    public MatchDto leave(@PathVariable Long id) {
        return matchService.leave(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/participants/{participantId}/accept")
    public MatchDto accept(@PathVariable Long id, @PathVariable Long participantId) {
        return matchService.respond(CurrentUser.id(), id, participantId, true);
    }

    @PostMapping("/{id}/participants/{participantId}/reject")
    public MatchDto reject(@PathVariable Long id, @PathVariable Long participantId) {
        return matchService.respond(CurrentUser.id(), id, participantId, false);
    }

    @PostMapping("/{id}/close")
    public MatchDto close(@PathVariable Long id) {
        return matchService.close(CurrentUser.id(), id);
    }
}
