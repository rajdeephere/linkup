package com.linkup.conversation;

import com.linkup.common.BadRequestException;
import com.linkup.common.ForbiddenException;
import com.linkup.common.NotFoundException;
import com.linkup.conversation.dto.AddMembersRequest;
import com.linkup.conversation.dto.ConversationResponse;
import com.linkup.conversation.dto.CreateConversationRequest;
import com.linkup.user.User;
import com.linkup.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conversation use-cases: create (direct/group), list mine, fetch one, add members.
 *
 * Membership is the authorization boundary — every read/write asserts the caller is a
 * participant of the conversation before doing anything.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ParticipantRepository participantRepository,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ConversationResponse create(UUID creatorId, CreateConversationRequest req) {
        // The creator is always a member; dedupe the "other members" and drop the creator.
        Set<UUID> others = new LinkedHashSet<>(req.memberUserIds());
        others.remove(creatorId);
        if (others.isEmpty()) {
            throw new BadRequestException("A conversation needs at least one other member");
        }
        List<User> otherUsers = userRepository.findAllById(others);
        if (otherUsers.size() != others.size()) {
            throw new BadRequestException("One or more members do not exist");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("Creator no longer exists"));

        return req.type() == ConversationType.DIRECT
                ? createDirect(creator, otherUsers)
                : createGroup(creator, otherUsers, req.title());
    }

    private ConversationResponse createDirect(User creator, List<User> otherUsers) {
        if (otherUsers.size() != 1) {
            throw new BadRequestException("A direct conversation must have exactly one other member");
        }
        User other = otherUsers.get(0);

        // Dedup: if these two already share a direct conversation, return it instead of a duplicate.
        List<UUID> existing = participantRepository.findDirectConversationBetween(creator.getId(), other.getId());
        if (!existing.isEmpty()) {
            Conversation c = conversationRepository.findById(existing.get(0)).orElseThrow();
            return respond(c);
        }

        Conversation c = newConversation(ConversationType.DIRECT, null);
        conversationRepository.save(c);
        addParticipant(c, creator, ParticipantRole.MEMBER);
        addParticipant(c, other, ParticipantRole.MEMBER);
        return respond(c);
    }

    private ConversationResponse createGroup(User creator, List<User> otherUsers, String title) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("A group conversation needs a title");
        }
        Conversation c = newConversation(ConversationType.GROUP, title.trim());
        conversationRepository.save(c);
        addParticipant(c, creator, ParticipantRole.ADMIN); // creator owns the group
        for (User u : otherUsers) {
            addParticipant(c, u, ParticipantRole.MEMBER);
        }
        return respond(c);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listMine(UUID userId) {
        List<UUID> convIds = participantRepository.findByUserId(userId).stream()
                .map(p -> p.getConversation().getId())
                .toList();
        if (convIds.isEmpty()) {
            return List.of();
        }
        // One batched fetch of all participants (with users) → group by conversation → no N+1.
        Map<UUID, List<Participant>> byConvo = participantRepository.findByConversationIdsFetchUser(convIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getConversation().getId()));

        return conversationRepository.findAllById(convIds).stream()
                .sorted(Comparator.comparing(this::recencyKey).reversed()) // most-recent first
                .map(c -> ConversationResponse.of(c, byConvo.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getOne(UUID userId, UUID conversationId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        assertMember(conversationId, userId);
        return respond(c);
    }

    @Transactional
    public ConversationResponse addMembers(UUID actorId, UUID conversationId, AddMembersRequest req) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        assertMember(conversationId, actorId);
        if (c.getType() == ConversationType.DIRECT) {
            throw new BadRequestException("Cannot add members to a direct conversation");
        }
        Set<UUID> toAdd = new LinkedHashSet<>(req.memberUserIds());
        List<User> users = userRepository.findAllById(toAdd);
        if (users.size() != toAdd.size()) {
            throw new BadRequestException("One or more members do not exist");
        }
        for (User u : users) {
            // Idempotent: silently skip anyone already in the conversation.
            if (!participantRepository.existsByConversation_IdAndUser_Id(conversationId, u.getId())) {
                addParticipant(c, u, ParticipantRole.MEMBER);
            }
        }
        return respond(c);
    }

    // --- helpers ---

    private void assertMember(UUID conversationId, UUID userId) {
        if (!participantRepository.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant of this conversation");
        }
    }

    private Conversation newConversation(ConversationType type, String title) {
        return Conversation.builder()
                .id(UUID.randomUUID())
                .type(type)
                .title(title)
                .createdAt(Instant.now())
                .build();
    }

    private void addParticipant(Conversation c, User user, ParticipantRole role) {
        participantRepository.save(Participant.builder()
                .id(UUID.randomUUID())
                .conversation(c)
                .user(user)
                .role(role)
                .joinedAt(Instant.now())
                .lastReadSeq(0)
                .build());
    }

    private Instant recencyKey(Conversation c) {
        return c.getLastMessageAt() != null ? c.getLastMessageAt() : c.getCreatedAt();
    }

    private ConversationResponse respond(Conversation c) {
        return ConversationResponse.of(c, participantRepository.findByConversationIdFetchUser(c.getId()));
    }
}
