package com.mssus.app.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileUploadServiceImpl Tests")
class FileUploadServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private FileUploadServiceImpl fileUploadService;

    private static final String CLOUD_NAME = "test-cloud";
    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-api-secret";
    private static final String FOLDER = "test-folder";
    private static final String EXPECTED_URL = "https://res.cloudinary.com/test-cloud/image/upload/v1234567890/test-folder/abc123.png";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileUploadService, "cloudName", CLOUD_NAME);
        ReflectionTestUtils.setField(fileUploadService, "apiKey", API_KEY);
        ReflectionTestUtils.setField(fileUploadService, "apiSecret", API_SECRET);
        ReflectionTestUtils.setField(fileUploadService, "folder", FOLDER);
        ReflectionTestUtils.setField(fileUploadService, "cloudinary", cloudinary);
    }

    // Helper methods
    private MultipartFile createTestMultipartFile(String filename, String contentType, byte[] content) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) content.length);
        try {
            when(file.getBytes()).thenReturn(content);
        } catch (IOException e) {
            // This should not happen in tests
        }
        return file;
    }

    private MultipartFile createTestMultipartFileWithException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenThrow(new IOException("Failed to read file"));
        return file;
    }

    private Map<String, Object> createMockUploadResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("secure_url", EXPECTED_URL);
        result.put("public_id", "test-folder/abc123");
        result.put("format", "png");
        result.put("width", 1920);
        result.put("height", 1080);
        result.put("bytes", 1024000);
        return result;
    }

    // Tests for uploadFile method
    @Test
    @DisplayName("Should upload file successfully when all parameters are valid")
    void should_uploadFileSuccessfully_when_allParametersValid() throws Exception {
        // Arrange
        byte[] fileContent = "test image content".getBytes();
        MultipartFile file = createTestMultipartFile("test.jpg", "image/jpeg", fileContent);
        Map<String, Object> uploadResult = createMockUploadResult();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(uploader).upload(bytesCaptor.capture(), optionsCaptor.capture());
        verify(cloudinary).uploader();

        assertThat(bytesCaptor.getValue()).isEqualTo(fileContent);
        Map<String, Object> capturedOptions = optionsCaptor.getValue();
        assertThat(capturedOptions.get("folder")).isEqualTo(FOLDER);
        assertThat(capturedOptions.get("resource_type")).isEqualTo("image");
        assertThat(capturedOptions.get("use_filename")).isEqualTo(false);
        assertThat(capturedOptions.get("unique_filename")).isEqualTo(true);
        assertThat(capturedOptions.get("format")).isEqualTo("png");
        assertThat(capturedOptions.get("quality")).isEqualTo("auto");
        assertThat(capturedOptions.get("fetch_format")).isEqualTo("auto");
    }

    @Test
    @DisplayName("Should handle empty file upload")
    void should_handleEmptyFile_when_uploadingFile() throws Exception {
        // Arrange
        byte[] emptyContent = new byte[0];
        MultipartFile file = createTestMultipartFile("empty.jpg", "image/jpeg", emptyContent);
        Map<String, Object> uploadResult = createMockUploadResult();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        assertThat(bytesCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Should handle large file upload")
    void should_handleLargeFile_when_uploadingFile() throws Exception {
        // Arrange
        byte[] largeContent = new byte[10 * 1024 * 1024]; // 10MB
        MultipartFile file = createTestMultipartFile("large.jpg", "image/jpeg", largeContent);
        Map<String, Object> uploadResult = createMockUploadResult();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        assertThat(bytesCaptor.getValue()).hasSize(10 * 1024 * 1024);
    }

    @ParameterizedTest
    @MethodSource("fileTypeProvider")
    @DisplayName("Should handle different file types")
    void should_handleDifferentFileTypes_when_uploadingFile(String filename, String contentType) throws Exception {
        // Arrange
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile(filename, contentType, fileContent);
        Map<String, Object> uploadResult = createMockUploadResult();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);
        verify(uploader).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("Should handle IOException when reading file bytes")
    void should_handleIOException_when_readingFileBytes() throws Exception {
        // Arrange
        MultipartFile file = createTestMultipartFileWithException();

        // Act & Assert
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        assertThatThrownBy(() -> result.get())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("File upload failed")
                .hasRootCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("Failed to read file");
    }

    @Test
    @DisplayName("Should handle Cloudinary upload exception")
    void should_handleCloudinaryUploadException_when_uploadFails() throws Exception {
        // Arrange
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile("test.jpg", "image/jpeg", fileContent);

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenThrow(new RuntimeException("Cloudinary upload failed"));

        // Act & Assert
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        assertThatThrownBy(() -> result.get())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("File upload failed")
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Cloudinary upload failed");
    }

    @Test
    @DisplayName("Should handle null secure_url in upload result")
    void should_handleNullSecureUrl_when_uploadResultIsInvalid() throws Exception {
        // Arrange
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile("test.jpg", "image/jpeg", fileContent);
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", null);
        uploadResult.put("public_id", "test-folder/abc123");

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isNull();
    }

    // Tests for validateCloudinaryConfig method
    @Test
    @DisplayName("Should throw exception when cloudinary is null")
    void should_throwException_when_cloudinaryIsNull() throws Exception {
        // Arrange
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile("test.jpg", "image/jpeg", fileContent);
        ReflectionTestUtils.setField(fileUploadService, "cloudinary", null);

        // Act & Assert
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        assertThatThrownBy(() -> result.get())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cloudinary not initialized");
    }

    // Tests for init method
    @Test
    @DisplayName("Should initialize cloudinary with correct configuration")
    void should_initializeCloudinary_when_initMethodCalled() {
        // Arrange
        FileUploadServiceImpl newService = new FileUploadServiceImpl();
        ReflectionTestUtils.setField(newService, "cloudName", CLOUD_NAME);
        ReflectionTestUtils.setField(newService, "apiKey", API_KEY);
        ReflectionTestUtils.setField(newService, "apiSecret", API_SECRET);

        // Act
        newService.init();

        // Assert
        Cloudinary cloudinary = (Cloudinary) ReflectionTestUtils.getField(newService, "cloudinary");
        assertThat(cloudinary).isNotNull();
    }

    @Test
    @DisplayName("Should handle null configuration values in init")
    void should_handleNullConfiguration_when_initMethodCalled() {
        // Arrange
        FileUploadServiceImpl newService = new FileUploadServiceImpl();
        ReflectionTestUtils.setField(newService, "cloudName", null);
        ReflectionTestUtils.setField(newService, "apiKey", null);
        ReflectionTestUtils.setField(newService, "apiSecret", null);

        // Act
        newService.init();

        // Assert - Cloudinary constructor doesn't throw exception with null values
        Cloudinary cloudinary = (Cloudinary) ReflectionTestUtils.getField(newService, "cloudinary");
        assertThat(cloudinary).isNotNull();
    }

    // Edge cases and boundary tests
    @Test
    @DisplayName("Should handle file with special characters in filename")
    void should_handleSpecialCharacters_when_uploadingFile() throws Exception {
        // Arrange
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile("test@#$%^&*().jpg", "image/jpeg", fileContent);
        Map<String, Object> uploadResult = createMockUploadResult();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);
    }

    @Test
    @DisplayName("Should handle file with very long filename")
    void should_handleLongFilename_when_uploadingFile() throws Exception {
        // Arrange
        String longFilename = "a".repeat(255) + ".jpg";
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile(longFilename, "image/jpeg", fileContent);
        Map<String, Object> uploadResult = createMockUploadResult();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);
    }

    @Test
    @DisplayName("Should handle concurrent upload requests")
    void should_handleConcurrentUploads_when_multipleRequests() throws Exception {
        // Arrange
        byte[] fileContent1 = "test content 1".getBytes();
        byte[] fileContent2 = "test content 2".getBytes();
        MultipartFile file1 = createTestMultipartFile("test1.jpg", "image/jpeg", fileContent1);
        MultipartFile file2 = createTestMultipartFile("test2.jpg", "image/jpeg", fileContent2);
        
        Map<String, Object> uploadResult1 = createMockUploadResult();
        Map<String, Object> uploadResult2 = createMockUploadResult();
        uploadResult2.put("secure_url", "https://res.cloudinary.com/test-cloud/image/upload/v1234567890/test-folder/def456.png");

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(eq(fileContent1), anyMap())).thenReturn(uploadResult1);
        when(uploader.upload(eq(fileContent2), anyMap())).thenReturn(uploadResult2);

        // Act
        CompletableFuture<String> result1 = fileUploadService.uploadFile(file1);
        CompletableFuture<String> result2 = fileUploadService.uploadFile(file2);

        // Assert
        String url1 = result1.get();
        String url2 = result2.get();
        
        assertThat(url1).isEqualTo(EXPECTED_URL);
        assertThat(url2).isEqualTo("https://res.cloudinary.com/test-cloud/image/upload/v1234567890/test-folder/def456.png");
        
        verify(uploader, times(2)).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("Should handle upload result with missing fields")
    void should_handleIncompleteUploadResult_when_uploadSucceeds() throws Exception {
        // Arrange
        byte[] fileContent = "test content".getBytes();
        MultipartFile file = createTestMultipartFile("test.jpg", "image/jpeg", fileContent);
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", EXPECTED_URL);
        // Missing other fields like public_id, format, etc.

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // Act
        CompletableFuture<String> result = fileUploadService.uploadFile(file);

        // Assert
        String uploadedUrl = result.get();
        assertThat(uploadedUrl).isEqualTo(EXPECTED_URL);
    }

    // Parameter providers
    private static Stream<Arguments> fileTypeProvider() {
        return Stream.of(
                Arguments.of("test.jpg", "image/jpeg"),
                Arguments.of("test.png", "image/png"),
                Arguments.of("test.gif", "image/gif"),
                Arguments.of("test.webp", "image/webp"),
                Arguments.of("test.bmp", "image/bmp"),
                Arguments.of("test.tiff", "image/tiff")
        );
    }
}
