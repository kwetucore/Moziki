package cd.moziki.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
public class WebConfig {

    public static final Locale SWAHILI = Locale.forLanguageTag("sw");
    public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, SWAHILI);

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.FRENCH);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    /** Accept-Language: en | fr | sw, defaulting to French (DRC business language). */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        resolver.setDefaultLocale(Locale.FRENCH);
        return resolver;
    }

    /** Lets Jakarta Validation messages ({validation.*}) resolve from our i18n bundles. */
    @Bean
    public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
}
