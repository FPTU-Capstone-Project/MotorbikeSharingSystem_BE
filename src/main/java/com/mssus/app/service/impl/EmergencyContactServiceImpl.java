package com.mssus.app.service.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.domain.sos.EmergencyContactRequest;
import com.mssus.app.dto.domain.sos.EmergencyContactResponse;
import com.mssus.app.entity.EmergencyContact;
import com.mssus.app.entity.User;
import com.mssus.app.repository.EmergencyContactRepository;
import com.mssus.app.service.EmergencyContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyContactServiceImpl implements EmergencyContactService {

    private final EmergencyContactRepository contactRepository;

    @Override
    @Transactional
    public EmergencyContactResponse createContact(User user, EmergencyContactRequest request) {
        validateRequest(request);
        int userId = user.getUserId();

        boolean hasExisting = contactRepository.existsByUser_UserId(userId);
        boolean shouldBePrimary = Boolean.TRUE.equals(request.getPrimary()) || !hasExisting;

        if (shouldBePrimary) {
            contactRepository.unsetPrimaryForUser(userId, null);
        }

        EmergencyContact contact = EmergencyContact.builder()
            .user(user)
            .name(request.getName().trim())
            .phone(request.getPhone().trim())
            .relationship(StringUtils.hasText(request.getRelationship()) ? request.getRelationship().trim() : null)
            .isPrimary(shouldBePrimary)
            .build();

        EmergencyContact saved = contactRepository.save(contact);
        log.info("Emergency contact {} created for user {} (primary={})", saved.getContactId(), userId, shouldBePrimary);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public EmergencyContactResponse updateContact(User user, Integer contactId, EmergencyContactRequest request) {
        validateRequest(request);

        EmergencyContact contact = requireOwnedContact(user, contactId);

        contact.setName(request.getName().trim());
        contact.setPhone(request.getPhone().trim());
        contact.setRelationship(StringUtils.hasText(request.getRelationship()) ? request.getRelationship().trim() : null);

        Boolean requestedPrimary = request.getPrimary();
        if (requestedPrimary != null) {
            if (requestedPrimary) {
                contactRepository.unsetPrimaryForUser(user.getUserId(), contact.getContactId());
                contact.setIsPrimary(true);
            } else if (Boolean.TRUE.equals(contact.getIsPrimary())) {
                promoteNextPrimary(user, contactId);
                contact.setIsPrimary(false);
            }
        }

        EmergencyContact saved = contactRepository.save(contact);
        log.info("Emergency contact {} updated for user {}", contactId, user.getUserId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteContact(User user, Integer contactId) {
        EmergencyContact contact = requireOwnedContact(user, contactId);
        boolean wasPrimary = Boolean.TRUE.equals(contact.getIsPrimary());
        contactRepository.delete(contact);
        log.info("Emergency contact {} deleted for user {}", contactId, user.getUserId());

        if (wasPrimary) {
            promoteNextPrimary(user, null);
        }
    }

    @Override
    @Transactional
    public EmergencyContactResponse setPrimaryContact(User user, Integer contactId) {
        EmergencyContact contact = requireOwnedContact(user, contactId);
        if (Boolean.TRUE.equals(contact.getIsPrimary())) {
            return toResponse(contact);
        }

        contactRepository.unsetPrimaryForUser(user.getUserId(), contactId);
        contact.setIsPrimary(true);
        EmergencyContact saved = contactRepository.save(contact);
        log.info("Emergency contact {} set as primary for user {}", contactId, user.getUserId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public List<EmergencyContactResponse> getContacts(User user) {
        return contactRepository.findByUser_UserIdOrderByIsPrimaryDescCreatedAtAsc(user.getUserId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private EmergencyContact requireOwnedContact(User user, Integer contactId) {
        return contactRepository.findById(contactId)
            .filter(contact -> contact.getUser().getUserId().equals(user.getUserId()))
            .orElseThrow(() -> BaseDomainException.of("sos.contact.not-found"));
    }

    private void promoteNextPrimary(User user, Integer excludedContactId) {
        List<EmergencyContact> contacts = contactRepository.findByUser_UserIdOrderByIsPrimaryDescCreatedAtAsc(user.getUserId());
        contacts.stream()
            .filter(contact -> excludedContactId == null || !contact.getContactId().equals(excludedContactId))
            .findFirst()
            .ifPresent(next -> {
                contactRepository.unsetPrimaryForUser(user.getUserId(), next.getContactId());
                next.setIsPrimary(true);
                contactRepository.save(next);
                log.info("Emergency contact {} promoted as primary for user {}", next.getContactId(), user.getUserId());
            });
    }

    private void validateRequest(EmergencyContactRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw BaseDomainException.of("sos.contact.invalid-name");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw BaseDomainException.of("sos.contact.invalid-phone");
        }
    }

    private EmergencyContactResponse toResponse(EmergencyContact contact) {
        return EmergencyContactResponse.builder()
            .contactId(contact.getContactId())
            .name(contact.getName())
            .phone(contact.getPhone())
            .relationship(contact.getRelationship())
            .primary(Boolean.TRUE.equals(contact.getIsPrimary()))
            .createdAt(contact.getCreatedAt())
            .updatedAt(contact.getUpdatedAt())
            .build();
    }
}
