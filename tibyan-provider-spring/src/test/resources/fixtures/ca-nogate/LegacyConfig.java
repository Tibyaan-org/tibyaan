package ca.nogate;

import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

// The LEGACY enablement route: it processes @Secured through a role-prefix voter whose matching depends
// on configuration the tool will not interpret, so the modern-enablement gate does not pass.
@EnableGlobalMethodSecurity(securedEnabled = true)
class LegacyConfig {
}
