package com.mssus.app.entity;
import com.mssus.app.common.enums.FileType;
import com.mssus.app.common.enums.UploadStatus;
import com.mssus.app.common.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import java.time.LocalDateTime;
@Entity
@Table(name = "file_uploads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Integer fileId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FileType fileType;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String storedFilename;

    @Column(nullable = false)
    private String filePath;

    private Integer fileSize;
    private String mimeType;
    @Enumerated(EnumType.STRING)
    private UploadStatus uploadStatus;
    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @CreatedDate
    private LocalDateTime createdAt;
}
