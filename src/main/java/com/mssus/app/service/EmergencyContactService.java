package com.mssus.app.service;

import com.mssus.app.dto.sos.EmergencyContactRequest;
import com.mssus.app.dto.sos.EmergencyContactResponse;
import com.mssus.app.entity.User;

import java.util.List;

public interface EmergencyContactService {

    List<EmergencyContactResponse> getContacts(User user);

    EmergencyContactResponse createContact(User user, EmergencyContactRequest request);

    EmergencyContactResponse updateContact(User user, Integer contactId, EmergencyContactRequest request);

    void deleteContact(User user, Integer contactId);

    EmergencyContactResponse setPrimaryContact(User user, Integer contactId);
}
