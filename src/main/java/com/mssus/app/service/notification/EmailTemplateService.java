package com.mssus.app.service.notification;

import com.mssus.app.common.exception.EmailTemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
@Slf4j
public class EmailTemplateService {
    private final TemplateEngine templateEngine;
    private final ResourceLoader resourceLoader;

    public EmailTemplateService(TemplateEngine templateEngine, ResourceLoader resourceLoader) {
        this.templateEngine = templateEngine;
        this.resourceLoader = resourceLoader;
    }
    public String renderTemplate(String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            return templateEngine.process("email/" + templateName, context);
        } catch (Exception e) {
            log.error("Failed to render email template: {}", templateName, e);
            throw new EmailTemplateException("Template rendering failed", e);
        }
    }

    public String renderTextTemplate(String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            return templateEngine.process("email/" + templateName + "-text", context);
        } catch (Exception e) {
            log.warn("Text template not found for: {}, using HTML version", templateName);
            return stripHtmlTags(renderTemplate(templateName, variables));
        }
    }

    private String stripHtmlTags(String html) {
        return html.replaceAll("<[^>]*>", "");
    }
}
