package com.theblastbeast.controller;

import com.theblastbeast.api.AdminPressContactsApiDelegate;
import com.theblastbeast.domain.vo.PressContactList;
import com.theblastbeast.service.PressContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminPressContactsController implements AdminPressContactsApiDelegate {

    private final PressContactService pressContactService;

    @Override
    public ResponseEntity<PressContactList> listAdminPressContacts() {
        return ResponseEntity.ok(pressContactService.list());
    }
}
