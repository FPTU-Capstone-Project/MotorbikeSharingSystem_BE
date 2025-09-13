//package com.mssus.app.util;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.ValueSource;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class ValidationUtilTest {
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "test@example.com",
//        "user123@domain.org",
//        "first.last@company.co.uk",
//        "user+tag@example.edu"
//    })
//    void isValidEmail_ValidEmails_ReturnsTrue(String email) {
//        assertTrue(ValidationUtil.isValidEmail(email));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "invalid-email",
//        "@example.com",
//        "user@",
//        "user@.com",
//        "user.example.com",
//        ""
//    })
//    void isValidEmail_InvalidEmails_ReturnsFalse(String email) {
//        assertFalse(ValidationUtil.isValidEmail(email));
//    }
//
//    @Test
//    void isValidEmail_NullEmail_ReturnsFalse() {
//        assertFalse(ValidationUtil.isValidEmail(null));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "0901234567",
//        "0912345678",
//        "+84901234567",
//        "+84912345678",
//        "01234567890"
//    })
//    void isValidPhone_ValidPhones_ReturnsTrue(String phone) {
//        assertTrue(ValidationUtil.isValidPhone(phone));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "123",
//        "12345678901234",
//        "+1234567890",
//        "abc1234567",
//        "090123456",
//        ""
//    })
//    void isValidPhone_InvalidPhones_ReturnsFalse(String phone) {
//        assertFalse(ValidationUtil.isValidPhone(phone));
//    }
//
//    @Test
//    void isValidPhone_NullPhone_ReturnsFalse() {
//        assertFalse(ValidationUtil.isValidPhone(null));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "Password1",
//        "ValidPass123",
//        "MySecure1",
//        "Test1234"
//    })
//    void isValidPassword_ValidPasswords_ReturnsTrue(String password) {
//        assertTrue(ValidationUtil.isValidPassword(password));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "password",     // no uppercase, no digit
//        "PASSWORD1",    // no lowercase
//        "Password",     // no digit
//        "Pass1",        // too short
//        "1234567",      // no letters
//        ""
//    })
//    void isValidPassword_InvalidPasswords_ReturnsFalse(String password) {
//        assertFalse(ValidationUtil.isValidPassword(password));
//    }
//
//    @Test
//    void isValidPassword_NullPassword_ReturnsFalse() {
//        assertFalse(ValidationUtil.isValidPassword(null));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "59A-12345",
//        "30B1-23456",
//        "77AA-12345",
//        "51F-123456",
//        "29A 12345"
//    })
//    void isValidPlateNumber_ValidPlateNumbers_ReturnsTrue(String plateNumber) {
//        assertTrue(ValidationUtil.isValidPlateNumber(plateNumber));
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = {
//        "ABC-123",
//        "1234567",
//        "59-12345",
//        "59AAA-123",
//        ""
//    })
//    void isValidPlateNumber_InvalidPlateNumbers_ReturnsFalse(String plateNumber) {
//        assertFalse(ValidationUtil.isValidPlateNumber(plateNumber));
//    }
//
//    @Test
//    void normalizePhone_PhoneWithPlus84_ConvertsTo0() {
//        String result = ValidationUtil.normalizePhone("+84901234567");
//        assertEquals("0901234567", result);
//    }
//
//    @Test
//    void normalizePhone_PhoneWithSpaces_RemovesSpaces() {
//        String result = ValidationUtil.normalizePhone("090 123 4567");
//        assertEquals("0901234567", result);
//    }
//
//    @Test
//    void normalizePhone_PhoneWithDashes_RemovesDashes() {
//        String result = ValidationUtil.normalizePhone("090-123-4567");
//        assertEquals("0901234567", result);
//    }
//
//    @Test
//    void normalizePhone_NormalPhone_RemainsUnchanged() {
//        String result = ValidationUtil.normalizePhone("0901234567");
//        assertEquals("0901234567", result);
//    }
//
//    @Test
//    void normalizePhone_NullPhone_ReturnsNull() {
//        String result = ValidationUtil.normalizePhone(null);
//        assertNull(result);
//    }
//
//    @Test
//    void isEmailOrPhone_ValidEmail_ReturnsTrue() {
//        assertTrue(ValidationUtil.isEmailOrPhone("test@example.com"));
//    }
//
//    @Test
//    void isEmailOrPhone_ValidPhone_ReturnsTrue() {
//        assertTrue(ValidationUtil.isEmailOrPhone("0901234567"));
//    }
//
//    @Test
//    void isEmailOrPhone_InvalidInput_ReturnsFalse() {
//        assertFalse(ValidationUtil.isEmailOrPhone("invalid-input"));
//    }
//}
