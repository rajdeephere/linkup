package com.linkup.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    /** My memberships (conversation lazy — we only need its id to build the convo list). */
    @Query("select p from Participant p where p.user.id = :userId")
    List<Participant> findByUserId(@Param("userId") UUID userId);

    /** Membership check — the basis of every authorization decision on a conversation. */
    boolean existsByConversation_IdAndUser_Id(UUID conversationId, UUID userId);

    /** My participant row in a conversation — used to read/advance lastReadSeq. */
    Optional<Participant> findByConversation_IdAndUser_Id(UUID conversationId, UUID userId);

    /** Usernames of everyone in a conversation except the given user (typing / read fan-out). */
    @Query("select p.user.username from Participant p where p.conversation.id = :convId and p.user.id <> :userId")
    List<String> findOtherParticipantUsernames(@Param("convId") UUID convId, @Param("userId") UUID userId);

    /** Usernames of ALL participants (message fan-out includes the sender's own echo). */
    @Query("select p.user.username from Participant p where p.conversation.id = :convId")
    List<String> findParticipantUsernames(@Param("convId") UUID convId);

    /** Distinct usernames of people who share ANY conversation with the user (presence fan-out). */
    @Query("""
            select distinct p2.user.username from Participant p1, Participant p2
            where p1.conversation.id = p2.conversation.id
              and p1.user.id = :userId and p2.user.id <> :userId
            """)
    List<String> findPeerUsernames(@Param("userId") UUID userId);

    /** Participants of a conversation, with the User fetch-joined (avoids N+1 on the list). */
    @Query("select p from Participant p join fetch p.user where p.conversation.id = :convId")
    List<Participant> findByConversationIdFetchUser(@Param("convId") UUID convId);

    @Query("select p from Participant p join fetch p.user where p.conversation.id in :convIds")
    List<Participant> findByConversationIdsFetchUser(@Param("convIds") Collection<UUID> convIds);

    /**
     * Finds an existing DIRECT conversation containing BOTH users — used to dedup so two
     * people never end up with multiple 1:1 threads. A direct convo has exactly 2
     * participants; if both are in (a, b) the count is 2, which uniquely identifies it.
     */
    @Query("""
            select p.conversation.id from Participant p
            where p.conversation.type = com.linkup.conversation.ConversationType.DIRECT
              and p.user.id in (:a, :b)
            group by p.conversation.id
            having count(p) = 2
            """)
    List<UUID> findDirectConversationBetween(@Param("a") UUID a, @Param("b") UUID b);
}
