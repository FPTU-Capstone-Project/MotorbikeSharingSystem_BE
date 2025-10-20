package com.mssus.app.service.impl;

import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.entity.User;
import com.mssus.app.service.impl.FPTAIServiceImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FPTAIServiceImplTest {

    private static final String DRIVER_LICENSE_URL = "https://api.fpt.ai/vision/dlr/vnm";
    private static final String VEHICLE_REGISTRATION_URL = "https://api.fpt.ai/vision/idr/vnm";

    @Mock
    private MultipartFile failingFile;

    @InjectMocks
    private FPTAIServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "apiKey", "secret-key");
    }

    static Stream<Arguments> analyzeDocumentProvider() {
        return Stream.of(
            Arguments.of(VerificationType.DRIVER_LICENSE, DRIVER_LICENSE_URL, "DRIVER_RESPONSE"),
            Arguments.of(VerificationType.VEHICLE_REGISTRATION, VEHICLE_REGISTRATION_URL, "VEHICLE_RESPONSE")
        );
    }

    @ParameterizedTest
    @MethodSource("analyzeDocumentProvider")
    void should_returnResponseBody_when_analyzeDocumentWithSupportedType(
        VerificationType type,
        String expectedUrl,
        String expectedBody
    ) {
        MockMultipartFile file = new MockMultipartFile(
            "image",
            "license.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "sample-image".getBytes(StandardCharsets.UTF_8)
        );

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
            (mock, context) -> lenient().when(mock.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(expectedBody)))) {

            String response = service.analyzeDocument(file, type);

            assertThat(response).isEqualTo(expectedBody);
            RestTemplate restTemplate = mocked.constructed().get(0);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<MultiValueMap<String, Object>>> requestCaptor =
                ArgumentCaptor.forClass((Class) HttpEntity.class);

            verify(restTemplate).postForEntity(eq(expectedUrl), requestCaptor.capture(), eq(String.class));
            verifyNoMoreInteractions(restTemplate);

            HttpEntity<MultiValueMap<String, Object>> capturedEntity = requestCaptor.getValue();
            HttpHeaders headers = capturedEntity.getHeaders();
            assertThat(headers.getFirst("api-key")).isEqualTo("secret-key");
            assertThat(headers.getContentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = capturedEntity.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getFirst("image")).isNotNull();
        }
    }

    @Test
    void should_throwIllegalArgumentException_when_analyzeDocumentWithUnsupportedType() {
        MockMultipartFile file = new MockMultipartFile(
            "image",
            "student-card.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "student".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.analyzeDocument(file, VerificationType.STUDENT_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported type");
    }

    @Test
    void should_throwRuntimeException_when_fileBytesCannotBeRead() throws IOException {
        when(failingFile.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> service.analyzeDocument(failingFile, VerificationType.DRIVER_LICENSE))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to read file");

        verify(failingFile).getBytes();
        verifyNoMoreInteractions(failingFile);
    }

    static Stream<Arguments> invalidDocumentProvider() {
        MultipartFile emptyDocument = mock(MultipartFile.class);
        lenient().when(emptyDocument.isEmpty()).thenReturn(true);
        return Stream.of(
            Arguments.of(null, "null-document"),
            Arguments.of(emptyDocument, "empty-document")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDocumentProvider")
    void should_throwValidationException_when_documentNullOrEmpty(MultipartFile document, String description) {
        User user = createUser("Nguyen Van A", LocalDate.of(1990, 1, 1));

        assertThatThrownBy(() -> service.verifyDriverLicense(user, document))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("Driver license image is required for verification");

        if (document != null) {
            verify(document).isEmpty();
            verifyNoMoreInteractions(document);
        }
    }

    @Test
    void should_returnTrue_when_driverLicenseDataMatchesUser() {
        MultipartFile document = mock(MultipartFile.class);
        when(document.isEmpty()).thenReturn(false);

        FPTAIServiceImpl spyService = spy(service);
        String json = buildDriverLicenseJson("Nguyen Van A", "123456789", "01/01/1990", "01/01/2030");
        doReturn(json).when(spyService).analyzeDocument(document, VerificationType.DRIVER_LICENSE);

        User user = createUser("Nguyen Van A", LocalDate.of(1990, 1, 1));

        boolean result = spyService.verifyDriverLicense(user, document);

        assertThat(result).isTrue();
        verify(document).isEmpty();
        verifyNoMoreInteractions(document);
        verify(spyService).analyzeDocument(document, VerificationType.DRIVER_LICENSE);
    }

    @Test
    void should_returnFalse_when_driverLicenseExpired() {
        MultipartFile document = mock(MultipartFile.class);
        when(document.isEmpty()).thenReturn(false);

        FPTAIServiceImpl spyService = spy(service);
        String json = buildDriverLicenseJson("Nguyen Van A", "123456789", "01/01/1990", "01/01/2020");
        doReturn(json).when(spyService).analyzeDocument(document, VerificationType.DRIVER_LICENSE);

        User user = createUser("Nguyen Van A", LocalDate.of(1990, 1, 1));

        boolean result = spyService.verifyDriverLicense(user, document);

        assertThat(result).isFalse();
        verify(document).isEmpty();
        verifyNoMoreInteractions(document);
        verify(spyService).analyzeDocument(document, VerificationType.DRIVER_LICENSE);
    }

    @Test
    void should_returnFalse_when_driverLicenseNameMismatch() {
        MultipartFile document = mock(MultipartFile.class);
        when(document.isEmpty()).thenReturn(false);

        FPTAIServiceImpl spyService = spy(service);
        String json = buildDriverLicenseJson("Tran Van B", "123456789", "01/01/1990", "01/01/2030");
        doReturn(json).when(spyService).analyzeDocument(document, VerificationType.DRIVER_LICENSE);

        User user = createUser("Nguyen Van A", LocalDate.of(1990, 1, 1));

        boolean result = spyService.verifyDriverLicense(user, document);

        assertThat(result).isFalse();
        verify(document).isEmpty();
        verifyNoMoreInteractions(document);
        verify(spyService).analyzeDocument(document, VerificationType.DRIVER_LICENSE);
    }

    private static User createUser(String fullName, LocalDate dateOfBirth) {
        User user = User.builder()
            .fullName(fullName)
            .email(fullName.toLowerCase().replace(' ', '.') + "@example.com")
            .build();
        user.setDateOfBirth(dateOfBirth);
        return user;
    }

    private static String buildDriverLicenseJson(String name, String id, String dob, String doe) {
        JSONObject data = new JSONObject(Map.of(
            "name", name,
            "id", id,
            "dob", dob,
            "doe", doe,
            "type", "A1",
            "text", String.format("FULL NAME: %s%nNO: %s%nDOB: %s%nNGÀY HẾT HẠN: %s", name.toUpperCase(), id, dob, doe)
        ));
        return new JSONObject(Map.of("data", new org.json.JSONArray().put(data))).toString();
    }
}
